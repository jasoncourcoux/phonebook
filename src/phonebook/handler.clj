(ns phonebook.handler
  (:require [clojure.edn :as edn]
            [compojure.core :refer :all]
            [clj-uuid :as uuid]
            [schema.core :as s]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.session :as session ]
            [ring.util.response :as r]))

(import java.util.UUID)

;Acceptance criteria.
;- List all entries in the phone book.
;- Create a new entry to the phone book.
;- Remove an existing entry in the phone book.
;- Update an existing entry in the phone book.
;- Search for entries in the phone book by surname.

;A phone book entry must contain the following details:
;- Surname
;- Firstname
;- Phone number
;- Address (optional)

(def schema {:firstname s/Str
             :surname s/Str
             :phonenumber s/Str
             (s/optional-key :address) {:place s/Str
                                        :country s/Str }})

(def phonebook-db (atom {:db { }
                             :last-added "38d77ce0-6073-11e5-960a-d35f77d80ceb"}))

(defn validate [data]
   (println data)
   (try
      (s/validate schema data)
    true
    (catch Exception e (do (println  (str "exception " (.getMessage e))) false) )))

(defn get-phonebook []
  (println @phonebook-db)
  (let [phonebook (:db @phonebook-db)
        m (zipmap (map #(.toString %) (keys phonebook)) (vals phonebook))]
  (-> (r/response (pr-str m))
      (r/content-type "application/edn"))))

(defn atomic-user-add [db data]
  ;(println db )
  (let [new-uuid (.toString (UUID/randomUUID))
        new-db (assoc-in db [:db new-uuid] data)]
    (assoc-in new-db [:last-added] (clojure.string/replace (.toString new-uuid) "\"" "" ))))

(defn add-user [data]
  (println data)
  (let [parsed-data (edn/read-string data)]
  (println  parsed-data )
    (if (validate parsed-data)
      (do (let [{id :last-added} (swap! phonebook-db atomic-user-add parsed-data )]
        (println @phonebook-db)
      {:status 201 :body (pr-str id)}))
      {:status 400 :body "malformed request\n"})))

(defn delete-user [id]
  (println id )
  (if (contains? (:db @phonebook-db ) id)
    (do (swap! phonebook-db update-in [:db] dissoc id)
       {:status 200})
     {:status 404 :body (str id " does not exist\n")}))

(defn update-user [id data]
  (println "here now" id "   " @phonebook-db )
  (let [parsed-data (edn/read-string data)]
    (if (contains? (:db @phonebook-db) id)
      (do (if (validate parsed-data)
        (do (swap! phonebook-db assoc-in [:db  id] parsed-data)
          {:status 200})
          {:status 400 :body "malformed request\n"}))
    {:status 404 :body (str id " does not exist\n")})))

(defn search-users [params]
  ;(println params)
  (let [surname (:surname params)
        filtered  (into {} (filter #(= surname (:surname (second %)) ) (:db @phonebook-db)))]
      (-> (r/response (pr-str filtered))
          (r/content-type "application/edn"))))

(defroutes app-routes
  (GET "/v1/phonebook" [] (get-phonebook))
  (POST "/v1/phonebook" {body :body}  (add-user (slurp body)))
  (PUT "/v1/phonebook/:id" {body :body params :params} (update-user  (:id params) (slurp body)))
  (DELETE "/v1/phonebook/:id" [id] (delete-user id))
  (GET "/v1/phonebook/search" {params :params} (search-users params))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (handler/site)
      (session/wrap-session)))