(ns metabase.api.card-test
  "Tests for /api/card endpoints."
  (:require [expectations :refer :all]
            [metabase.db :refer :all]
            [metabase.http-client :refer :all]
            (metabase.models [card :refer [Card]]
                             [common :as common]
                             [database :refer [Database]])
            [metabase.test.data :refer :all]
            [metabase.test.data.users :refer :all]
            [metabase.test.util :refer [match-$ expect-eval-actual-first random-name with-temp]]
            [metabase.test.util.q :refer [Q-expand]]))

;; # CARD LIFECYCLE

;; ## Helper fns
(defn post-card [card-name]
  ((user->client :rasta) :post 200 "card" {:name                   card-name
                                           :public_perms           0
                                           :can_read               true
                                           :can_write              true
                                           :display                "scalar"
                                           :dataset_query          (Q-expand aggregate count of categories)
                                           :visualization_settings {:global {:title nil}}}))

;; ## GET /api/card
;; Filter cards by database
(expect [true
         false
         true]
  (with-temp Database [{dbid :id} {:name    (random-name)
                                    :engine  :h2
                                    :details {}}]
    (with-temp Card [{id1 :id} {:name                   (random-name)
                                :public_perms           common/perms-none
                                :creator_id             (user->id :crowberto)
                                :display                :table
                                :dataset_query          {}
                                :visualization_settings {}
                                :database_id            (db-id)}]
      (with-temp Card [{id2 :id} {:name                   (random-name)
                                  :public_perms           common/perms-none
                                  :creator_id             (user->id :crowberto)
                                  :display                :table
                                  :dataset_query          {}
                                  :visualization_settings {}
                                  :database_id            dbid}]
        (let [card-returned? (fn [database-id card-id]
                               (contains? (->> ((user->client :crowberto) :get 200 "card" :f :database :model_id database-id)
                                               (map :id)
                                               set)
                                          card-id))]
          [(card-returned? (db-id) id1)
           (card-returned? dbid id1)
           (card-returned? dbid id2)])))))

;; Make sure `id` is required when `f` is :database
(expect {:errors {:id "id is required parameter when filter mode is 'database'"}}
  ((user->client :crowberto) :get 400 "card" :f :database))

;; Filter cards by table
(expect [true
         false
         true]
  (with-temp Card [{id1 :id} {:name                   (random-name)
                              :public_perms           common/perms-none
                              :creator_id             (user->id :crowberto)
                              :display                :table
                              :dataset_query          {}
                              :visualization_settings {}
                              :table_id               1}]
    (with-temp Card [{id2 :id} {:name                   (random-name)
                                :public_perms           common/perms-none
                                :creator_id             (user->id :crowberto)
                                :display                :table
                                :dataset_query          {}
                                :visualization_settings {}
                                :table_id               2}]
      (let [card-returned? (fn [table-id card-id]
                             (contains? (->> ((user->client :crowberto) :get 200 "card" :f :table :model_id table-id)
                                             (map :id)
                                             set)
                                        card-id))]
      [(card-returned? 1 id1)
       (card-returned? 2 id1)
       (card-returned? 2 id2)]))))

;; Make sure `id` is required when `f` is :table
(expect {:errors {:id "id is required parameter when filter mode is 'table'"}}
  ((user->client :crowberto) :get 400 "card" :f :table))

;; Check that only the creator of a private Card can see it
(expect [true
         false]
  (with-temp Card [{:keys [id]} {:name                   (random-name)
                                 :public_perms           common/perms-none
                                 :creator_id             (user->id :crowberto)
                                 :display                :table
                                 :dataset_query          {}
                                 :visualization_settings {}}]
    (let [can-see-card? (fn [user]
                          (contains? (->> ((user->client user) :get 200 "card" :f :all)
                                          (map :id)
                                          set)
                                     id))]
      [(can-see-card? :crowberto)
       (can-see-card? :rasta)])))


;; ## POST /api/card
;; Test that we can make a card
(let [card-name (random-name)]
  (expect-eval-actual-first (match-$ (sel :one Card :name card-name)
                              {:description nil
                               :organization_id nil
                               :name card-name
                               :creator_id (user->id :rasta)
                               :updated_at $
                               :dataset_query (Q-expand aggregate count of categories)
                               :id $
                               :display "scalar"
                               :visualization_settings {:global {:title nil}}
                               :public_perms 0
                               :created_at $
                               :database_id (db-id)
                               :table_id (id :categories)
                               :query_type "query"})
    (post-card card-name)))

;; ## GET /api/card/:id
;; Test that we can fetch a card
(let [card-name (random-name)]
  (expect-eval-actual-first
      (match-$ (sel :one Card :name card-name)
        {:description nil
         :can_read true
         :can_write true
         :organization_id nil
         :dashboard_count 0
         :name card-name
         :creator_id (user->id :rasta)
         :creator (match-$ (fetch-user :rasta)
                    {:common_name "Rasta Toucan",
                     :is_superuser false,
                     :last_login $,
                     :last_name "Toucan",
                     :first_name "Rasta",
                     :date_joined $,
                     :email "rasta@metabase.com",
                     :id $})
         :updated_at $
         :dataset_query (Q-expand aggregate count of categories)
         :id $
         :display "scalar"
         :visualization_settings {:global {:title nil}}
         :public_perms 0
         :created_at $
         :database_id (db-id)
         :table_id (id :categories)
         :query_type "query"})
    (let [{:keys [id]} (post-card card-name)]
      ((user->client :rasta) :get 200 (format "card/%d" id)))))

;; ## PUT /api/card/:id
;; Test that we can edit a Card
(let [card-name (random-name)
      updated-name (random-name)]
  (expect-eval-actual-first
      [card-name
       updated-name]
    (let [{id :id} (post-card card-name)]
      [(sel :one :field [Card :name] :id id)
       (do ((user->client :rasta) :put 200 (format "card/%d" id) {:name updated-name})
           (sel :one :field [Card :name] :id id))])))

;; ## DELETE /api/card/:id
;; Check that we can delete a card
(expect-eval-actual-first nil
  (let [{:keys [id]} (post-card (random-name))]
    ((user->client :rasta) :delete 204 (format "card/%d" id))
    (Card id)))


;; # CARD FAVORITE STUFF

;; Helper Functions
(defn fave? [card]
  ((user->client :rasta) :get 200 (format "card/%d/favorite" (:id card))))

(defn fave [card]
  ((user->client :rasta) :post 200 (format "card/%d/favorite" (:id card))))

(defn unfave [card]
  ((user->client :rasta) :delete 204 (format "card/%d/favorite" (:id card))))

;; ## GET /api/card/:id/favorite
;; Can we see if a Card is a favorite ?
(expect-let [card (post-card (random-name))]
  {:favorite false}
  (fave? card))

;; ## POST /api/card/:id/favorite
;; Can we favorite a card?
(expect-let [card (post-card (random-name))]
  [{:favorite false}
   {:favorite true}]
  [(fave? card)
   (do (fave card)
       (fave? card))])

;; DELETE /api/card/:id/favorite
;; Can we unfavorite a card?
(expect-let [card (post-card (random-name))
             get-fave? ((user->client :rasta) :get (format "card/%d/favorite" (:id card)))]
  [{:favorite false}
   {:favorite true}
   {:favorite false}]
  [(fave? card)
   (do (fave card)
       (fave? card))
   (do (unfave card)
       (fave? card))])
