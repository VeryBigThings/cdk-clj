[![GitHub Action Status](https://github.com/verybigthings/cdk-clj/workflows/build/badge.svg)](https://github.com/verybigthings/cdk-clj/actions)

## Prerequisites

`cdk-clj` requires:

1. [Clojure](https://clojure.org/guides/getting_started)
1. [Node.js](https://nodejs.org/en/)
1. [AWS CDK CLI](https://docs.aws.amazon.com/cdk/latest/guide/tools.html)
1. [AWS Credentials](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html)

## Quick Start

1. Add this inside `deps.edn` file.

``` clojure
{:paths   ["src"]
 :deps    {org.clojure/clojure {:mvn/version "1.11.1"}}
 :aliases {:dev {:extra-paths ["cdk"]
                 :extra-deps  {verybigthings/cdk-clj {:git/url "https://github.com/verybigthings/cdk-clj.git"
                                              :sha     "<LATEST SHA HERE>"}
                               ;; Required in order to use the "s3" module below
                               ;; List of all available modules [here](https://search.maven.org/search?q=software.amazon.awscdk)
                               software.amazon.awscdk/s3 {:mvn/version "1.176.0"}
                               }}}}
```

2. Create a CDK infrastructure file with the path `./cdk/cdk/entry.clj`.

``` clojure
(ns cdk.entry
  (:require [verybigthings.cdk :as cdk]))

(cdk/import [[Stack] :from "core"]
            [[Bucket] :from "s3"])

(defn AppStack
  [scope id props]
  (let [stack (Stack scope id props)]
    (Bucket stack "bucket" {:versioned true})))

(cdk/defapp exampleApp
  [app]
  (AppStack app "dev-example-app" {}))
```

3. Create `cdk.json` in the root of your project with following:

```json
{"app":"clojure -A:dev -M cdk/cdk/entry.clj"}
```

4. Verify evertying works correctly

``` shell
cdk ls
# should return `dev-example-app`
```

## Usage

**Import cdk**

:from should contain the service you are trying to import. For example if the package is `software.amazon.awscdk.services.ec2` then the :from should be `ec2`. The only exception is `software.amazon.awscdk` which contains classes such as `App`, `Stage` and `Stack`. Then :from is refered as `core`
``` clojure
(cdk/import [[Stack] :from "core"]
            [[Bucket] :from "s3"])
```

**Instantiate an object from a class**

``` clojure
;; Creates a bucket based on the CDK class software.amazon.awscdk.services.s3.Bucket
(cdk/import [[Bucket] :from "s3"])
(def bucket (Bucket parent "my-bucket" {}))
```

**Get property of an object**
``` clojure
;; Gets the bucketArn property off of the bucket instance
(cdk/get bucket :bucketArn)
```

**Call static method on class**
``` clojure
(cdk/import [[Source] :from "s3.deployment"])
;; Refer to the src directory as an asset to be uploaded
(Source/asset "./resources/public")
```

**Call instance method**
``` clojure
(cdk/import [[Bucket] :from "s3"])
;; Refer to the src directory as an asset to be uploaded
(let [stack (Stack nil "name")
      bucket (Bucket stack "bucket")]
  (is (Bucket/getBucketArn bucket)))
```

**Call enum member**
``` clojure
(cdk/import [[BucketAccessControl] :from "s3"])
BucketAccessControl/PRIVATE
```

## Contributing

Contributors are welcome to submit issues, bug reports, and feature requests. 

## License

cdk-clj is distributed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

See [LICENSE](LICENSE) for more information.
