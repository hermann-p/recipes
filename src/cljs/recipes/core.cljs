(ns recipes-cljs.core
  (:require [reagent.core :as r]
            [dommy.core :as dommy]
            [ajax.core :refer [POST]]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello world!"}))

(defn on-js-reload [])

(defonce search-results (r/atom []))
(defonce cur-recipe (r/atom {}))

(defn store-recipe [n-files e]
  (POST
   "/store"
   {:params {:text (-> e .-target .-result)}
    :format :json
    :keywords? true
    :handler (fn [_]
               (swap! n-files update :done inc)
               (if (= (:done @n-files) (:req n-files))
                 (dommy/toggle! (dommy/sel1 :#loading) false)))
    :error-handler #(println %)}))

(defn import-files [_]
  (let [file-list (.-files (dommy/sel1 :#fileDialog))
        n (.-length file-list)]
    ; Tell server to initialise import buffer for n recipes
    (POST "/import"
          {:params {:imports n}
           :format :json
           :keywords? true})
    ; Tell client to read files and send them to server
    (let [files (map #(aget file-list %) (range n))
          n-files (atom {:req (count files) :done 0})]
      (dommy/show! (dommy/sel1 :#loading))
      (doseq [file files]
        (let [reader (js/FileReader.)]
          (set! (.-onload reader) (partial store-recipe n-files))
          (set! (.-onerror reader) #(js/alert "Konnte" file "nicht laden!"))
          (.readAsText reader file))))))

(defn start-search [_]
  (let [query (.-value (dommy/sel1 :#searchText))
        tags? (.-checked (dommy/sel1 :#searchTags))
        title? (.-checked (dommy/sel1 :#searchTitles))
        search (cond
                 (= tags? title?) :both
                 tags? :tag
                 title? :title)]
    (dommy/show! (dommy/sel1 :#waitForSearch))
    (POST "/search"
          {:params {search query}
           :format :json
           :response-format :json
           :keywords? true
           :handler #(reset! search-results %)})))

(defn remove-recipe []
  (let [title (:title @cur-recipe)]
   (if (js/confirm (str title \newline "wirklich löschen?"))
     (POST "/delete"
           {:params {:title title}
            :format :json
            :keywords? true
            :handler #(reset! cur-recipe {})}))))

(defn select-recipe [title]
  (POST "/select"
        {:params {:title title}
         :format :json
         :response-format :json
         :keywords? true
         :handler #(reset! cur-recipe %)}))

(defn header-component []
  [:font.header (or (:title @cur-recipe) "Das Kochbuch")])

(defn result-component []
  (dommy/toggle! (dommy/sel1 :#waitForSearch) false)
  [:p
   (for [result @search-results]
     ^{:key result} [:div.rlink {:on-click #(select-recipe result)}
                     [:a result]])])

(defn recipe-component []
  [:div.inner
   (if (:title @cur-recipe) [:h1 "Zubereitung"])
   (for [step (:procedure @cur-recipe)]
     ^{:key step} [:p step])
   (if (:title @cur-recipe) [:p
           [:button {:on-click remove-recipe} "Rezept entfernen"]])])

(defn ingred-component []
  [:div.inner
   (if (:title @cur-recipe) [:h1 "Zutaten"])
   [:ul
    (for [ingredient (:ingreds @cur-recipe)]
      ^{:key ingredient} [:li [:text ingredient]])]])

(r/render-component [header-component] (dommy/sel1 :#header))
(r/render-component [recipe-component] (dommy/sel1 :#recipePanel))
(r/render-component [ingred-component] (dommy/sel1 :#ingredients))
(r/render-component [result-component] (dommy/sel1 :#resultArea))
(dommy/listen! (dommy/sel1 :#searchButton) :click start-search)
(dommy/listen! (dommy/sel1 :#importButton) :click import-files)
(dommy/listen! (dommy/sel1 :#files) :click #(dommy/toggle! (dommy/sel1 :#importDialog)))
(dommy/listen! (dommy/sel1 :#searchText) :keyup
               (fn [ev] (if (= 13 (.-keyCode ev)) (start-search ev))))

(start-search "")
