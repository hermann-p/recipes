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
     :ingreds ingreds
     :procedure procedure
     :tags  (set
             (filter
              #(< 0 (count %)) 
              (clojure.string/split (clojure.string/join tags) #"[,\n][ ]*")))
     :image image}))


(defn- dispatch-key
  "Multimethod dispatch-function, allows to select from {:name name} instead of
   {:type :name :value name}"
  [el]
  (first (keys el)))


(defmulti get-recipe dispatch-key)
(defmethod get-recipe :title [{:keys [title]}]
  (first (in-db oq/native-query :recipe {:title title})))
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
  (oc/with-db (og/open-graph-db! dbname user pass)
    (let [rcp-data (if-not (get-recipe {:title title})
                     rcp-data
                     (assoc rcp-data :title (str title " " (now))))
          recipe (oc/save! (og/vertex :recipe (assoc rcp-data :created (now))))]
      (doseq [tag (map get-tag! tags)]
        (oc/save! (og/link! tag recipe))))))


(defn new-recipe! [plaintext]
  (store-recipe! (extract-recipe plaintext)))


(defn delete-recipe! [recipe]
  (oc/with-db (og/open-graph-db! dbname user pass)
    (if-let [tags (:tags recipe)]
      (doseq [tag (filter identity (map get-tag tags))]
        (og/unlink! tag recipe)
        (if-not (seq (og/get-edges tag :out))
          (og/delete-vertex! tag)))
      (og/delete-vertex! recipe))))


(defmulti find-recipes dispatch-key)

(defmethod find-recipes :title [{:keys [title]}]
  (in-db
    oq/native-query :recipe {:title [:$like (str "%" title "%")]}))

(defmethod find-recipes :tag [{:keys [tag]}]
  (oc/with-db (og/open-graph-db! dbname user pass)
    (let [tags (oq/native-query :tag {:name [:$like (str "%" tag "%")]})]
      (reduce into [] (map #(og/get-ends % :out) tags)))))

(defmethod find-recipes :both [{:keys [both]}]
  (sort-by :title (set
   (into (or (seq (find-recipes {:title both})) [])
         (find-recipes {:tag both})))))
