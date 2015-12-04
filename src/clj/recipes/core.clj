(ns recipes.core
  (:require [ring.adapter.jetty :refer :all]
            [ring.util.response :as resp]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [recipes.dataconnection :as dc])
  (:gen-class))


(def import-cache (atom {:textfiles []
                         :num 0}))


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
  (POST "/import" req
        (let [input (json/read-str (slurp (:body req)))
              n (get input "imports")]
          (println "/import: preparing for" n " files")
          (swap! import-cache assoc :textfiles [] :num n))
        "cache ready")
  (POST "/store" req
        (let [plaintext (get (json/read-str (slurp (:body req))) "text")]
          (println "/store" (first (clojure.string/split plaintext #"\n")))
          (swap! import-cache update :textfiles conj plaintext)
          (if (= (:num @import-cache) (count (:textfiles @import-cache)))
            (do
              (println "/store: cache filled, starting import")
              (swap! import-cache assoc :num -1)
              (dc/mass-import (:textfiles @import-cache)))
            (println "  " (count (:textfiles @import-cache))
                     "of"
                     (:num @import-cache)
                     "files received")))
          "storing successfull")

  (POST "/delete" req
        (let [title (get (json/read-str (slurp (:body req))) "title")]
          (println "/delete" (first (clojure.string/split title #"\n")))
          (dc/delete-recipe! title))
          "deletion successfull")
          
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
