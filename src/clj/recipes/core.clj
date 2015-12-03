(ns recipes.core
  (:require [ring.adapter.jetty :refer :all]
            [ring.util.response :as resp]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [recipes.dataconnection :as dc])
  (:gen-class))


(defn slurp-json [json-str]
  (json/read-str (slurp json-str) :key-fn keyword))

(defn extract-data [{:keys [title procedure ingreds tags image]}]
  {:title title :procedure procedure :ingreds ingreds :tags tags :image image})

(defroutes app-routes
  (GET "/" [] (resp/resource-response "book.html" {:root "public"}))
  (POST "/search" {body :body}
        (let [query (slurp-json body)
              results (dc/find-recipes query)
              results (map extract-data results)]
          (println "/search query:" query)
          (json/write-str results)))
  (POST "/select" {body :body}
        (let [query (slurp-json body)
              title (:title query)
              result (dc/get-recipe {:title title})]
          (println "/select" query)
          (json/write-str (extract-data result))))
  (POST "/store" req
        (let [plaintext (get (json/read-str (slurp (:body req))) "text")]
          (println "/store" (first (clojure.string/split plaintext #"\n")))
          (dc/new-recipe! plaintext)
          "storing successfull"))

  (POST "/delete" req
        (let [title (get (json/read-str (slurp (:body req))) "title")]
          (println "/delete" title)
          (dc/delete-recipe! (dc/get-recipe {:title title}))
          "deletion successfull"))
          
  (route/resources "/")
  (route/not-found "<h1>404 - Page not found</h1>"))

(def handler
  (handler/site app-routes))

(defn -main [& args]
  (run-jetty app-routes {:port 8080 :join? false})
  (if (java.awt.Desktop/isDesktopSupported)
    (try
      (.browse (java.awt.Desktop/getDesktop)
               (java.net.URI. "http://0.0.0.0:8080"))
      (catch Exception e))))
