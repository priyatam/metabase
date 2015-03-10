(ns metabase.api.routes
  (:require [compojure.core :refer [context defroutes GET]]
            [compojure.route :as route]
            (metabase.api [annotation :as annotation]
                          [card :as card]
                          [dash :as dash]
                          [emailreport :as emailreport]
                          [org :as org]
                          [qs :as qs]
                          [query :as query]
                          [result :as result]
                          [search :as search]
                          [session :as session]
                          [user :as user])
            (metabase.api.meta [dataset :as dataset]
                               [db :as db]
                               [field :as field]
                               [table :as table])
            [metabase.middleware.auth :as auth]))

(defroutes routes
  (context "/annotation"   [] (-> annotation/routes auth/bind-current-user auth/enforce-authentication))
  (context "/card"         [] (-> card/routes auth/bind-current-user auth/enforce-authentication))
  (context "/dash"         [] (-> dash/routes auth/bind-current-user auth/enforce-authentication))
  (context "/emailreport"  [] (-> emailreport/routes auth/bind-current-user auth/enforce-authentication))
  (GET     "/health"       [] {:status 200 :body {:status "ok"}})
  (context "/meta/dataset" [] (-> dataset/routes auth/bind-current-user auth/enforce-authentication))
  (context "/meta/db"      [] (-> db/routes auth/bind-current-user auth/enforce-authentication))
  (context "/meta/field"   [] (-> field/routes auth/bind-current-user auth/enforce-authentication))
  (context "/meta/table"   [] (-> table/routes auth/bind-current-user auth/enforce-authentication))
  (context "/org"          [] (-> org/routes auth/bind-current-user auth/enforce-authentication))
  (context "/qs"           [] (-> qs/routes auth/bind-current-user auth/enforce-authentication))
  (context "/query"        [] (-> query/routes auth/bind-current-user auth/enforce-authentication))
  (context "/result"       [] (-> result/routes auth/bind-current-user auth/enforce-authentication))
  (context "/search"       [] (-> search/routes auth/bind-current-user auth/enforce-authentication))
  (context "/session"      [] session/routes)
  (context "/user"         [] (-> user/routes auth/bind-current-user auth/enforce-authentication))
  (route/not-found (fn [{:keys [request-method uri]}]
                        {:status 404
                         :body (str (.toUpperCase (name request-method)) " " uri " is not yet implemented.")})))