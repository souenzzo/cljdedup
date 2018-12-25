(ns cljdedup.core
  (:require [datascript.core :as d]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (java.io File)
           (java.nio.file Files LinkOption Path)
           (com.google.common.hash Hashing)
           (java.nio.file.attribute FileTime)
           (java.util Date)))

(set! *warn-on-reflection* true)

(defn db-without-attr
  [db attrs]
  (->> (d/q '[:find ?op ?e ?a ?v
              :in $ [?a ...]
              :where
              [(ground :db/retract) ?op]
              [?e ?a ?v]]
            db attrs)
       vec
       (d/with db)
       :db-after))


(defn find-by-md5
  [db]
  (->> (d/q '[:find (count ?e) ?md5-sum
              :where
              [?e :file/name ?name]
              [?e :file/md5-sum ?md5-sum]]
            db)
       (keep (fn [[count sha]]
               (when (> count 1)
                 sha)))
       (d/q '[:find [(pull ?e [*]) ...]
              :in $ [?md5 ...]
              :where
              [?e :file/md5-sum ?md5]
              [?e :file/absolute-path ?path]]
            db)
       (group-by :file/md5-sum)))

(def schema
  {:file/absolute-path {:db/unique :db.unique/identity}
   :file/contents      {:db/cardinality :db.cardinality/many
                        :db/valueType   :db.type/ref
                        :db/isComponent true}})

(defonce conn (d/create-conn schema))
(defn md5
  [f]
  (-> f
      (com.google.common.io.Files/hash (Hashing/md5))
      (.toString)))

(defn file-time?
  [x]
  (instance? FileTime x))

(defn starts-with-is?
  [x]
  (boolean (re-find #"^is[A-Z]" x)))

(defn java->clojuric-keyword
  [ns k]
  (keyword ns (-> (if (starts-with-is? k)
                    (str (subs k 2) "?")
                    k)
                  (string/replace #"[A-Z]" #(str "-" (string/lower-case %1)))
                  (string/replace #"^\-" ""))))

(defn attributes
  [^File f]
  (let [path (.toPath f)
        attributes* (Files/readAttributes path
                                          "*"
                                          ^"[Ljava.nio.file.LinkOption;"
                                          (into-array LinkOption []))
        xform (map (fn [[k v]]
                     [(java->clojuric-keyword "file" k)
                      (cond
                        (file-time? v) (Date/from (.toInstant ^FileTime v))
                        :else v)]))]
    (into {} xform attributes*)))

(defn discovery!
  [conn ^File f & {:keys [parent]}]
  (let [{:file/keys [directory?]
         :as        file-attributes} (attributes f)
        is-file? (.isFile f)
        absolute-path (.getAbsolutePath f)
        name (.getName f)
        id (d/tempid :db.part/user)
        tx-data (cond-> [(assoc file-attributes
                           :db/id id
                           :file/name name
                           :file/is-file? is-file?
                           :file/absolute-path absolute-path)]
                        is-file? (conj [:db/add id :file/md5-future-sum (future
                                                                          (let [md5 (md5 f)]
                                                                            @(d/transact-async conn [{:file/absolute-path absolute-path
                                                                                                      :file/md5-sum       md5}])
                                                                            md5))])
                        (string? parent) (conj [:db/add [:file/absolute-path parent] :file/contents id]))]
    @(d/transact-async conn tx-data)
    (when directory?
      (doseq [f (.listFiles f)]
        (discovery! conn f :parent absolute-path)))))

(defn -main
  "I don't do a whole lot."
  [& argv]
  (printf "Hello World! %s\n" argv))
