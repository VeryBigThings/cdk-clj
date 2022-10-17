(ns verybigthings.cdk
  (:refer-clojure :exclude [import get])
  (:require [clojure.spec.alpha :as s]
            [clojure.reflect :as r]))

(s/def ::bindings
  (s/+
   (s/alt :alias (s/cat :alias symbol?)
          :as    (s/cat :class symbol?
                        :as    #{:as}
                        :alias symbol?))))

(s/def ::import-args
  (s/coll-of
   (s/cat :bindings (s/spec ::bindings)
          :from #{:from}
          :module string?)))

(defn- apply-methods [object props]
  (let [props' (seq props)]
    (reduce
     (fn [o [method value]]
       (clojure.lang.Reflector/invokeInstanceMethod o (name method) (to-array [value])))
     object
     props')))

(defn- has-builder-method? [class-fqn]
  (let [class-fqn-symbol (eval `~(symbol class-fqn))]
    (->>
     class-fqn-symbol
     r/reflect
     :members
     (filter #(contains? (:flags %) :public))
     (filter #(contains? (:flags %) :static))
     (filter #(= (:name %) (symbol "builder")))
     seq
     boolean)))

(defn- extract-props [props]
  (let [config (last props)]
    (if (map? config)
      [(drop-last props) config]
      [props {}])))

(defn- get-type [class-fqn]
  (if (has-builder-method? class-fqn)
    :builder
    (let [class-builder-sym (str class-fqn "$Builder")]
      (try
        (Class/forName class-builder-sym)
        :builder
        (catch ClassNotFoundException _
          :constructor)))))

(defn- get-builder-class-fqn-and-method [class-fqn]
  (if (has-builder-method? class-fqn)
    [class-fqn "builder"]
    [(str class-fqn "$Builder") "create"]))

(defmulti ^:private init-object (fn [type _ _] type))
(defmethod init-object :constructor [_ class-fqn props]
  (clojure.lang.Reflector/invokeConstructor (eval `~(symbol class-fqn)) (to-array props)))
(defmethod init-object :builder [_ class-fqn props]
  (let [[create-props static-method-props] (extract-props props)
        [builder-class-fqn method] (get-builder-class-fqn-and-method class-fqn)]
    (->
     builder-class-fqn
     (clojure.lang.Reflector/invokeStaticMethod method (to-array create-props))
     (apply-methods static-method-props)
     (.build))))

(defn- intern-initializator [impl-ns-sym class-sym class-fqn]
  (intern
   impl-ns-sym
   class-sym
   (fn [& props]
     (let [type (get-type class-fqn)]
       (init-object type class-fqn props)))))

(defn- get-static-method-names [class-fqn]
  (let [class-fqn-symbol (eval `~(symbol class-fqn))]
    (->>
     class-fqn-symbol
     r/reflect
     :members
     (filter #(contains? (:flags %) :public))
     (filter #(contains? (:flags %) :static))
     (filter #(not (contains? (:flags %) :enum)))
     (filter #(not (contains? (:flags %) :final)))
     (map #(:name %))
     distinct)))

(defn- intern-static-methods [target-ns-sym class-fqn]
  (let [methods (get-static-method-names class-fqn)]
    (doseq [static-method methods]
      (intern
       target-ns-sym
       static-method
       (fn [& args]
         (clojure.lang.Reflector/invokeStaticMethod class-fqn (name static-method) (to-array args)))))))

(defn- get-static-fields [class-fqn]
  (let [class-fqn-symbol (eval `~(symbol class-fqn))]
    (->>
     class-fqn-symbol
     r/reflect
     :members
     (filter #(contains? (:flags %) :public))
     (filter #(contains? (:flags %) :static))
     (filter #(contains? (:flags %) :final))
     (filter #(not (contains? (:flags %) :enum)))
     (map #(:name %))
     distinct)))

(defn- intern-static-fields [target-ns-sym class-fqn]
  (let [methods (get-static-fields class-fqn)]
    (doseq [static-method methods]
      (intern
       target-ns-sym
       static-method
       (fn []
         (clojure.lang.Reflector/getStaticField class-fqn (name static-method)))))))

(defn- get-member-methods [class-fqn]
  (let [class-fqn-symbol (eval `~(symbol class-fqn))]
    (->>
     class-fqn-symbol
     r/reflect
     :members
     (filter #(contains? (:flags %) :public))
     (filter #(not (= (name (:name %)) class-fqn)))
     (filter #(not (contains? (:flags %) :static)))
     (map #(:name %))
     distinct)))

(defn- intern-base-class-methods [target-ns-sym class-fqn]
  (let [bases (bases (Class/forName class-fqn))
        bases' (filter #(clojure.string/includes? (str %) "awscdk") bases)]
    (doseq [base bases']
      (let [methods (get-member-methods (.getCanonicalName base))]
        (doseq [method methods]
          (intern
           target-ns-sym
           method
           (fn [& args]
             (let [o (first args)
                   props (drop 1 args)]
               (clojure.lang.Reflector/invokeInstanceMethod o (name method) (to-array props))))))))))

(defn- intern-member-methods [target-ns-sym class-fqn]
  (let [methods (get-member-methods class-fqn)]
    (doseq [method methods]
      (intern
       target-ns-sym
       method
       (fn [& args]
         (let [o (first args)
               props (drop 1 args)]
           (clojure.lang.Reflector/invokeInstanceMethod o (name method) (to-array props))))))
    (intern-base-class-methods target-ns-sym class-fqn)))

(defn- get-class-fqn [module class]
  (let [package (case module
                  "core" "software.amazon.awscdk"
                  (str "software.amazon.awscdk.services." module))]
    (str package "." class)))

(defn- get-enums [class-fqn]
  (let [class-fqn-symbol (eval `~(symbol class-fqn))]
    (->>
     class-fqn-symbol
     r/reflect
     :members
     (filter #(contains? (:flags %) :enum))
     (map #(:name %)))))

(defn- intern-enum-members [target-ns-sym class-fqn]
  (let [enums (get-enums class-fqn)]
    (doseq [enum enums]
      (intern
       target-ns-sym
       enum
       (eval (symbol (str class-fqn "/" enum)))))))

(defn- import-fqn [module class-sym alias-sym]
  (let [class-fqn (get-class-fqn module class-sym)
        target-ns-sym (symbol (str "cdk-clj." class-fqn))
        impl-ns-sym (symbol (str target-ns-sym ".impl"))]
    (create-ns target-ns-sym)
    (create-ns impl-ns-sym)
    (ns-unmap impl-ns-sym class-sym)
    (intern-initializator impl-ns-sym class-sym class-fqn)
    (intern-static-methods target-ns-sym class-fqn)
    (intern-static-fields target-ns-sym class-fqn)
    (intern-member-methods target-ns-sym class-fqn)
    (intern-enum-members target-ns-sym class-fqn)
    (alias alias-sym target-ns-sym)
    (ns-unmap *ns* alias-sym)
    (refer impl-ns-sym
           :only [class-sym]
           :rename {class-sym alias-sym})))

(defn get [object key]
  (let [keys (list key)
        keys' (flatten keys)]
    (reduce
     #(%2 (bean %1))
     object
     keys')))

(defmacro import
  [& imports]
  (let [import-list (s/conform ::import-args imports)]
    (doseq [{:keys [module bindings]} import-list
            [_ {:keys [class alias]}] bindings]
      (import-fqn module (or class alias) alias))))

(defmacro defapp
  [name args & body]
  `(do
     (import [[~'App] :from "core"])
     (let [app# (~'App)]
       ((fn ~args ~@body) app#)
       (def ~name
         (doto app#
           (.synth))))))
