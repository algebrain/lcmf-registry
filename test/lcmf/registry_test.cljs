(ns lcmf.registry-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [lcmf.registry :as registry]))

(defn- thrown-data [f]
  (try
    (f)
    nil
    (catch :default ex
      (ex-data ex))))

(deftest register-and-resolve-provider-test
  (let [reg (registry/make-registry)
        provider-id :users/get-user-by-id
        provider-fn (fn [{:keys [user-id]}]
                      {:ok? true :value {:id user-id}})]
    (is (true? (registry/register-provider! reg
                                            {:provider-id provider-id
                                             :module :users
                                             :provider-fn provider-fn
                                             :meta {:version "1.0"}})))
    (is (= provider-fn (registry/resolve-provider reg provider-id)))
    (is (= {:ok? true :value {:id "u-1"}}
           ((registry/require-provider reg provider-id) {:user-id "u-1"})))))

(deftest duplicate-provider-registration-test
  (let [reg (registry/make-registry)
        provider-id :users/get-user-by-id]
    (registry/register-provider! reg
                                 {:provider-id provider-id
                                  :module :users
                                  :provider-fn identity})
    (let [data (thrown-data #(registry/register-provider! reg
                                                          {:provider-id provider-id
                                                           :module :other
                                                           :provider-fn identity}))]
      (is (some? data))
      (is (= :duplicate-provider (:reason data)))
      (is (= provider-id (:provider-id data))))))

(deftest require-missing-provider-test
  (let [provider-id :users/get-user-by-id
        data (thrown-data #(registry/require-provider
                            (registry/make-registry)
                            provider-id))]
    (is (some? data))
    (is (= :missing-provider (:reason data)))
    (is (= provider-id (:provider-id data)))))

(deftest requirements-validation-and-assert-test
  (let [reg (registry/make-registry)]
    (registry/declare-requirements! reg
                                    :orders
                                    #{:users/get-user-by-id
                                      :payments/get-method})
    (is (= {:ok? false
            :missing {:orders #{:users/get-user-by-id
                                :payments/get-method}}
            :registered-provider-ids #{}
            :declared-requirements {:orders #{:users/get-user-by-id
                                              :payments/get-method}}}
           (registry/validate-requirements reg)))
    (registry/register-provider! reg
                                 {:provider-id :users/get-user-by-id
                                  :module :users
                                  :provider-fn identity})
    (registry/register-provider! reg
                                 {:provider-id :payments/get-method
                                  :module :payments
                                  :provider-fn identity})
    (is (= {:ok? true
            :missing {}
            :registered-provider-ids #{:users/get-user-by-id
                                       :payments/get-method}
            :declared-requirements {:orders #{:users/get-user-by-id
                                              :payments/get-method}}}
           (registry/validate-requirements reg)))
    (is (true? (registry/assert-requirements! reg)))))

(deftest assert-requirements-fail-fast-test
  (let [reg (registry/make-registry)]
    (registry/declare-requirements! reg :orders #{:users/get-user-by-id})
    (let [data (thrown-data #(registry/assert-requirements! reg))]
      (is (some? data))
      (is (= :missing-required-providers (:reason data)))
      (is (= {:orders #{:users/get-user-by-id}}
             (:missing data))))))

(deftest declare-requirements-merge-test
  (let [reg (registry/make-registry)]
    (registry/declare-requirements! reg :orders #{:users/get-user-by-id})
    (registry/declare-requirements! reg :orders #{:payments/get-method})
    (is (= {:ok? false
            :missing {:orders #{:users/get-user-by-id
                                :payments/get-method}}
            :registered-provider-ids #{}
            :declared-requirements {:orders #{:users/get-user-by-id
                                              :payments/get-method}}}
           (registry/validate-requirements reg)))))

(deftest invalid-arguments-test
  (let [reg (registry/make-registry)]
    (testing "invalid provider-id"
      (let [data (thrown-data #(registry/register-provider! reg
                                                            {:provider-id "users/get-user-by-id"
                                                             :module :users
                                                             :provider-fn identity}))]
        (is (some? data))
        (is (= :invalid-argument (:reason data)))))
    (testing "extra keys in provider spec are rejected"
      (let [data (thrown-data #(registry/register-provider! reg
                                                            {:provider-id :users/get-user-by-id
                                                             :module :users
                                                             :provider-fn identity
                                                             :unknown true}))]
        (is (some? data))
        (is (= :invalid-argument (:reason data)))
        (is (= :provider-spec (:field data)))))
    (testing "invalid requirements set"
      (let [data (thrown-data #(registry/declare-requirements!
                                reg
                                :orders
                                [:users/get-user-by-id]))]
        (is (some? data))
        (is (= :invalid-argument (:reason data)))))
    (testing "requirements set with non-keywords is rejected"
      (let [data (thrown-data #(registry/declare-requirements!
                                reg
                                :orders
                                #{"users/get-user-by-id"}))]
        (is (some? data))
        (is (= :invalid-argument (:reason data)))))
    (testing "resolve-provider validates provider-id"
      (let [data (thrown-data #(registry/resolve-provider reg "users/get-user-by-id"))]
        (is (some? data))
        (is (= :invalid-argument (:reason data)))
        (is (= :provider-id (:field data)))))
    (testing "require-provider validates provider-id"
      (let [data (thrown-data #(registry/require-provider reg "users/get-user-by-id"))]
        (is (some? data))
        (is (= :invalid-argument (:reason data)))
        (is (= :provider-id (:field data)))))))

(deftest cljs-registry-shape-test
  (let [reg (registry/make-registry)]
    (is (satisfies? IAtom reg))
    (is (= {:providers {}
            :requirements {}
            :sealed? false}
           @reg))
    (let [data (thrown-data #(registry/resolve-provider {} :users/get-user-by-id))]
      (is (some? data))
      (is (= :invalid-registry (:reason data))))))

(deftest registry-is-sealed-after-successful-assert-test
  (let [reg (registry/make-registry)]
    (registry/declare-requirements! reg :orders #{:users/get-user-by-id})
    (registry/register-provider! reg
                                 {:provider-id :users/get-user-by-id
                                  :module :users
                                  :provider-fn identity})
    (is (true? (registry/assert-requirements! reg)))
    (is (true? (:sealed? @reg)))
    (let [register-data (thrown-data #(registry/register-provider! reg
                                                                   {:provider-id :payments/get-method
                                                                    :module :payments
                                                                    :provider-fn identity}))
          declare-data (thrown-data #(registry/declare-requirements! reg
                                                                     :booking
                                                                     #{:payments/get-method}))]
      (is (= :registry-sealed (:reason register-data)))
      (is (= :registry-sealed (:reason declare-data))))))
