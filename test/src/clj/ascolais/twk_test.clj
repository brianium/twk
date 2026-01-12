(ns ascolais.twk-test
  (:require [ascolais.sandestin :as s]
            [ascolais.twk :as twk :refer [with-datastar]]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [dev.onionpancakes.chassis.core :as c]
            [ring.mock.request :as mock]
            [starfederation.datastar.clojure.protocols :as p]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Testing utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype SSEGenerator [*event-log]
  p/SSEGenerator
  (send-event! [_ event-type data-lines opts]
    (let [result [event-type data-lines opts]]
      (swap! *event-log conj result)
      result))
  (get-lock [_] nil)
  (close-sse! [_]
    (swap! *event-log conj [::close])
    true)
  (sse-gen? [_] true))

(defn test-generator
  ([]
   (test-generator (atom [])))
  ([*event-log]
   (SSEGenerator. *event-log)))

(defn ->sse-response
  [*ref]
  (fn [request opts]
    (reset! *ref {:request request :opts opts})))

(def dispatch
  "Default dispatch with Twk registry for testing"
  (s/create-dispatch [(twk/registry)]))

(defn handle
  "Given a request, a Twk response, and optional opts, returns a map with the following keys:
  | key       | description |
  |-----------|--------------
  | :request  | The request passed to ->sse-response
  | :opts     | The sse options passed to ->sse-response, including :d*.sse/on-open and :d*.sse/on-close
  | :response | A ring response - only used for testing html responses (as opposed to sse responses)"
  ([req res]
   (handle req res {}))
  ([req res opts]
   (handle req res opts dispatch))
  ([req res opts dispatch]
   (let [*ref     (atom {})
         wd       (with-datastar (->sse-response *ref) dispatch opts)
         handler  (wd (constantly res))
         res      (handler req)]
     (assoc  @*ref :response res))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test SSE responses + effects
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest html-response
  (testing "default 200 ok"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 "hello"]})]
      (is (= response {:status  200
                       :body    "<h1>hello</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}}))))
  (testing "user provided status code"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 "Bad Times"] :status 404})]
      (is (= response {:status  404
                       :body    "<h1>Bad Times</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}}))))
  (testing "user provided headers"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 "Bad Times"]
                                              :status 404
                                              :headers {"X-Rad-Header" "verycool"}})]
      (is (= response {:status  404
                       :body    "<h1>Bad Times</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"
                                 "X-Rad-Header" "verycool"}})))))

(deftest signal-inclusion
  (let [request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true")
                    (mock/json-body {:ok true}))
        result  (handle request {::twk/fx [[::twk/patch-elements [:h1#test "hello"]]]})
        {{:keys [signals]} :request} result]
    (is (= {:ok true} signals))))

(deftest patch-elements-effect
  (testing "without any options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::twk/fx [[::twk/patch-elements [:h1#test "hello"]]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements" ["elements <h1 id=\"test\">hello</h1>"] #:d*.sse{:id false :retry-duration false}] res))))
  (testing "with options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::twk/fx [[::twk/patch-elements [:h1#test "hello"] {twk/patch-mode twk/pm-append}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["mode append" "elements <h1 id=\"test\">hello</h1>"]
              #:d*.sse{:id false :retry-duration false}] res)))))

(deftest patch-elements-seq-effect
  (testing "without any options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::twk/fx [[::twk/patch-elements-seq [[:h1#a "hello"] [:h2#b "goodbye"]]]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["elements <h1 id=\"a\">hello</h1>"
               "elements <h2 id=\"b\">goodbye</h2>"]
              #:d*.sse{:id false :retry-duration false}] res))))
  (testing "with options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::twk/fx [[::twk/patch-elements-seq [[:h1#a "hello"] [:h2#b "goodbye"]] {twk/patch-mode twk/pm-append}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["mode append"
               "elements <h1 id=\"a\">hello</h1>"
               "elements <h2 id=\"b\">goodbye</h2>"]
              #:d*.sse{:id false :retry-duration false}] res)))))

(deftest patch-signals-effect
  (testing "without any options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::twk/fx [[::twk/patch-signals {:fun true}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-signals"
              ["signals {\"fun\":true}"]
              #:d*.sse{:id false :retry-duration false}] res))))
  (testing "with options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::twk/fx [[::twk/patch-signals {:fun true} {twk/only-if-missing true}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-signals"
              ["onlyIfMissing true" "signals {\"fun\":true}"]
              #:d*.sse{:id false :retry-duration false}] res)))))

(deftest execute-script-effect
  (testing "without any options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result   (handle request {::twk/fx [[::twk/execute-script "alert('Datastar! Wow!')"]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["selector body"
               "mode append"
               "elements <script data-effect=\"el.remove()\">alert('Datastar! Wow!')</script>"]
              #:d*.sse{:id false :retry-duration false}] res))))
  (testing "with options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result   (handle request {::twk/fx [[::twk/execute-script "alert('Datastar! Wow!')" {twk/auto-remove false}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["selector body"
               "mode append"
               "elements <script>alert('Datastar! Wow!')</script>"]
              #:d*.sse{:id false :retry-duration false}] res)))))

(deftest multiple-effects
  (let [request  (-> (mock/request :post "/")
                     (mock/header "datastar-request" "true"))
        result   (handle request {::twk/fx [[::twk/patch-elements [:h1#a "hello"]]
                                            [::twk/patch-elements-seq [[:h2#b "goodbye"] [:h3#c "for now"]] {twk/patch-mode twk/pm-replace}]
                                            [::twk/patch-signals {:test true}]
                                            [::twk/execute-script "alert('Datastar! Wow!')"]]})
        {:d*.sse/keys [on-open]} (:opts result)
        *event-log                 (atom [])
        _ (on-open (test-generator *event-log))]
    (is (= 4 (count @*event-log)))
    (is (= [["datastar-patch-elements"
             ["elements <h1 id=\"a\">hello</h1>"]
             #:d*.sse{:id false :retry-duration false}]
            ["datastar-patch-elements"
             ["mode replace"
              "elements <h2 id=\"b\">goodbye</h2>"
              "elements <h3 id=\"c\">for now</h3>"]
             #:d*.sse{:id false :retry-duration false}]
            ["datastar-patch-signals"
             ["signals {\"test\":true}"]
             #:d*.sse{:id false :retry-duration false}]
            ["datastar-patch-elements"
             ["selector body"
              "mode append"
              "elements <script data-effect=\"el.remove()\">alert('Datastar! Wow!')</script>"]
             #:d*.sse{:id false :retry-duration false}]] @*event-log))))

(deftest fun-enhancement
  (let [request  (-> (mock/request :post "/")
                     (mock/header "datastar-request" "true"))
        result    (handle request {:🚀 [[::twk/patch-signals {:fun true}]]})
        {:d*.sse/keys [on-open]} (:opts result)
        {[{:keys [res]}] :results} (on-open (test-generator))]
    (is (= ["datastar-patch-signals"
            ["signals {\"fun\":true}"]
            #:d*.sse{:id false :retry-duration false}] res))))

(deftest close-sse-effect
  (let [*event-log  (atom [])
        request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true"))
        result  (handle request {::twk/fx [[::twk/patch-elements [:h1#test "hello"]]
                                           [::twk/close-sse]]})
        {:d*.sse/keys [on-open]} (:opts result)
        _ (on-open (test-generator *event-log))]
    (is (= [::close] (last @*event-log)))))

(deftest status-and-headers-in-sse-response
  (let [request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true"))
        result  (handle request {::twk/fx [[::twk/patch-elements [:h1#test "hello"]]]
                                 :status 204
                                 :headers {"X-Rad-Header" "verycool"}})
        opts    (:opts result)]
    (is (= (select-keys opts [:status :headers])
           {:status 204
            :headers {"X-Rad-Header" "verycool"}}))))

(deftest using-an-existing-connection
  (let [*event-log  (atom [])
        request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true"))
        gen     (test-generator *event-log)
        {:keys [response]} (handle request {::twk/connection gen
                                            ::twk/fx         [[::twk/patch-elements [:h1#test "hello"]]]})]
    (is (= {:status 204} response))
    (is (= [["datastar-patch-elements"
             ["elements <h1 id=\"test\">hello</h1>"]
             #:d*.sse{:id false :retry-duration false}]] @*event-log))))

(deftest with-open-sse
  (testing "configured in middleware options"
    (let [*event-log  (atom [])
          request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::twk/fx [[::twk/patch-elements [:h1#test "hello"]]]} {::twk/with-open-sse? true})
          {:d*.sse/keys [on-open]} (:opts result)
          _ (on-open (test-generator *event-log))]
      (is (= [::close] (last @*event-log)))))
  (testing "configured in response"
    (let [*event-log  (atom [])
          request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::twk/fx [[::twk/patch-elements [:h1#test "hello"]]]
                                   ::twk/with-open-sse? true})
          {:d*.sse/keys [on-open]} (:opts result)
          _ (on-open (test-generator *event-log))]
      (is (= [::close] (last @*event-log)))))
  (testing "ignored when given an existing connection"
    (let [*event-log (atom [])
          request    (-> (mock/request :post "/")
                         (mock/header "datastar-request" "true"))
          _  (handle request {::twk/connection (test-generator *event-log)
                              ::twk/fx [[::twk/patch-elements [:h1#test "hello"]]]
                              ::twk/with-open-sse? true})]
      (is (not= [::close] (last @*event-log))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Middleware options
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest injected-html-attributes
  (testing "hiccup form with existing attributes"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 {:class "cool"} "hello"]} {::twk/html-attrs {:data-cool "very"}})]
      (is (= response {:status  200
                       :body    "<h1 class=\"cool\" data-cool=\"very\">hello</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}}))))
  (testing "hiccup form with no attributes"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 "hello"]} {::twk/html-attrs {:data-cool "very"}})]
      (is (= response {:status  200
                       :body    "<h1 data-cool=\"very\">hello</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}}))))
  (testing "raw values are ignored"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body (c/raw "<h1>hello</h1>")} {::twk/html-attrs {:data-cool "very"}})]
      (is (= response {:status  200
                       :body    "<h1>hello</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}})))))

(deftest using-custom-read-json-function
  (let [read-json   (fn [& _]
                      (throw (Exception. "READ JSON")))
        request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true")
                    (mock/json-body {:ok true}))]
    (is (thrown-with-msg? Exception #"READ JSON"
                          (handle request {::twk/fx [[::twk/patch-elements [:h1#test "hello"]]]} {::twk/read-json read-json})))))

(deftest using-custom-write-json-function
  (let [custom-dispatch (s/create-dispatch [(twk/registry {:write-json (constantly "{\"bigfun\":true}")})])
        request  (-> (mock/request :post "/")
                     (mock/header "datastar-request" "true"))
        result   (handle request {::twk/fx [[::twk/patch-signals {:fun true}]]} {} custom-dispatch)
        {:d*.sse/keys [on-open]} (:opts result)
        {[{:keys [res]}] :results} (on-open (test-generator))]
    (is (= ["datastar-patch-signals"
            ["signals {\"bigfun\":true}"]
            #:d*.sse{:id false :retry-duration false}] res))))

(deftest using-custom-write-html-function
  (let [request (mock/request :get "/")
        {:keys [response]} (handle request {:body [:h1 "whoa"]} {::twk/write-html (constantly "<h1>hello</h1>")})]
    (is (= response {:status  200
                     :body    "<h1>hello</h1>"
                     :headers {"Content-Type" "text/html; charset=utf-8"}}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Extending via Sandestin
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def *conn-store (atom {}))

(def *errors (atom []))

(defn reset-refs
  [f]
  (reset! *conn-store {})
  (reset! *errors [])
  (f))

(use-fixtures :each reset-refs)

(def connection-storage-interceptor
  "An interceptor supporting userland connection storage"
  {:id :connection-storage
   :before-dispatch
   (fn [{:keys [system dispatch-data] :as ctx}]
     (let [{:keys  [sse]} system
           store?  (not (::twk/with-open-sse? dispatch-data))
           conn-id (get-in dispatch-data [::twk/response ::conn-id])]
       (when (and store? conn-id)
         (swap! *conn-store assoc conn-id sse))
       ctx))
   :after-dispatch
   (fn [{:keys [errors] :as ctx}]
     (when (seq errors)
       (reset! *errors errors))
     ctx)})

(defn cowsay
  "Xtreme cowsay"
  [_ _ post-moo-text]
  (str "Moo! " post-moo-text))

(defn moo-placeholder
  [{:keys [post-moo]}]
  post-moo)

(defn async-cowsay
  [{:keys [dispatch]} & _]
  ;; Sandestin dispatch continuation: (dispatch extra-dispatch-data effects)
  (dispatch {:post-moo "Moo!"} [[::cowsay [:placeholder/moo]]]))

(defn badtimes
  "Throw an error for bad times"
  [_ _ msg]
  (throw (Exception. msg)))

(defn uc-signals
  "Very important signals"
  [{:keys [signals]}]
  [[::twk/patch-signals
    (reduce-kv
     (fn [m k v]
       (assoc m k (string/upper-case v))) {} signals)]])

(defn send-tracker
  "Interceptor that tracks when ::send effects are executed"
  [{:keys [on-send]}]
  {:ascolais.sandestin/interceptors
   [{:id ::send-tracker
     :before-effect
     (fn [{:keys [effect system] :as ctx}]
       (when (= (first effect) :ascolais.twk/send)
         (on-send (:sse system)))
       ctx)}]})

(deftest extending-dispatch
  (testing "userland connection storage via interceptors"
    (let [custom-dispatch (s/create-dispatch [(twk/registry)
                                              {:ascolais.sandestin/interceptors [connection-storage-interceptor]}])
          request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::conn-id :fun-test-conn
                                   ::twk/fx   [[::twk/patch-signals {:ok true}]]}
                          {} custom-dispatch)
          {:d*.sse/keys [on-open]} (:opts result)
          sse-gen (test-generator)
          _ (on-open sse-gen)]
      (is (= sse-gen (:fun-test-conn @*conn-store)))))

  (testing "adding effects"
    (let [custom-dispatch (s/create-dispatch [(twk/registry)
                                              {:ascolais.sandestin/effects
                                               {::cowsay {:ascolais.sandestin/handler cowsay}}}])
          request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::twk/fx  [[::twk/patch-signals {:ok true}]
                                              [::cowsay "Said the cow"]]}
                          {} custom-dispatch)
          {:d*.sse/keys [on-open]} (:opts result)
          result (on-open (test-generator))
          effect (->> result :results (filterv #(= "Moo! Said the cow" (:res %))) first)]
      (is (= "Moo! Said the cow" (:res effect)))))

  (testing "adding pure actions"
    (let [custom-dispatch (s/create-dispatch [(twk/registry)
                                              {:ascolais.sandestin/actions
                                               {::uc-signals {:ascolais.sandestin/handler uc-signals}}}])
          request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true")
                      (mock/json-body {:name "turjan"}))
          result  (handle request {::twk/fx  [[::uc-signals]]}
                          {} custom-dispatch)
          {:d*.sse/keys [on-open]} (:opts result)
          result (on-open (test-generator))
          uc (-> result :results first :effect (nth 2) :name)]
      (is (= uc "TURJAN"))))

  (testing "placeholders"
    (let [custom-dispatch (s/create-dispatch [(twk/registry)
                                              {:ascolais.sandestin/placeholders
                                               {:placeholder/moo {:ascolais.sandestin/handler moo-placeholder}}
                                               :ascolais.sandestin/effects
                                               {::cowsay {:ascolais.sandestin/handler cowsay}
                                                ::async-cowsay {:ascolais.sandestin/handler async-cowsay}}}])
          request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::twk/fx [[::async-cowsay]]}
                          {} custom-dispatch)
          {:d*.sse/keys [on-open]} (:opts result)
          result (on-open (test-generator))
          effect (->> result :results first :res :results first)]
      (is (= "Moo! Moo!" (:res effect)))))

  (testing "error capture"
    (let [custom-dispatch (s/create-dispatch [(twk/registry)
                                              {:ascolais.sandestin/effects
                                               {::badtimes {:ascolais.sandestin/handler badtimes}}
                                               :ascolais.sandestin/interceptors [connection-storage-interceptor]}])
          request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::twk/fx  [[::twk/patch-signals {:ok true}]
                                              [::badtimes "Error!"]]}
                          {} custom-dispatch)
          {:d*.sse/keys [on-open]} (:opts result)
          _ (on-open (test-generator))
          errors @*errors]
      (is (= 1 (count errors)))
      (is (= "Error!" (-> errors first :err .getMessage)))))

  (testing "interceptors can observe effect execution"
    (let [*sse-observed (atom nil)
          custom-dispatch (s/create-dispatch [(twk/registry)
                                              (send-tracker {:on-send #(reset! *sse-observed %)})])
          request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result   (handle request {::twk/fx [[::twk/patch-signals {:ok true}]]}
                           {} custom-dispatch)
          {:d*.sse/keys [on-open]} (:opts result)
          sse-gen (test-generator)
          _ (on-open sse-gen)]
      (is (= @*sse-observed sse-gen)))))
