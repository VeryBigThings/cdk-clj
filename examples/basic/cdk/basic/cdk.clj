(ns basic.cdk
  (:require [verybigthings.cdk :as cdk]))

(cdk/import [[Stack] :from "core"]
            [[ContainerImage] :from "ecs"]
            [[Repository] :from "ecr"]
            [[ApplicationLoadBalancedTaskImageOptions ApplicationLoadBalancedFargateService] :from "ecs.patterns"])

(defn TaskImageOptions [repo]
  (ApplicationLoadBalancedTaskImageOptions
   {:image (ContainerImage/fromEcrRepository repo commitHash)
    :containerPort 3001}))

(defn AppStack
  [scope id props]
  (let [stack (Stack scope id props)
        repo (Repository/fromRepositoryName stack "myRepo" "repo-name")]
    (ApplicationLoadBalancedFargateService
     stack
     "myfargate"
     {:desiredCount 1
      :taskImageOptions (TaskImageOptions repo)
      :memoryLimitMiB 2048
      :publicLoadBalancer true})))

(cdk/defapp exampleApp
  [app]
  (AppStack app "my-app-dev" {}))
