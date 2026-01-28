# Support Datastar Form Content Type - Implementation Plan

## Overview

Modify TWK's `with-signals` middleware to detect form-encoded requests and skip JSON parsing, allowing standard Ring form handling to work.

## Prerequisites

- [x] Research Datastar's `contentType: 'form'` behavior
- [x] Understand current `with-signals` middleware implementation
- [x] Confirm no signals are sent with form content type

## Phase 1: Core Implementation

### Tasks

- [x] Add `form-content-type?` helper function to middleware.clj (commit: e8c28a8)
- [x] Modify `with-signals` to check content type before parsing (commit: e8c28a8)
- [x] Ensure form requests pass through without `:signals` key (not `nil` value) (commit: e8c28a8)

### Implementation

In `src/clj/ascolais/twk/middleware.clj`:

```clojure
(defn- form-content-type?
  "Returns true if request has form-encoded content type.
   Datastar sends no signals with form content type, so we skip JSON parsing."
  [request]
  (when-let [ct (get-in request [:headers "content-type"])]
    (or (str/starts-with? ct "application/x-www-form-urlencoded")
        (str/starts-with? ct "multipart/form-data"))))

(defn with-signals
  "Parse signals and place on request as a map.
   Skips parsing for form-encoded requests (no signals sent by Datastar)."
  ([]
   (with-signals default-read-json))
  ([read-json]
   (fn [handler]
     (fn [request]
       (if (d*/datastar-request? request)
         (if (form-content-type? request)
           (handler request)
           (let [raw-signals (d*/get-signals request)]
             (if (some? raw-signals)
               (handler (assoc request :signals (read-json raw-signals)))
               (handler request))))
         (handler request))))))
```

## Phase 2: Testing

### Tasks

- [x] Add test for form-encoded POST request (no error thrown) (commit: e8c28a8)
- [x] Add test for form-encoded request has no `:signals` key (commit: e8c28a8)
- [x] Add test for JSON POST request still parses signals correctly (commit: e8c28a8)
- [x] Add test for multipart/form-data content type (commit: e8c28a8)
- [x] Verify existing tests still pass (commit: e8c28a8)

### Test Cases

```clojure
(deftest form-content-type-test
  (testing "form-encoded requests do not throw"
    (let [handler (-> identity (mw/with-signals))
          request {:headers {"datastar-request" "true"
                             "content-type" "application/x-www-form-urlencoded"}
                   :request-method :post
                   :body (io/input-stream (.getBytes "foo=bar&baz=qux"))}]
      (is (map? (handler request)))
      (is (not (contains? (handler request) :signals)))))

  (testing "JSON requests still parse signals"
    (let [handler (-> identity (mw/with-signals))
          request {:headers {"datastar-request" "true"
                             "content-type" "application/json"}
                   :request-method :post
                   :body (io/input-stream (.getBytes "{\"foo\":1}"))}]
      (is (= {:foo 1} (:signals (handler request)))))))
```

## Phase 3: Documentation

### Tasks

- [x] Add docstring note about form content type behavior (commit: e8c28a8)
- [ ] Consider adding example to README showing form pattern usage

## Rollout Plan

1. Implement and test locally
2. Run full test suite
3. Commit with clear message about form content type support
4. Consider patch version bump

## Rollback Plan

If issues arise:
1. Revert the content-type check, restoring original `with-signals` behavior
