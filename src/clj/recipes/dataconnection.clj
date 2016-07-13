(ns recipes.dataconnection
  (:require [clj-orient.core :as oc]
            [clj-orient.graph :as og]
            [clj-orient.query :as oq]))

(def dbname "local:food")
(def user "admin")
(def pass "admin")

(defn in-db [db-fn & args]
  (oc/with-db
    (og/open-graph-db! dbname user pass)
      (apply db-fn args)))

(if-not (oc/db-exists? dbname)
  (do
    (println "Creating new OrientDB database...")
    (oc/create-db! dbname)
    (oc/with-db (og/open-graph-db! dbname user pass)
      (og/create-vertex-type! :recipe)
      (og/create-vertex-type! :tag)))
  (println "Using existing database"))


(defn extract-recipe [text]
  {:pre [(<= 3 (count (clojure.string/split text #"\n\n")))]
   :post [#(:title %) #(:ingreds %) #(:procedure %)]}
  (let [[title ingreds procedure tags image]
        (map clojure.string/join
             (clojure.string/split text #"\n\n"))]
    {:title title
     :ltitle (clojure.string/lower-case title)
     :ingreds ingreds
     :procedure procedure
     :tags  (set
             (map clojure.string/lower-case
                  (filter
                   #(< 0 (count %)) 
                   (clojure.string/split (clojure.string/join tags) #"[,\n][ ]*"))))
     :image image}))


(defn- dispatch-key
  "Multimethod dispatch-function, allows to select from {:name name} instead of
   {:type :name :value name}"
  [el]
  (first (keys el)))


(defmulti get-recipe dispatch-key)
(defmethod get-recipe :title [{:keys [title]}]
  (first (in-db oq/native-query :recipe {:ltitle (clojure.string/lower-case title)})))
(defmethod get-recipe :orid [{:keys [orid]}]
  (in-db oc/load orid))


(defn get-tag [tag-name]
  (first
   (oq/native-query :tag {:name tag-name})))

(defn get-tag!
  "create if not found"
  [tag-name]
  (or (get-tag tag-name)
      (oc/save! (og/vertex :tag {:name tag-name}))))


(defn- now []
  (let [dateformat (java.text.SimpleDateFormat. "(dd.MM.yy, HH:mm)")
        date (java.util.Date.)]
    (.format dateformat date)))


(defn store-recipe! [{:keys [tags title] :as rcp-data} & update-tags]
  (let [ltitle (clojure.string/lower-case title)
        rcp-data (if-not (get-recipe {:title title})
                     rcp-data
                     (assoc rcp-data
                            :title (str title " " (now))
                            :ltitle (clojure.string/lower-case (str title " " (now)))))
          recipe  (oc/save! (og/vertex :recipe (assoc rcp-data :created (now))))]
        (doseq [tag (map get-tag! tags)]
          (oc/save! (og/link! tag recipe)))))


(defn new-recipe! [plaintext]
  (in-db store-recipe! (extract-recipe plaintext)))


(defn- is-recipe-file? [text]
  (if-not (< 3 (count (clojure.string/split text #"\n\n")))
    (println (str "## Error: >>"
                  (first (clojure.string/split text #"\n"))
                  "<< is not good"))
    true))

(defn mass-import [text-list]
  (println "\n\nImporting" (count text-list) "items...")
  (oc/with-db
    (og/open-graph-db! dbname user pass)
    (oc/with-intent :massive-write
      (doall (map #(store-recipe! (extract-recipe %))
                  (filter is-recipe-file? text-list))))
    (println "- now"
             (count (oq/native-query :recipe {})) "recipes,"
             (count (oq/native-query :tag {})) "tags")))


(defn delete-recipe! [recipe]
  (let [recipe (if (map? recipe)
                 recipe
                 (get-recipe {:title recipe}))]
    (if recipe
      (println "- recipe found")
      (println "- no such recipe"))
    (oc/with-db (og/open-graph-db! dbname user pass)
      (if-let [tags (:tags recipe)]
        (doseq [tag (filter identity (map get-tag tags))]
          (og/unlink! tag recipe)
          (if-not (seq (og/get-edges tag :out))
            (og/delete-vertex! tag))))
      (og/delete-vertex! recipe))))


(defn- transferable [{:keys [title procedure ingreds image]}]
  {:title title
   :ingreds ingreds
   :procedure procedure
   :image image})


(defmulti find-recipes dispatch-key)

(defmethod find-recipes :title [{:keys [title]}]
  (sort-by :title
           (set
            (map transferable
                 (in-db
                  oq/native-query :recipe
                  {:ltitle [:$like (str "%" (clojure.string/lower-case title) "%")]})))))

(defmethod find-recipes :tag [{:keys [tag]}]
  (sort-by :title
           (set
            (map transferable
                 (oc/with-db (og/open-graph-db! dbname user pass)
                   (let [tags (oq/native-query
                               :tag {:name [:$like (str "%" (clojure.string/lower-case tag) "%")]})]
                     (reduce into [] (map #(og/get-ends % :out) tags))))))))

(defmethod find-recipes :both [{:keys [both]}]
  (sort-by :title
           (set
            (into (or (seq (find-recipes {:title both})) [])
                  (find-recipes {:tag both})))))
