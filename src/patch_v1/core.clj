(ns patch-v1.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer  [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [drawbridge.core :as d]))

(defonce scores (atom []))

(defn calculate-chortle-magnitude [chortle]
  (let [sub-chortles (re-seq #"(?im)ha+" chortle)
        caps (apply + (for [sub sub-chortles c sub
                            :when (Character/isUpperCase c)] 1))]
    (+ (count sub-chortles) caps)))

(defn percentile [magnitude]
  (* 100.0 (/ (count (filter (partial >= magnitude) @scores))
              (count @scores))))

(defn app [req]
  (let [chortle (slurp (:body req))
        magnitude (calculate-chortle-magnitude chortle)]
    (swap! scores conj magnitude)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (format "{\"%s\": %s, \"percentile\": %s}"
                   chortle magnitude (percentile magnitude))}))


(def drawbridge-handler
  (-> (d/ring-handler)
      (wrap-keyword-params)
      (wrap-nested-params)
      (wrap-params)
      (wrap-session)))

(defn wrap-drawbridge [handler]
  (fn [req]
    (if (= "/repl/v1" (:uri req))
      (drawbridge-handler req)
      (handler req))))

(defn -main [& [port]]
  (let [port (Integer. (or port (System/getenv "PORT")))]
    (jetty/run-jetty (wrap-drawbridge app)
                     {:port port :join? false})))