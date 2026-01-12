# Twk

Datastar integration for [Sandestin](https://github.com/brianium/sandestin).

Twk provides feature parity with [datastar.wow](https://github.com/brianium/datastar.wow) while embracing Sandestin's registry and dispatch system. Instead of owning the registry concept, Twk is "just another registry" in your Sandestin-powered application.

## Installation

```clojure
;; deps.edn
{:deps {io.github.brianium/twk {:git/tag "v0.1.0" :git/sha "e3aea2b"}
        io.github.brianium/sandestin {:git/tag "v0.1.0" :git/sha "cfe9c24"}}}
```

## Quick Start

```clojure
(ns myapp.core
  (:require [ascolais.sandestin :as s]
            [ascolais.twk :as twk]
            [starfederation.datastar.clojure.adapter.http-kit :as hk]))

;; 1. Create dispatch with Twk registry
(def dispatch (s/create-dispatch [(twk/registry)]))

;; 2. Apply middleware to your Ring handler
(def app
  (-> my-handler
      (twk/with-datastar hk/->sse-response dispatch)))

;; 3. Return effects from handlers
(defn my-handler [{:keys [request-method]}]
  (case request-method
    :get {:body [:h1 "Hello, Datastar!"]}
    :post {::twk/fx [[::twk/patch-elements [:h1#greeting "Updated!"]]
                     [::twk/patch-signals {:count 42}]]}))
```

## Usage

### Registry

The `registry` function returns a Sandestin registry with Datastar effects and actions:

```clojure
;; Default serializers (Chassis for HTML, Charred for JSON)
(twk/registry)

;; Custom serializers
(twk/registry {:write-html my-html-fn
               :write-json my-json-fn})
```

Include the registry when creating your Sandestin dispatch:

```clojure
(s/create-dispatch [(twk/registry)
                    my-app-registry
                    another-registry])
```

### Middleware

```clojure
(twk/with-datastar ->sse-response dispatch)
(twk/with-datastar ->sse-response dispatch opts)
```

**Arguments:**
- `->sse-response` - SSE adapter from Datastar SDK (http-kit, jetty, etc.)
- `dispatch` - A Sandestin dispatch function (required)
- `opts` - Optional configuration map

**Options:**

| Key | Description |
|-----|-------------|
| `::twk/with-open-sse?` | Auto-close SSE after dispatch. Defaults to `false` |
| `::twk/write-profile` | SSE write profile for all responses |
| `::twk/write-html` | HTML serializer for `:body`. Defaults to Chassis |
| `::twk/read-json` | JSON deserializer for signals. Defaults to Charred |
| `::twk/html-attrs` | Attributes injected into `:body` hiccup forms |

### Handler Responses

**HTML Response (non-Datastar requests):**

```clojure
{:body [:h1 "Hello"]}
{:body [:h1 "Not Found"] :status 404}
```

**Datastar Effects:**

```clojure
{::twk/fx [[::twk/patch-elements [:div#content "New content"]]
           [::twk/patch-signals {:count 1}]
           [::twk/execute-script "console.log('hello')"]]}
```

**With Options:**

```clojure
;; Auto-close connection after sending
{::twk/fx [[::twk/patch-elements [:div "Done"]]]
 ::twk/with-open-sse? true}

;; Use existing connection
{::twk/fx [[::twk/patch-elements [:div "Update"]]]
 ::twk/connection existing-sse-gen}

;; Fun enhancement
{:🚀 [[::twk/patch-elements [:div "Rocket!"]]]}
```

### Available Actions

| Action | Description |
|--------|-------------|
| `::twk/patch-elements` | Patch a hiccup element into the DOM |
| `::twk/patch-elements-seq` | Patch multiple hiccup elements |
| `::twk/patch-signals` | Update client-side signals |
| `::twk/execute-script` | Execute JavaScript in the browser |
| `::twk/close-sse` | Close the SSE connection |

**With options:**

```clojure
[::twk/patch-elements [:div#id "content"] {twk/patch-mode twk/pm-append}]
[::twk/patch-signals {:count 1} {twk/only-if-missing true}]
[::twk/execute-script "alert('hi')" {twk/auto-remove false}]
```

### Signals

Datastar signals are automatically parsed and available on the request:

```clojure
(defn my-handler [{:keys [signals]}]
  (let [{:keys [name email]} signals]
    {::twk/fx [[::twk/patch-elements [:div (str "Hello, " name)]]]}))
```

Actions receive signals as state (via Sandestin's `system->state`):

```clojure
(defn my-action [{:keys [signals]}]
  [[::twk/patch-signals (update signals :count inc)]])
```

### Extending with Custom Effects

Add your own effects alongside Twk's:

```clojure
(def my-registry
  {:ascolais.sandestin/effects
   {::notify {:ascolais.sandestin/handler
              (fn [ctx system message]
                (send-notification! message))}}})

(def dispatch
  (s/create-dispatch [(twk/registry)
                      my-registry]))

;; Use in handlers
{::twk/fx [[::twk/patch-elements [:div "Saved"]]
           [::notify "Record saved successfully"]]}
```

### SDK Re-exports

Twk re-exports common constants from the Datastar SDK:

```clojure
;; Patch modes
twk/pm-outer twk/pm-inner twk/pm-append twk/pm-prepend
twk/pm-before twk/pm-after twk/pm-remove twk/pm-replace

;; Option keys
twk/patch-mode twk/selector twk/only-if-missing twk/auto-remove

;; SSE action helpers
twk/sse-get twk/sse-post twk/sse-put twk/sse-patch twk/sse-delete

;; CDN
twk/CDN-url twk/CDN-map-url
```

## System Contract

Twk expects the Sandestin system to have:

```clojure
{:sse <SSEGenerator>    ;; From Datastar SDK
 :request <ring-request>}
```

The middleware provides this automatically when calling dispatch.

## Comparison with datastar.wow

| Aspect | datastar.wow | Twk |
|--------|--------------|-----|
| Registry | Owns concept, accepts vector | Exports single registry |
| Dispatch | Optional, can create internally | Required, explicit parameter |
| Effect system | Nexus | Sandestin |
| Namespace | `datastar.wow/*` | `ascolais.twk/*` |

## Development

```bash
# Start REPL with demo
clj -M:dev

# Run tests
clj -X:test
```

The demo runs at http://localhost:3000 and shows a simple user profile editor with SSE-based updates.

## License

MIT
