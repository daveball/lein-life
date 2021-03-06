(ns {{ns-name}}.integration.controllers.people.core
  (:require [midje.sweet :refer [fact facts anything => against-background before after contains]]
            [com.stuartsierra.component :as component]
            [metrics.core :refer [new-registry]]
            [ring.mock.request :as mock]
            [ring.util.response :refer [get-header]]
            [{{ns-name}}.components.jetty.lifecycle :refer [create-handler]]
            [{{ns-name}}.components.mongodb.lifecycle :refer [new-mongodb]]
            [cheshire.core :refer [decode]]
            [monger.db :refer [drop-db]]
            [clojure.string :refer [split]]))

(def mongodb (new-mongodb "people_integration_tests"))

(defn validate-person-uri
  [uri]
  (re-matches #"http:\/\/{{docker-ip}}:4321\/api\/people\/.*" uri))

(defn setup
  []
  (alter-var-root #'mongodb component/start)
  (drop-db (:db mongodb)))

(defn teardown
  []
  (drop-db (:db mongodb))
  (alter-var-root #'mongodb component/stop))

(against-background [(before :contents (setup)) (after :contents (teardown))]

  (facts "when listing people but no-one exists"
    (let [app (create-handler {:people {:mongodb mongodb}})
          res (app (mock/request :get "/api/people"))]

      (fact "response has a 200 status code"
          (:status res) => 200)

      (fact "response has application/json content type"
          (get-header res "Content-Type") => "application/json")

      (fact "response has an empty vector as a result"
          (decode (:body res)) => (contains {"result" []}))))

  (facts "when creating people"
    (let [app (create-handler {:people {:mongodb mongodb}})
          res (app (mock/request :post "/api/people" {:name "gary" :location "home"}))]

      (fact "response has a 201 status code"
        (:status res) => 201)

      (fact "response has application/json content type"
        (get-header res "Content-Type") => "application/json")

      (fact "response has an empty vector as a result"
        (get-header res "Location") => validate-person-uri)))

  (facts "when listing people and people exist"
    (let [app (create-handler {:people {:mongodb mongodb}})
          res (app (mock/request :get "/api/people"))]

      (fact "the response has a 200 status code"
        (:status res) => 200)

      (fact "the response has application/json content type"
        (get-header res "Content-Type") => "application/json")

      (fact "the response contains a result with a vector value"
          (decode (:body res)) => (contains {"result" (contains (contains {"location" "home" "name" "gary"}))}))))

  (facts "when reading people"
    (let [app (create-handler {:people {:mongodb mongodb}})
          setup-response (app (mock/request :post "/api/people" {:name "erin" :location "garden"}))
          location-uri (get-header setup-response "Location")
          id (last (split location-uri #"/"))
          response (app (mock/request :get location-uri))]

      (fact "response has a 200 status code"
        (:status response) => 200)

      (fact "response has application/json content type"
        (get-header response "Content-Type") => "application/json")

      (fact "response has user data"
        (decode (:body response) true) => {:result {:id id :location "garden" :name "erin"}})))

  (facts "when updating people"
    (let [app (create-handler {:people {:mongodb mongodb}})
          setup-response (app (mock/request :post "/api/people" {:name "erin" :location "garden"}))
          location-uri (get-header setup-response "Location")
          id (last (split location-uri #"/"))
          response (app (mock/request :put location-uri {:location "table"}))]

      (fact "response has a 204 status code"
        (:status response) => 204)

      (fact "response has application/json content type"
        (get-header response "Content-Type") => "application/json")

      (fact "response contains a location header with pointer to the resurce"
        (get-header response "Location") => validate-person-uri)

     (fact "the updated data is available"
       (decode (:body (app (mock/request :get location-uri))) true) => (contains {:result (contains {:location "table"})}))))

  (facts "when deleting people"
    (let [app (create-handler {:people {:mongodb mongodb}})
          setup-response (app (mock/request :post "/api/people" {:name "erin" :location "garden"}))
          location-uri (get-header setup-response "Location")
          id (last (split location-uri #"/"))
          response (app (mock/request :delete location-uri))]

      (fact "response has a 204 status code"
        (:status response) => 204)

      (fact "response has application/json content type"
        (get-header response "Content-Type") => "application/json")

     (fact "the deleted data is not available"
       (:status (app (mock/request :get location-uri))) => 404))))
