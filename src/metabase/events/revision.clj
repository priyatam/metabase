(ns metabase.events.revision
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [metabase.config :as config]
            [metabase.events :as events]
            (metabase.models [card :refer [Card]]
                             [dashboard :refer [Dashboard]]
                             [revision :refer [push-revision]])))


(def revisions-topics
  "The `Set` of event topics which are subscribed to for use in revision tracking."
  #{:card-create
    :card-update
    :dashboard-create
    :dashboard-update
    :dashboard-add-cards
    :dashboard-remove-cards
    :dashboard-reposition-cards})

(def ^:private revisions-channel
  "Channel for receiving event notifications we want to subscribe to for revision events."
  (async/chan))


;;; ## ---------------------------------------- EVENT PROCESSING ----------------------------------------


(defn process-revision-event
  "Handle processing for a single event notification received on the revisions-channel"
  [revision-event]
  ;; try/catch here to prevent individual topic processing exceptions from bubbling up.  better to handle them here.
  (try
    (when-let [{topic :topic object :item} revision-event]
      (let [model   (events/topic->model topic)
            id      (events/object->model-id topic object)
            user-id (events/object->user-id object)]
        (case model
          "card"      (push-revision :entity       Card,
                                     :id           id,
                                     :object       (Card id),
                                     :user-id      user-id,
                                     :is-creation? (= :card-create topic))
          "dashboard" (push-revision :entity       Dashboard,
                                     :id           id,
                                     :object       (Dashboard id),
                                     :user-id      user-id,
                                     :is-creation? (= :dashboard-create topic)))))
    (catch Throwable e
      (log/warn (format "Failed to process revision event. %s" (:topic revision-event)) e))))



;;; ## ---------------------------------------- LIFECYLE ----------------------------------------


(defn events-init []
  (when-not (config/is-test?)
    (log/info "Starting revision events listener")
    (events/start-event-listener revisions-topics revisions-channel process-revision-event)))
