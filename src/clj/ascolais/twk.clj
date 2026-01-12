(ns ascolais.twk
  "Twk - Datastar integration for Sandestin

   Twk provides a Sandestin registry with effects and actions for building
   Datastar-powered web applications. Include the registry when creating
   your Sandestin dispatch, then use with-datastar middleware.

   Example:
   ```clojure
   (require '[ascolais.sandestin :as s])
   (require '[ascolais.twk :as twk])

   (def dispatch (s/create-dispatch [(twk/registry)]))

   (def app
     (twk/with-datastar ->sse-response dispatch))
   ```"
  (:require [ascolais.twk.events :as events]
            [ascolais.twk.middleware :as mw]
            [ascolais.twk.schema :as schema]
            [charred.api :as json]
            [dev.onionpancakes.chassis.core :as c]
            [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.utils :refer [def-clone]]))

(defn- create-send
  "Create the send effect handler with the given serializers"
  [write-json write-html]
  {:ascolais.sandestin/description "Route Datastar events through a single send effect"
   :ascolais.sandestin/schema [:tuple
                               [:enum ::send]
                               [:enum ::patch-elements ::patch-elements-seq ::patch-signals ::execute-script]
                               :any
                               [:? :map]]
   :ascolais.sandestin/system-keys [:sse]
   :ascolais.sandestin/handler
   (fn [_ {:keys [sse]} & [action payload ?opts]]
     (let [args (if (some? ?opts) [payload ?opts] [payload])]
       (case action
         ::patch-elements     (apply events/patch-elements! write-html sse args)
         ::patch-elements-seq (apply events/patch-elements-seq! write-html sse args)
         ::patch-signals      (apply events/patch-signals! write-json sse args)
         ::execute-script     (apply d*/execute-script! sse args))))})

(def ^:private connection-effect
  {:ascolais.sandestin/description "Returns existing SSE connection from dispatch-data"
   :ascolais.sandestin/schema [:tuple [:enum ::connection]]
   :ascolais.sandestin/handler
   (fn [{{::keys [connection]} :dispatch-data} _]
     connection)})

(def ^:private close-sse-effect
  {:ascolais.sandestin/description "Closes the SSE connection"
   :ascolais.sandestin/schema [:tuple [:enum ::close-sse]]
   :ascolais.sandestin/system-keys [:sse]
   :ascolais.sandestin/handler
   (fn [_ {:keys [sse]}]
     (d*/close-sse! sse))})

(def ^:private sse-closed-effect
  {:ascolais.sandestin/description "Dispatched on SSE connection close (noop hook for user extension)"
   :ascolais.sandestin/schema [:tuple [:enum ::sse-closed]]
   :ascolais.sandestin/handler (constantly nil)})

(def ^:private patch-elements-action
  {:ascolais.sandestin/description "Patch a hiccup element into the DOM"
   :ascolais.sandestin/schema [:or
                               [:tuple [:enum ::patch-elements] schema/Element]
                               [:tuple [:enum ::patch-elements] schema/Element schema/PatchElementsOptions]]
   :ascolais.sandestin/handler
   (fn [_ elements & [?opts]]
     [[::send ::patch-elements elements ?opts]])})

(def ^:private patch-elements-seq-action
  {:ascolais.sandestin/description "Patch multiple hiccup elements into the DOM"
   :ascolais.sandestin/schema [:or
                               [:tuple [:enum ::patch-elements-seq] [:sequential schema/Element]]
                               [:tuple [:enum ::patch-elements-seq] [:sequential schema/Element] schema/PatchElementsOptions]]
   :ascolais.sandestin/handler
   (fn [_ elements-seq & [?opts]]
     [[::send ::patch-elements-seq elements-seq ?opts]])})

(def ^:private patch-signals-action
  {:ascolais.sandestin/description "Update client-side signals"
   :ascolais.sandestin/schema [:or
                               [:tuple [:enum ::patch-signals] schema/Signals]
                               [:tuple [:enum ::patch-signals] schema/Signals schema/PatchSignalsOptions]]
   :ascolais.sandestin/handler
   (fn [_ signals & [?opts]]
     [[::send ::patch-signals signals ?opts]])})

(def ^:private execute-script-action
  {:ascolais.sandestin/description "Execute JavaScript in the browser"
   :ascolais.sandestin/schema [:or
                               [:tuple [:enum ::execute-script] schema/Script]
                               [:tuple [:enum ::execute-script] schema/Script schema/ExecuteScriptOptions]]
   :ascolais.sandestin/handler
   (fn [_ script-content & [?opts]]
     [[::send ::execute-script script-content ?opts]])})

(defn- system->state
  "Extract signals from the request for actions"
  [{:keys [request]}]
  {:signals (:signals request)})

(defn registry
  "Returns a Sandestin registry providing Datastar effects and actions.
   Include this registry when creating your Sandestin dispatch.

   Options:
   | key          | description                                         |
   |--------------+-----------------------------------------------------|
   | :write-html  | HTML serializer. Defaults to chassis/html           |
   | :write-json  | JSON serializer. Defaults to charred/write-json-str |

   Expected system shape: {:sse <SSEGenerator> :request <ring-request>}

   Effects:
   - ::send          - Routes all actions through a single audit point
   - ::connection    - Returns existing connection from dispatch-data
   - ::close-sse     - Closes the SSE connection
   - ::sse-closed    - Dispatched on connection close (noop, for user extension)

   Actions:
   - ::patch-elements     - Patch a hiccup element
   - ::patch-elements-seq - Patch multiple hiccup elements
   - ::patch-signals      - Update client signals
   - ::execute-script     - Execute JavaScript

   system->state:
   - Extracts {:signals ...} from the request

   All effects and actions include Sandestin schema registrations for
   discoverability via (s/describe dispatch ::twk/patch-elements)"
  ([] (registry {}))
  ([{:keys [write-html write-json]
     :or {write-html c/html
          write-json json/write-json-str}}]
   {:ascolais.sandestin/system->state system->state

    :ascolais.sandestin/effects
    {::send       (create-send write-json write-html)
     ::connection connection-effect
     ::close-sse  close-sse-effect
     ::sse-closed sse-closed-effect}

    :ascolais.sandestin/actions
    {::patch-elements     patch-elements-action
     ::patch-elements-seq patch-elements-seq-action
     ::patch-signals      patch-signals-action
     ::execute-script     execute-script-action}}))

(defn with-datastar
  "Ring middleware for Datastar-powered applications.

   Arguments:
   - ->sse-response: SSE adapter from Datastar SDK (e.g., http-kit, jetty)
   - dispatch: A Sandestin dispatch function (required)
   - opts: Optional configuration map

   Options:
   | key                | description                                                    |
   |--------------------+----------------------------------------------------------------|
   | ::with-open-sse?   | Auto-close SSE after dispatch. Defaults to false               |
   | ::write-profile    | SSE write profile for all responses                            |
   | ::write-html       | HTML serializer for :body. Defaults to chassis/html            |
   | ::read-json        | JSON deserializer for signals. Defaults to charred             |
   | ::html-attrs       | Attributes injected into :body hiccup forms                    |

   System provided to dispatch:
   {:sse <SSEGenerator> :request <ring-request>}

   Dispatch-data provided:
   {::response <handler-response>
    ::request <ring-request>
    ::with-open-sse? <boolean>}

   Example ring responses for handlers using with-datastar:
   ```clojure
   (require '[ascolais.twk :as twk])
   {:body [some-hiccup-form]} ; Server rendered for non-Datastar requests
   {::twk/fx [[::twk/patch-signals {:foo 1}]
              [::twk/patch-elements [:h1#demo \"Hello\"]]]} ; send datastar events
   {::twk/fx [[::twk/patch-elements [:h1#demo \"Hello\"]]]
    ::twk/connection existing-sse-gen} ; use an existing connection
   {::twk/fx [[::twk/patch-elements [:h1#demo \"Hello\"]]]
    ::twk/with-open-sse? true} ; close sse connection after sending events
   {:🚀 [[::twk/patch-elements [:h1#demo \"Hello\"]]]} ; fun with the rocket emoji alias
   ```"
  {:malli/schema schema/=>with-datastar}
  ([->sse-response dispatch]
   (mw/create-middleware ->sse-response dispatch {}))
  ([->sse-response dispatch opts]
   (mw/create-middleware ->sse-response dispatch opts)))

;;; Official SDK constants re-exported here for convenience

(def-clone CDN-url d*/CDN-url)
(def-clone CDN-map-url d*/CDN-map-url)
(def-clone id d*/id)
(def-clone retry-duration d*/retry-duration)
(def-clone selector d*/selector)
(def-clone patch-mode d*/patch-mode)
(def-clone use-view-transition d*/use-view-transition)
(def-clone only-if-missing d*/only-if-missing)
(def-clone auto-remove d*/auto-remove)
(def-clone attributes d*/attributes)
(def-clone pm-outer d*/pm-outer)
(def-clone pm-inner d*/pm-inner)
(def-clone pm-remove d*/pm-remove)
(def-clone pm-prepend d*/pm-prepend)
(def-clone pm-append d*/pm-append)
(def-clone pm-before d*/pm-before)
(def-clone pm-after d*/pm-after)
(def-clone pm-replace d*/pm-replace)

;;; Action helpers

(def-clone sse-get d*/sse-get)
(def-clone sse-post d*/sse-post)
(def-clone sse-put d*/sse-put)
(def-clone sse-patch d*/sse-patch)
(def-clone sse-delete d*/sse-delete)
