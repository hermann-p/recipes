(ns recipes-cljs.core
  (:require [dommy.core :as dommy :refer-macros [sel1]]
            [ajax.core :refer [POST]]
            [hipo.core :as hipo]))


(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))


(defn delete-recipe [title]
  (fn [_]
    (if (js/confirm (str title \newline "wirklich löschen?"))
      (POST "/delete"
            {:params {:title title}
             :format :json
             :keywords? true}))))


(defn make-recipe-view [{:keys [title procedure image]}]
  (hipo/create [:p
                [:h1  {:style "text-align: center;"} title]
                (for [line (clojure.string/split procedure #"\n")]
                  [:p line])
                (if image
                  [:p [:img {:src image
                             :width "80%"}]])
                [:p [:button {:onclick (delete-recipe title)} "Rezept entfernen"]]]))


(defn make-ingr-list [{:keys [ingreds]}]
  (hipo/create [:p
                [:h2 {:style "text-align: center;"} "Zutaten"]
                (for [ingr (clojure.string/split ingreds #"\n")]
                  [:p ingr])]))

 
(defn display-recipe [response]
  (.log js/console "Displaying:" response)
  (dommy/replace-contents!
   (sel1 :#recipe)
   (make-recipe-view response))
  (dommy/replace-contents!
   (sel1 :#ingrCont)
   (make-ingr-list response)))


(defn create-selector [{:keys [title] :as recipe}]
  (let [click-handler (fn [_] (display-recipe recipe))
        selector (hipo/create [:a.rlink title [:br]])]
    (dommy/listen! selector :click click-handler)))


(defn display-search-results [results]
  (let [area (sel1 :#resultArea)]
    (dommy/clear! area)
    (doseq [el (map create-selector results)]
      (dommy/append! area el))))


(defn start-search [e]
  (let [query (.-value (sel1 :#searchText))
        tags? (.-checked (sel1 :#searchTags))
        title? (.-checked (sel1 :#searchTitles))
        search (cond
                 (= tags? title?) :both
                 tags? :tag
                 title? :title)]
    (.log js/console "Query:" query)
    (POST "/search"
          {:params {search query}
           :format :json
           :response-format :json
           :keywords? true
           :handler display-search-results
           :error-handler error-handler})))


(defn store-recipe [e]
  (.log js/console (-> e .-target .-result))
  (POST
   "/store"
   {:params {:text (-> e .-target .-result)}
    :format :json
    :keywords? true
    :handler (fn [_])
    :error-handler error-handler}))


(defn import-files [_]
  (let [file-list (.-files (sel1 :#fileDialog))
        n (.-length file-list)]
    (let [files (map #(aget file-list %) (range n))]
      (doseq [file files]
        (let [reader (js/FileReader.)]
          (set! (.-onload reader) store-recipe)
          (.readAsText reader file))
        (.log js/console (.-name file))))))


(defn toggle-import [_]
  (dommy/toggle! (sel1 :#importDialog)))


(dommy/listen! (sel1 :#searchButton) :click start-search)
(dommy/listen! (sel1 :#importButton) :click import-files)
(dommy/listen! (sel1 :#files) :click toggle-import)

(start-search "")
