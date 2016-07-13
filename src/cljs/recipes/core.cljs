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
                [:ul
                 (for [ingr (clojure.string/split ingreds #"\n")]
                   [:li ingr])]]))

 
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
        selector (hipo/create [:p [:a.rlink title]])]
    (dommy/listen! selector :click click-handler)))


(defn display-search-results [results]
  (dommy/toggle! (dommy/sel1 :#waitForSearch) false)
  (let [area (sel1 :#resultArea)]
    (dommy/clear! area)
    (doseq [el (map create-selector results)]
      (dommy/append! area el))))


(defn start-search [_]
  (let [query (.-value (sel1 :#searchText))
        tags? (.-checked (sel1 :#searchTags))
        title? (.-checked (sel1 :#searchTitles))
        search (cond
                 (= tags? title?) :both
                 tags? :tag
                 title? :title)]
    (dommy/show! (dommy/sel1 :#waitForSearch))
    (.log js/console "Query:" query)
    (POST "/search"
          {:params {search query}
           :format :json
           :response-format :json
           :keywords? true
           :handler display-search-results
           :error-handler error-handler})))


(defn store-recipe [n-files e]
  (.log js/console (-> e .-target .-result))
  (POST
   "/store"
   {:params {:text (-> e .-target .-result)}
    :format :json
    :keywords? true
    :handler (fn [_]
               (swap! n-files update :done inc)
               (if (= (:done @n-files) (:req n-files))
                 (dommy/toggle! (dommy/sel1 :#loading) false)))
    :error-handler error-handler}))


(defn import-files [_]
  (let [file-list (.-files (sel1 :#fileDialog))
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
          (.readAsText reader file))
        (.log js/console (.-name file))))))


(defn toggle-import [_]
  (dommy/toggle! (sel1 :#importDialog)))

(defn toggle-filters [_]
  (let [filter-panel (sel1 :#filter)
        ingred-panel (sel1 :#ingredients)
        recipe-panel (sel1 :#recipePanel)]
    (.log js/console filter-panel "\n" ingred-panel "\n" recipe-panel)
    (if (neg? (first
               (clojure.string/split
                (dommy/style filter-panel :left) #"%")))
      (do ; maximise
        (.log js/console "Maximising...")
        (dommy/set-style! filter-panel :left "0")
        (dommy/set-style! ingred-panel :width "30%" :left "28%")
        (dommy/set-style! recipe-panel :left "61%"))
      (do ; minimise
        (.log js/console "Minimising")
        (dommy/set-style! filter-panel :left "-20%")
        (dommy/set-style! ingred-panel :left "8%" :width "35%")
        (dommy/set-style! recipe-panel :left "46%")))))

(dommy/listen! (sel1 :#searchButton) :click start-search)
(dommy/listen! (sel1 :#importButton) :click import-files)
(dommy/listen! (sel1 :#files) :click toggle-import)
(dommy/listen! (sel1 :#searchText) :keyup
               (fn [ev] (if (= 13 (.-keyCode ev)) (start-search ev))))
;(dommy/listen! (sel1 :#filter) :click toggle-filters)

(start-search "")
