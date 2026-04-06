(ns lcmf.registry
  (:require [clojure.set :as set]))

(defn make-registry
  []
  (atom {:providers {}
         :requirements {}}))

(defn- ensure-registry! [registry]
  (when-not
      #?(:clj  (instance? clojure.lang.IAtom registry)
         :cljs (satisfies? IAtom registry))
    (throw (ex-info "registry must be an atom"
                    {:reason :invalid-registry
                     :registry registry})))
  registry)

(defn- ensure-keyword! [value field]
  (when-not (keyword? value)
    (throw (ex-info (str (name field) " must be keyword")
                    {:reason :invalid-argument
                     :field field
                     :value value}))))

(defn- ensure-fn! [value field]
  (when-not (fn? value)
    (throw (ex-info (str (name field) " must be function")
                    {:reason :invalid-argument
                     :field field
                     :value value}))))

(defn register-provider!
  [registry {:keys [provider-id module provider-fn meta]}]
  (ensure-registry! registry)
  (ensure-keyword! provider-id :provider-id)
  (ensure-keyword! module :module)
  (ensure-fn! provider-fn :provider-fn)
  (when (and (some? meta) (not (map? meta)))
    (throw (ex-info "meta must be map"
                    {:reason :invalid-argument
                     :field :meta
                     :value meta})))
  (swap! registry
         (fn [state]
           (when (contains? (:providers state) provider-id)
             (throw (ex-info "provider already registered"
                             {:reason :duplicate-provider
                              :provider-id provider-id
                              :existing-module (get-in state [:providers provider-id :module])
                              :module module})))
           (assoc-in state [:providers provider-id]
                     {:fn provider-fn
                      :module module
                      :meta (or meta {})})))
  true)

(defn resolve-provider
  [registry provider-id]
  (ensure-registry! registry)
  (ensure-keyword! provider-id :provider-id)
  (get-in @registry [:providers provider-id :fn]))

(defn require-provider
  [registry provider-id]
  (or (resolve-provider registry provider-id)
      (throw (ex-info "required provider is missing"
                      {:reason :missing-provider
                       :provider-id provider-id}))))

(defn declare-requirements!
  [registry module required-provider-ids]
  (ensure-registry! registry)
  (ensure-keyword! module :module)
  (when-not (set? required-provider-ids)
    (throw (ex-info "required-provider-ids must be set"
                    {:reason :invalid-argument
                     :field :required-provider-ids
                     :module module
                     :value required-provider-ids})))
  (when-not (every? keyword? required-provider-ids)
    (throw (ex-info "all required providers must be keywords"
                    {:reason :invalid-argument
                     :field :required-provider-ids
                     :module module
                     :value required-provider-ids})))
  (swap! registry update-in [:requirements module] (fnil into #{}) required-provider-ids)
  true)

(defn validate-requirements
  [registry]
  (ensure-registry! registry)
  (let [{:keys [providers requirements]} @registry
        provider-ids (set (keys providers))
        missing (reduce-kv (fn [acc module required]
                             (let [diff (set/difference required provider-ids)]
                               (if (empty? diff)
                                 acc
                                 (assoc acc module diff))))
                           {}
                           requirements)]
    (if (empty? missing)
      {:ok? true}
      {:ok? false
       :missing missing})))

(defn assert-requirements!
  [registry]
  (let [result (validate-requirements registry)]
    (when-not (:ok? result)
      (throw (ex-info "missing required read providers"
                      (assoc result :reason :missing-required-providers))))
    true))
