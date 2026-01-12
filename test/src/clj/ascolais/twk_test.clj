(ns ascolais.twk-test
  (:require [clojure.test :refer [deftest is testing]]
            [ascolais.twk :as twk]))

(deftest greet-test
  (testing "greet returns a greeting message"
    (is (= "Hello, World!" (twk/greet "World")))
    (is (= "Hello, Clojure!" (twk/greet "Clojure")))))
