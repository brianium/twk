(ns ascolais.twk.schema
  "Malli schemas for Twk Datastar integration"
  (:require [starfederation.datastar.clojure.adapter.common :as ac]
            [starfederation.datastar.clojure.api :as d*])
  (:import (java.io OutputStream Writer)))

(def Hiccup
  [:schema
   {:registry
    {"hiccup" [:orn
               [:node [:catn
                       [:name keyword?]
                       [:props [:? [:map-of keyword? any?]]]
                       [:children [:* [:schema [:ref "hiccup"]]]]]]
               [:primitive [:orn
                            [:nil nil?]
                            [:boolean boolean?]
                            [:number number?]
                            [:text string?]]]]}}
   "hiccup"])

(def Element Hiccup)

(def Script string?)

(def Signals
  [:map-of keyword? any?])

(def PatchElementsOptions
  [:map {:closed true}
   [d*/id {:optional true} :string]
   [d*/retry-duration {:optional true} number?]
   [d*/selector {:optional true} :string]
   [d*/patch-mode {:optional true}
    [:enum
     d*/pm-inner
     d*/pm-outer
     d*/pm-prepend
     d*/pm-append
     d*/pm-before
     d*/pm-after
     d*/pm-remove
     d*/pm-replace]]
   [d*/use-view-transition {:optional true} :boolean]])

(def PatchSignalsOptions
  [:map {:closed true}
   [d*/id {:optional true} :string]
   [d*/retry-duration {:optional true} number?]
   [d*/only-if-missing {:optional true} :boolean]])

(def ExecuteScriptOptions
  [:map {:closed true}
   [d*/id {:optional true} :string]
   [d*/retry-duration {:optional true} number?]
   [d*/auto-remove {:optional true} :boolean]
   [d*/attributes {:optional true} [:map-of keyword? string?]]])

(def PatchElementsAction
  [:or
   [:tuple [:enum :ascolais.twk/patch-elements] Element]
   [:tuple [:enum :ascolais.twk/patch-elements] Element PatchElementsOptions]])

(def PatchElementsSeqAction
  [:or
   [:tuple [:enum :ascolais.twk/patch-elements-seq] [:sequential Element]]
   [:tuple [:enum :ascolais.twk/patch-elements-seq] [:sequential Element] PatchElementsOptions]])

(def PatchSignalsAction
  [:or
   [:tuple [:enum :ascolais.twk/patch-signals] Signals]
   [:tuple [:enum :ascolais.twk/patch-signals] Signals PatchSignalsOptions]])

(def ExecuteScriptAction
  [:or
   [:tuple [:enum :ascolais.twk/execute-script] Script]
   [:tuple [:enum :ascolais.twk/execute-script] Script ExecuteScriptOptions]])

(def DatastarAction
  [:orn
   [:elements PatchElementsAction]
   [:elements-seq PatchElementsSeqAction]
   [:signals PatchSignalsAction]
   [:script ExecuteScriptAction]
   [:user [:and vector? [:fn (comp keyword? first)]]]])

(def DatastarResponse
  "Twk Datastar response"
  [:map
   [:ascolais.twk/fx {:description "Effects to dispatch"} [:vector DatastarAction]]
   [:ascolais.twk/with-open-sse?
    {:description "Response level override for whether SSE connection closes automatically" :optional true} :boolean]
   [:ascolais.twk/connection {:description "An existing open SSE connection" :optional true} :some]])

(def ReadJson
  [:=> [:cat :any] :any])

(def WriteJson
  [:=> [:cat :any] :string])

(def WriteHtml
  [:=> [:cat Hiccup] :string])

;;; Write profile schema lifted from starfederation.datastar.clojure.adapter.common-schemas

(defn output-stream? [o]
  (instance? OutputStream o))

(def output-stream-schema
  [:fn {:error/message "should be a java.io.OutputStream"}
   output-stream?])

(defn writer? [x]
  (instance? Writer x))

(def writer-schema
  [:fn {:error/message "should be a java.io.Writer"}
   writer?])

(def wrap-output-stream-schema
  [:-> output-stream-schema writer-schema])

(def WriteProfile
  [:map
   [ac/wrap-output-stream wrap-output-stream-schema]
   [ac/write! fn?]
   [ac/content-encoding :string]])

(def WithDatastarOpts
  [:map
   [:ascolais.twk/html-attrs {:optional true :description "Attributes injected into hiccup forms in :body"} map?]
   [:ascolais.twk/write-html {:optional true :description "HTML serialization function"} WriteHtml]
   [:ascolais.twk/read-json {:optional true :description "JSON deserialization function for signals"} ReadJson]
   [:ascolais.twk/write-profile {:optional true} WriteProfile]
   [:ascolais.twk/with-open-sse? {:optional true :description "Auto-close SSE after dispatch"} :boolean]])

(def =>with-datastar
  [:function
   [:=> [:cat ifn? ifn?] ifn?]
   [:=> [:cat ifn? ifn? WithDatastarOpts] ifn?]])
