(ns server
  (:require
   [org.httpkit.server :as http]
   [clojure.core.match :refer [match]]
   [clojure.string :as str]
   [selmer.parser :as parser]
   [babashka.pods :as pods]))

(pods/load-pod 'org.babashka/go-sqlite3 "0.0.1")
(require '[pod.babashka.go-sqlite3 :as sqlite])

(def db "guestbook.db")

(defn execute! [stmt]
  (sqlite/execute! db stmt))

(defn query [stmt]
  (sqlite/query db stmt))

(defn guestbook-up []
  (execute!
   ["create table if not exists guestbook
     (author TEXT, message TEXT, timestamp TEXT)"]))

(defn now-str []
  (let [date (java.time.LocalDateTime/now)
        formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")]
    (.format date formatter)))

(defn add-message! [{:keys [author message]}]
  (execute!
   ["insert into guestbook
    (author, message, timestamp)
     VALUES
     (?, ?, ?)"
    author message (now-str)]))

(defn get-messages []
  (query ["select * from guestbook"]))

(def server (atom nil))

(defn form-decode 
  "Decodes url-encoded form parameters into a map of keyword->string"
  [s]
  (-> s
      (java.net.URLDecoder/decode)
      (str/split #"&")
      (->>
       (reduce
        (fn [params segment]
          (let [[k v] (str/split segment #"=" 2)]
            (assoc params (keyword k) v)))
        {}))))

(defn render-response [file req]
  {:status 200
   :content-type "text/html"
   :body (parser/render (slurp file) req)})

(defn post-msg-and-redirect [req]
  (let [params (form-decode (slurp (:body req)))]
    (println params)
    (add-message! params)
    {:status 302
     :headers {"Location" "/guestbook"}
     :body ""}))

(defn router [{:keys [uri request-method] :as req}]
  (match [uri request-method]
    ["/" _]             (render-response "templates/home.html" req)
    ["/guestbook" :get] (render-response "templates/guestbook.html" (assoc req :messages (get-messages)))
    ["/guestbook" :post] (post-msg-and-redirect req)
    :else nil))

(defn handler [req]
  (or
   (router req)
   {:status 404
    :content-type "text/plain"
    :body "404 - Page Not Found"}))


(defn restart-server! []
  (swap! server
         (fn [s]
           (when s 
             (s))
           (http/run-server #'handler {:port 3000}))))

(defn -main []
  (restart-server!)
  @(promise))

(comment
  (java.net.URLDecoder/decode "foo=bar&bar=baz")

  (restart-server!) 
  
  (guestbook-up)
  (execute! ["delete from guestbook"])
  (query ["select * from guestbook"])
  (add-message! {:author "Scot"
                 :message "Hello Audience!"}))