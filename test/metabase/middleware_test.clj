(ns metabase.middleware-test
  (:require [expectations :refer :all]
            [korma.core :as k]
            [ring.mock.request :as mock]
            [metabase.api.common :refer [*current-user-id* *current-user*]]
            [metabase.middleware :refer :all]
            [metabase.models.session :refer [Session]]
            [metabase.test.data :refer :all]
            [metabase.test.data.users :refer :all]
            [metabase.util :as u]))

;;  ===========================  TEST wrap-session-id middleware  ===========================

;; create a simple example of our middleware wrapped around a handler that simply returns the request
;; this works in this case because the only impact our middleware has is on the request
(def ^:private wrapped-handler
  (wrap-session-id identity))


;; no session-id in the request
(expect nil
  (-> (wrapped-handler (mock/request :get "/anyurl") )
      :metabase-session-id))


;; extract session-id from header
(expect "foobar"
  (-> (wrapped-handler (mock/header (mock/request :get "/anyurl") metabase-session-header "foobar"))
      :metabase-session-id))


;; extract session-id from cookie
(expect "cookie-session"
  (-> (wrapped-handler (assoc (mock/request :get "/anyurl") :cookies {metabase-session-cookie {:value "cookie-session"}}))
      :metabase-session-id))


;; if both header and cookie session-ids exist, then we expect the cookie to take precedence
(expect "cookie-session"
  (-> (wrapped-handler (-> (mock/header (mock/request :get "/anyurl") metabase-session-header "foobar")
                           (assoc :cookies {metabase-session-cookie {:value "cookie-session"}})))
      :metabase-session-id))


;;  ===========================  TEST enforce-authentication middleware  ===========================


;; create a simple example of our middleware wrapped around a handler that simply returns the request
(def ^:private auth-enforced-handler
  (wrap-current-user-id (enforce-authentication identity)))


(defn- request-with-session-id
  "Creates a mock Ring request with the given session-id applied"
  [session-id]
  (-> (mock/request :get "/anyurl")
      (assoc :metabase-session-id session-id)))


;; no session-id in the request
(expect response-unauthentic
  (auth-enforced-handler (mock/request :get "/anyurl")))

(defn- random-session-id []
  {:post [(string? %)]}
  (.toString (java.util.UUID/randomUUID)))

;; valid session ID
(expect (user->id :rasta)
  (let [session-id (random-session-id)]
    (k/insert Session (k/values {:id session-id, :user_id (user->id :rasta), :created_at (u/new-sql-timestamp)}))
    (-> (auth-enforced-handler (request-with-session-id session-id))
        :metabase-user-id)))


;; expired session-id
;; create a new session (specifically created some time in the past so it's EXPIRED)
;; should fail due to session expiration
(expect response-unauthentic
  (let [session-id (random-session-id)]
    (k/insert Session (k/values {:id session-id, :user_id (user->id :rasta), :created_at (java.sql.Timestamp. 0)}))
    (auth-enforced-handler (request-with-session-id session-id))))


;; inactive user session-id
;; create a new session (specifically created some time in the past so it's EXPIRED)
;; should fail due to inactive user
;; NOTE that :trashbird is our INACTIVE test user
(expect response-unauthentic
  (let [session-id (random-session-id)]
    (k/insert Session (k/values {:id session-id, :user_id (user->id :trashbird), :created_at (u/new-sql-timestamp)}))
    (auth-enforced-handler (request-with-session-id session-id))))


;;  ===========================  TEST bind-current-user middleware  ===========================


;; create a simple example of our middleware wrapped around a handler that simply returns our bound variables for users
(def ^:private user-bound-handler
  (bind-current-user (fn [_] {:user-id *current-user-id*
                              :user    (select-keys @*current-user* [:id :email])})))

(defn- request-with-user-id
  "Creates a mock Ring request with the given user-id applied"
  [user-id]
  (-> (mock/request :get "/anyurl")
      (assoc :metabase-user-id user-id)))


;; with valid user-id
(expect
    {:user-id (user->id :rasta)
     :user    {:id    (user->id :rasta)
               :email (:email (fetch-user :rasta))}}
  (user-bound-handler (request-with-user-id (user->id :rasta))))

;; with invalid user-id (not sure how this could ever happen, but lets test it anyways)
(expect
    {:user-id 0
     :user    {}}
  (user-bound-handler (request-with-user-id 0)))


;;  ===========================  TEST wrap-api-key middleware  ===========================

;; create a simple example of our middleware wrapped around a handler that simply returns the request
;; this works in this case because the only impact our middleware has is on the request
(def ^:private wrapped-api-key-handler
  (wrap-api-key identity))


;; no apikey in the request
(expect nil
  (-> (wrapped-api-key-handler (mock/request :get "/anyurl") )
      :metabase-session-id))


;; extract apikey from header
(expect "foobar"
  (-> (wrapped-api-key-handler (mock/header (mock/request :get "/anyurl") metabase-api-key-header "foobar"))
      :metabase-api-key))


;;  ===========================  TEST enforce-api-key middleware  ===========================


;; create a simple example of our middleware wrapped around a handler that simply returns the request
(def ^:private api-key-enforced-handler
  (enforce-api-key (constantly {:success true})))


(defn- request-with-api-key
  "Creates a mock Ring request with the given apikey applied"
  [api-key]
  (-> (mock/request :get "/anyurl")
      (assoc :metabase-api-key api-key)))


;; no apikey in the request, expect 403
(expect response-forbidden
  (api-key-enforced-handler (mock/request :get "/anyurl")))


;; valid apikey, expect 200
(expect
    {:success true}
  (api-key-enforced-handler (request-with-api-key "test-api-key")))


;; invalid apikey, expect 403
(expect response-forbidden
  (api-key-enforced-handler (request-with-api-key "foobar")))



;;; # ------------------------------------------------------------ FORMATTING TESTS ------------------------------------------------------------

;; `format`, being a middleware function, expects a `handler`
;; and returns a function that actually affects the response.
;; Since we're just interested in testing the returned function pass it `identity` as a handler
;; so whatever we pass it is unaffected
(def fmt (format-response identity))

;; check basic stripping
(expect {:a 1}
        (fmt {:a 1
              :b (fn [] 2)}))

;; check recursive stripping w/ map
(expect {:response {:a 1}}
        (fmt {:response {:a 1
                         :b (fn [] 2)}}))

;; check recursive stripping w/ array
(expect [{:a 1}]
        (fmt [{:a 1
               :b (fn [] 2)}]))

;; check combined recursive stripping
(expect [{:a [{:b 1}]}]
        (fmt [{:a [{:b 1
                    :c (fn [] 2)} ]}]))
