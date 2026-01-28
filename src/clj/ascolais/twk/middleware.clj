(ns ascolais.twk.middleware
  "Implementation for the with-datastar middleware"
  (:require [charred.api :as json]
            [clojure.set :as set]
            [clojure.string :as str]
            [dev.onionpancakes.chassis.core :as c]
            [starfederation.datastar.clojure.api :as d*]))

(def default-read-json (json/parse-json-fn {:async? false :bufsize 1024 :key-fn keyword}))

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

(defn- datastar-response?
  [response]
  (and (map? response)
       (contains? response :ascolais.twk/fx)))

(defn- get-connection
  "The connection used by dispatch. Order of priority is:
   - 1. A :ascolais.twk/connection key on the response map returned by a handler
   - 2. A :ascolais.twk/connection key found in dispatch-data
   - 3. A new sse-gen created in sse-response"
  [dispatch request dispatch-data]
  (if-some [connection (get-in dispatch-data [:ascolais.twk/response :ascolais.twk/connection])]
    connection
    (let [dispatch-result (dispatch {:sse nil :request request} dispatch-data [[:ascolais.twk/connection]])]
      (some->
       dispatch-result
       (:results)
       (first)
       (:res)))))

(defn- sse-response
  [request response opts ->sse-response dispatch]
  (let [{:ascolais.twk/keys [with-open-sse? write-profile]} opts
        actions (:ascolais.twk/fx response)
        dispatch-data (-> {:ascolais.twk/response response :ascolais.twk/request request}
                          (assoc :ascolais.twk/with-open-sse? (response :ascolais.twk/with-open-sse? with-open-sse?)))
        wp            (response :ascolais.twk/write-profile write-profile)]
    (if-some [connection (get-connection dispatch request dispatch-data)]
      (do (dispatch {:sse connection :request request} dispatch-data actions)
          {:status 204})
      (->sse-response
       request
       (cond-> {:d*.sse/on-close
                (fn [& _]
                  (dispatch {:sse nil :request request} dispatch-data [[:ascolais.twk/sse-closed]]))
                :d*.sse/on-open
                (fn [sse]
                  (let [system {:sse sse :request request}]
                    (if (:ascolais.twk/with-open-sse? dispatch-data)
                      (d*/with-open-sse sse
                        (dispatch system dispatch-data actions))
                      (dispatch system dispatch-data actions))))}
         (some? wp) (assoc :d*.sse/write-profile wp)
         (int? (:status response))  (assoc :status (:status response))
         (map? (:headers response)) (assoc :headers (:headers response)))))))

(def fun-enhancers
  "If we aren't having fun, then what is the point?"
  {:🚀 :ascolais.twk/fx})

(defn with-dispatch
  [dispatch opts ->sse-response]
  (fn [handler]
    (fn [request]
      (let [response (set/rename-keys (handler request) fun-enhancers)
            dsr?     (datastar-response? response)]
        (if-not dsr?
          response
          (let [datastar? (d*/datastar-request? request)]
            (if-not datastar?
              (dissoc response :ascolais.twk/fx :ascolais.twk/connection :ascolais.twk/with-open-sse?)
              (sse-response request response opts ->sse-response dispatch))))))))

(defn- with-attrs
  [h attrs]
  (if (and (vector? h) (map? attrs) (seq attrs))
    (let [[_ b & _] h]
      (if (map? b)
        (update h 1 merge attrs)
        (vec (concat (subvec h 0 1)
                     [attrs]
                     (subvec h 1)))))
    h))

(defn with-html
  "hiccup forms in :body will be assumed to be a 200 OK html response. :body is ignored
   if request is a datastar request."
  [write-html attrs]
  (fn [handler]
    (fn [request]
      (let [res (handler request)]
        (if (and (not (d*/datastar-request? request))
                 (map? res)
                 (contains? res :body))
          (-> (merge res {:body    (write-html (with-attrs (:body res) attrs))
                          :headers {"Content-Type" "text/html; charset=utf-8"}})
              (assoc :status (:status res 200))
              (update :headers (fn [h]
                                 (if (map? (:headers res))
                                   (merge h (:headers res))
                                   h))))
          res)))))

(defn create-middleware
  "Create the with-datastar middleware stack"
  [->sse-response dispatch {:ascolais.twk/keys [html-attrs read-json with-open-sse? write-html write-profile]
                            :or {html-attrs     {}
                                 read-json      default-read-json
                                 with-open-sse? false}}]
  (let [html-fn  (or write-html c/html)
        signals  (with-signals read-json)
        html     (with-html html-fn html-attrs)]
    (comp
     signals
     html
     (with-dispatch
       dispatch
       {:ascolais.twk/with-open-sse? with-open-sse?
        :ascolais.twk/write-profile  write-profile}
       ->sse-response))))
