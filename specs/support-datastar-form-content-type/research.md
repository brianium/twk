# Support Datastar Form Content Type - Research

## Problem Statement

When using Datastar's `{contentType: 'form'}` option with `@post()`, TWK's `with-signals` middleware fails with a JSON parse error:

```
java.io.EOFException: Unexpected end of input
    at charred.api$json_reader_fn$fn__20802.invoke(api.clj:512)
    ...
    at ascolais.twk.middleware$with_signals$fn__21361$fn__21362.invoke(middleware.clj:20)
```

This prevents using native HTML form patterns with Datastar, forcing all form state into signals even when standard form params would be simpler.

## Root Cause Analysis

### How the Datastar SDK handles signals

From `starfederation.datastar.clojure.api.signals/get-signals`:

```clojure
(defn get-signals [request]
  (if (= :get (:request-method request))
    (get-in request [:query-params consts/datastar-key])
    (:body request)))
```

- **GET requests**: Signals come from query params (`?datastar={...}`)
- **Non-GET requests**: Signals are read from the request body (assumed to be JSON)

### How TWK uses this

In `with-signals` middleware (middleware.clj:10-22):

```clojure
(defn with-signals [read-json]
  (fn [handler]
    (fn [request]
      (if (d*/datastar-request? request)
        (let [raw-signals (d*/get-signals request)]
          (if (some? raw-signals)
            (handler (assoc request :signals (read-json raw-signals)))
            (handler request)))
        (handler request)))))
```

TWK assumes `raw-signals` is JSON and parses it with `read-json` (charred).

### The conflict with `contentType: 'form'`

Per [Datastar documentation](https://data-star.dev/reference/actions#backend-actions):

> "A value of `form` tells the action to look for the closest form... and send them to the backend using a form request (**no signals are sent**)."

When `contentType: 'form'` is used:
1. Request body contains `application/x-www-form-urlencoded` data (not JSON)
2. **No signals are transmitted** - only form field data
3. TWK's `get-signals` returns the body (an InputStream of form data)
4. `read-json` attempts to parse form data as JSON → fails

## Requirements

### Functional Requirements

1. TWK must not throw errors when handling Datastar form-encoded requests
2. Handlers should receive `nil` signals for form requests (matching Datastar's behavior)
3. Standard Ring form params (`:form-params`) should be available when using form content type
4. Existing JSON signal parsing must continue to work unchanged

### Non-Functional Requirements

- **Backwards compatibility**: No breaking changes to existing TWK users
- **Performance**: Minimal overhead for content-type detection
- **Simplicity**: Solution should be straightforward, not over-engineered

## Options Considered

### Option A: Check Content-Type header before parsing

**Description:** Modify `with-signals` to inspect the `Content-Type` header. Skip JSON parsing for form-encoded content types.

```clojure
(defn- form-content-type? [request]
  (when-let [ct (get-in request [:headers "content-type"])]
    (or (str/starts-with? ct "application/x-www-form-urlencoded")
        (str/starts-with? ct "multipart/form-data"))))

(defn with-signals [read-json]
  (fn [handler]
    (fn [request]
      (if (d*/datastar-request? request)
        (if (form-content-type? request)
          (handler request)  ; No signals for form requests
          (let [raw-signals (d*/get-signals request)]
            (if (some? raw-signals)
              (handler (assoc request :signals (read-json raw-signals)))
              (handler request))))
        (handler request)))))
```

**Pros:**
- Simple, targeted fix
- Matches Datastar's documented behavior (no signals with form content type)
- No changes needed to Ring middleware stack
- Minimal code change

**Cons:**
- Adds content-type parsing logic to TWK

### Option B: Wrap JSON parsing in try-catch

**Description:** Catch JSON parse errors and treat them as "no signals".

```clojure
(defn with-signals [read-json]
  (fn [handler]
    (fn [request]
      (if (d*/datastar-request? request)
        (let [raw-signals (d*/get-signals request)]
          (if (some? raw-signals)
            (let [signals (try (read-json raw-signals) (catch Exception _ nil))]
              (handler (cond-> request signals (assoc :signals signals))))
            (handler request)))
        (handler request)))))
```

**Pros:**
- Handles any malformed input gracefully

**Cons:**
- Silently swallows legitimate JSON errors (bad debugging experience)
- Doesn't clearly communicate intent
- Could mask bugs in signal serialization

### Option C: Check for JSON content type explicitly

**Description:** Only parse signals when content type is explicitly JSON.

```clojure
(defn- json-content-type? [request]
  (when-let [ct (get-in request [:headers "content-type"])]
    (str/starts-with? ct "application/json")))
```

**Pros:**
- Explicit about what gets parsed

**Cons:**
- Datastar's default content type is `text/event-stream` for SSE, but signals in POST body are typically sent without explicit JSON content type
- May break existing behavior

## Recommendation

**Option A: Check Content-Type header before parsing**

This approach:
1. Aligns with Datastar's documented behavior ("no signals are sent" with form content type)
2. Is a minimal, targeted change
3. Preserves existing behavior for JSON requests
4. Makes the intent clear in code

The form content type check is the correct semantic boundary - if Datastar sends form data, it intentionally omits signals, so TWK should not attempt to parse them.

## Open Questions

- [x] Does Datastar send signals in headers when using form content type? **No** - per docs, no signals are sent at all
- [ ] Should TWK provide a helper for extracting form params in handlers?
- [ ] Should we document the form content type pattern in TWK's README?

## References

- [Datastar Form Data Example](https://data-star.dev/examples/form_data)
- [Datastar Backend Actions - contentType](https://data-star.dev/reference/actions#backend-actions)
- [Datastar Clojure SDK - signals.clj](https://github.com/starfederation/datastar-clojure/blob/main/libraries/sdk/src/main/starfederation/datastar/clojure/api/signals.clj)
- [TWK middleware.clj](src/clj/ascolais/twk/middleware.clj)
