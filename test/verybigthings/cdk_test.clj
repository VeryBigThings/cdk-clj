(ns verybigthings.cdk-test
  (:require [clojure.test :refer [deftest is testing]]
            [verybigthings.cdk :as cdk]))

(cdk/import [[Stack] :from "core"]
            [[BucketAccessControl] :from "s3"]
            [[DatabaseInstanceEngine] :from "rds"])

(deftest cdk
  (testing "instantiating an object"
    (is (Stack nil "my-stack")))
  (testing "getting single propery from instance"
    (let [stack (Stack nil "name")]
      (is (= (cdk/get stack :stackName) "name"))))
  (testing "getting nested propery from instance"
    (let [stack (Stack nil "name")]
      (is (= (cdk/get stack [:tags :tagPropertyName]) "tags"))))
  (testing "calling static method"
    (let [stack (Stack nil "name")]
      (is (Stack/isStack stack))))
  (testing "calling enum member"
    (is BucketAccessControl/PRIVATE))
  (testing "calling static field"
    (is (DatabaseInstanceEngine/POSTGRES))))
