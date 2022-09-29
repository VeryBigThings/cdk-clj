(ns verybigthings.cdk-test
  (:require [clojure.test :refer [deftest is testing]]
            [verybigthings.cdk :as cdk]))

(cdk/import [[Stack] :from "core"]
            [[BucketAccessControl] :from "s3"])

(deftest cdk
  (testing "instantiating an object"
    (is (Stack nil "my-stack")))
  (testing "getting propery from instance"
    (let [stack (Stack nil "name")]
      (is (= (cdk/get stack :stackName) "name"))))
  (testing "calling static method"
    (let [stack (Stack nil "name")]
      (is (Stack/isStack stack))))
  (testing "calling enum member"
    (is BucketAccessControl/PRIVATE)))
