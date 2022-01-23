(ns sailtrk.core
  (:require
   [nrepl.server :as nrepl]
   [clojure.data.json :as json]
   [clj-time.core :as time]
   [clj-time.format :as ftime]
   [monger.core :as mg]
   [monger.credentials :as mcr]
   [monger.collection :as mc]
   )
  (:gen-class)
  )

;;;
;;; Static parameters
;;;

(def loc-timezone "America/Los_Angeles")

;;;
;;; MongoDB database of sailing tracks
;;;

;;; Mongo connection and db objects

(def mgconn
  (case (System/getenv "TRKDB")
    "openshift" (let [host (System/getenv "MONGODB_SERVICE_HOST")
                      port (Integer/parseInt
                            (System/getenv "MONGODB_SERVICE_PORT"))
                      uname (System/getenv "SLCAL_MGUSR")
                      dbname (System/getenv "SLCAL_MGDB")
                      pwd-raw (System/getenv "SLCAL_MGPWD")
                      pwd (.toCharArray pwd-raw)
                      creds (mcr/create uname dbname pwd)]
                  (mg/connect-with-credentials host port creds))
    "local" (mg/connect)
    "atlas" nil
    ))

(def mgdb
  (case (System/getenv "TRKDB")
    "atlas" (let [atlas-username (System/getenv "ATLAS_USERNAME")
                  atlas-password (System/getenv "ATLAS_PASSWORD")
                  atlas-host (System/getenv "ATLAS_HOST")
                  atlas-db (System/getenv "ATLAS_DB")]
              (:db (mg/connect-via-uri
                    (str "mongodb+srv://"
                         atlas-username ":"
                         atlas-password "@"
                         atlas-host "/"
                         atlas-db))))
    (mg/get-db mgconn (System/getenv "ATLAS_DB"))
    ))


;;;
;;; Date/Time utilities
;;;

(defn cur-dtobj []
  (time/to-time-zone
   (time/now)
   (time/time-zone-for-id loc-timezone)))

(defn to-pac-tim [gmt-str]
  (let [gmt-obj
        (ftime/parse
         (ftime/formatters :date-hour-minute-second)
         gmt-str)]
    (ftime/unparse
     (ftime/with-zone
       (ftime/formatters :date-hour-minute-second)
       (time/time-zone-for-id "America/Los_Angeles"))
     gmt-obj)))

;;;
;;; De-dupe utilities
;;;

(defn tracked-instant? [dtstr points]
  (some #(= % dtstr) (map first points)))

(defn already-tracked? [dtstr]
  (let [dstr (subs dtstr 0 10)]
    (if-let [existing-trk (mc/find-one-as-map
                           mgdb
                           "tracks"
                           { :date dstr })]
      (tracked-instant? dtstr (:points existing-trk)))))

;;;
;;; for REPL
;;;

(comment
  ;; getting on-off track local
  (def rawdat
    (seq
     (json/read-str
      (slurp
       "~/data/localexample.json"
       ))))

  (count rawdat)

  (ftime/parse (nth (last rawdat) 7))

  (def thrdysago (time/minus (time/now) (time/days 3)))

  (def fildat (filter
               (fn [x]
                 (time/after? (ftime/parse (nth x 7))
                              thrdysago))
               rawdat))

  (count fildat)

        
  (mc/find-one-as-map mgdb "tracks" { :date "2016-06-11" })
  
  ;; getting on-off track OpenShift (didn't work)
  (def rawdat
    (seq
     (json/read-str
      (slurp
       "/tmp/M.Piech_20160612.json"
       ))))
  ;; writing one-off to local Mongo
  (let [procdat
        {:date
         (subs (nth (nth rawdat 0) 7) 0 10), 
         :points 
         (mapv (fn [x]
                 [(to-pac-tim (nth x 7))
                  (nth x 4)
                  (nth x 3)])
               rawdat)}]
    (mc/insert mgdb "tracks" procdat)
    )

  ;; looking at a single track
  (mc/find-one-as-map mgdb "tracks" {:date "2015-11-08"})

  ;; original 'into' before figured out mapv
  (def procdat
    {:date (subs (nth (nth rawdat 0) 7) 0 10), 
     :points (into [] 
                   (for [x rawdat] 
                     [(nth x 7) (nth x 4) (nth x 3)]))})
  (def agmt-points
    (into [] (for [x rawdat]
               [(nth x 7) (nth x 4) (nth x 3)])))

  (def bgmt-points
    (map (fn [x]
           [(nth x 7) (nth x 4) (nth x 3)])
         rawdat))

  (def cgmt-points
    (vec (map (fn [x]
                [(nth x 7) (nth x 4) (nth x 3)])
              rawdat)))

  (def dgmt-points
    (mapv (fn [x]
            [(nth x 7) (nth x 4) (nth x 3)])
          rawdat))

  ;; figuring out time thing
  (def gmt-str (nth (nth rawdat 0) 7))

  (def gmt-obj
    (ftime/parse (ftime/formatters
                  :date-hour-minute-second)
                 gmt-str))
  (def pac-str
    (ftime/unparse
     (ftime/with-zone
       (ftime/formatters :date-hour-minute-second)
       (time/time-zone-for-id "America/Los_Angeles"))
     ex-date-obj))

  (def pac-points
    (map (fn [x]
           [(to-pac-tim (nth x 7)) (nth x 4) (nth x 3)])
         rawdat))

  ;; figuring out date list for calendar
  (def trdates
    (map (fn [x] (:date x))
         (mc/find-maps mgdb "tracks")))

  ;; hand-created track
  (def newtrack
    {:date "2016-03-08"
     :points
     [["2016-03-08T13:45:00" "37.809760" "-122.410900"]
      ["2016-03-08T13:48:00" "37.819150" "-122.435400"]
      ["2016-03-08T13:51:00" "37.826040" "-122.465100"]]
     })

  (mc/insert mgdb "newtracks" newtrack)

  ;; figuring out upsert and $push
  (mc/update mgdb
             "newtracks"
             {:date "2016-03-08"}
             {"$push" {:points ["2016-03-08T13:45:00"
                                "37.809760"
                                "-122.410900"]}}
             {:upsert true})
              
  ;; convert existing Mongo from UTC to PST
  (let [oldtrks (mc/find-maps mgdb "tracks")]
    (map (fn [trk]
           (let [oldpts (:points trk)]
             (map (fn [pt]
                    (let [newdatim (to-pac-tim (nth pt 0))
                          newdate (subs newdatim 0 10)
                          newlat (nth pt 1)
                          newlon (nth pt 2)]
                      (mc/update mgdb
                                 "newtracks"
                                 {:date newdate}
                                 {"$push" {:points
                                           [newdatim
                                            newlat
                                            newlon]}}
                                 {:upsert true})))
                  oldpts)))
         oldtrks))

  ;; old test main
  (defn oldtestmain []
    (let [fname "put local example .json track file here"]
      (if-let [rawdat (seq (json/read-str (slurp fname)))]
        (map (fn [rawpt]
               (let [pdatim (to-pac-tim (nth rawpt 7))
                     pdate (subs pdatim 0 10)
                     plat (nth rawpt 4)
                     plon (nth rawpt 3)]
                 (mc/update mgdb
                            "tracks"
                            {:date pdate}
                            {"$push" {:points
                                      [pdatim
                                       plat
                                       plon]}}
                            {:upsert true})))
             rawdat))))

  (defn testmain []
    (let [fname "put local example .json track file here"]
      (when-let [rawdat (seq (json/read-str (slurp fname)))]
        (let [thrdysago (time/minus (time/now)
                                    (time/days 3))
              fildat (filter
                      (fn [x]
                        (time/after?
                         (ftime/parse (nth x 7))
                         thrdysago))
                      rawdat)
              ]
          (dorun
           (map (fn [rawpt]
                  (let [pdatim (to-pac-tim (nth rawpt 7))
                        pdate (subs pdatim 0 10)
                        plat (nth rawpt 4)
                        plon (nth rawpt 3)]
                    (if-not (already-tracked? pdatim)
                      (dorun
                       (mc/update mgdb
                                  "tracks"
                                  {:date pdate}
                                  {"$push" {:points
                                            [pdatim
                                             plat
                                             plon]}}
                                  {:upsert true})
                       (println "upserting" pdatim)
                       ))))
                fildat))
          (println "Latest track" (nth (last rawdat) 7)))
      )))
  )

;; db.tracks.count()
;; db.newtracks.count()
;; db.tracks.renameCollection( "oldtracks" )
;; db.newtracks.renameCollection( "tracks" )
;; db.oldtracks.remove()
;; db.runCommand({"drop" : "oldtracks"});

(defn -main 
  "Parse track file from e.g. MarineTraffice; write to Mongo"
  [& args]
  ; uncomment nrepl line below to debug with nrepl
  ; (defonce server (nrepl/start-server :port 7888))
  (let [fname (first args)]
    (when-let [rawdat (seq (json/read-str (slurp fname)))]
      (let [thrdysago (time/minus (time/now)
                                  (time/days 3))
            fildat (filter
                    (fn [x]
                      (time/after?
                       (ftime/parse (nth x 7))
                       thrdysago))
                    rawdat)
            ]
        (println "Starting processing")
        (dorun
         (map (fn [rawpt]
                (let [pdatim (to-pac-tim (nth rawpt 7))
                      pdate (subs pdatim 0 10)
                      plat (nth rawpt 4)
                      plon (nth rawpt 3)]
                  (if-not (already-tracked? pdatim)
                    (mc/update mgdb
                               "tracks"
                               {:date pdate}
                               {"$push" {:points
                                         [pdatim
                                          plat
                                          plon]}}
                               {:upsert true}))))
;;              fildat)) ;; use this for only last 3 days
              rawdat))
        ;; this println should be inside the let [thrdysago
        (println "Latest track" (nth (last rawdat) 7)))
      )
    ; uncomment infinite loop below to jack in with nREPL and debug
    ; (while true nil)
    ))

;;; EOF
