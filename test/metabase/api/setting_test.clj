(ns metabase.api.setting-test
  (:require [expectations :refer :all]
            (metabase.models [setting :as setting]
                             [setting-test :refer [set-settings
                                                   setting-exists?
                                                   test-setting-1
                                                   test-setting-2]])
            (metabase.test [data :refer :all]
                           [util :refer :all])
            [metabase.test.data.users :refer :all]))

;; ## Helper Fns
(defn fetch-all-settings  []
  (filter (fn [{k :key}]
            (re-find #"^test-setting-\d$" (name k)))
          ((user->client :crowberto) :get 200 "setting")))

(defn fetch-setting [setting-name]
  ((user->client :crowberto) :get 200 (format "setting/%s" (name setting-name))))

;; ## GET /api/setting
;; Check that we can fetch all Settings for Org
(expect-eval-actual-first
    [{:key "test-setting-1", :value nil,     :description "Test setting - this only shows up in dev (1)", :default "Using $MB_TEST_SETTING_1"}
     {:key "test-setting-2", :value "FANCY", :description "Test setting - this only shows up in dev (2)", :default "[Default Value]"}]
    (do (set-settings nil "FANCY")
        (fetch-all-settings)))

;; Check that non-superusers are denied access
(expect "You don't have permissions to do that."
  ((user->client :rasta) :get 403 "setting"))


;; ## GET /api/setting/:key
;; Test that we can fetch a single setting
(expect-eval-actual-first
    "OK!"
    (do (test-setting-2 "OK!")
        (fetch-setting :test-setting-2)))


;; ## PUT /api/setting/:key
(expect-eval-actual-first
    ["NICE!"
     "NICE!"]
  (do ((user->client :crowberto) :put 200 "setting/test-setting-1" {:value "NICE!"})
      [(test-setting-1)
       (fetch-setting :test-setting-1)]))

;; ## Check non-superuser can't set a Setting
(expect "You don't have permissions to do that."
  ((user->client :rasta) :put 403 "setting/test-setting-1" {:value "NICE!"}))

;; ## DELETE /api/setting/:key
(expect-eval-actual-first
    ["ABCDEFG"
     nil
     false]
  (do ((user->client :crowberto) :delete 204 "setting/test-setting-1")
      [(test-setting-1)
       (fetch-setting :test-setting-1)
       (setting-exists? :test-setting-1)]))
