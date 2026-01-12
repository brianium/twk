(ns demo.config
  (:require [ascolais.sandestin :as s]
            [ascolais.twk :as twk]
            [demo.app :as app]
            [integrant.core :as ig]
            [starfederation.datastar.clojure.brotli :as brotli]))

(defmethod ig/init-key ::constant [_ x] x)
(derive ::initial-state ::constant)

(defmethod ig/halt-key! ::app/server [_ stop-fn]
  (stop-fn))

(def brotli? true)

(def write-profile
  "Set this to nil if you want to test without a write profile"
  (brotli/->brotli-profile))

;; Create Sandestin dispatch with Twk registry
(def dispatch
  (s/create-dispatch [(twk/registry)]))

(def config
  {::app/with-datastar (cond-> {:dispatch dispatch}
                         (and (some? write-profile)
                              (true? brotli?)) (assoc ::twk/write-profile write-profile))
   ::app/router        {:routes     app/routes
                        :middleware [(ig/ref ::app/with-datastar)]}
   ::app/handler       {:router (ig/ref ::app/router)}
   ::app/server        {:handler (ig/ref ::app/handler)}
   ::app/state         app/initial-state})
