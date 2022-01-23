(defproject sailtrk "0.4.0"
  :description "Write MarineTraffic tracks to MongoDB"
  :url "http://piech.io"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies 
  [;http://clojure.org/downloads
   [org.clojure/clojure "1.10.0"]
   ;https://github.com/nrepl/nrepl
   [nrepl "0.9.0"]
   ;https://github.com/clojure/data.json/
   [org.clojure/data.json "2.4.0"]
   ;https://github.com/clj-time/clj-time
   [clj-time "0.15.2"]
   ;https://github.com/michaelklishin/monger
   [com.novemberain/monger "3.5.0"]
   ]
  :main sailtrk.core
  :aot [sailtrk.core]
  :profiles {:uberjar {:aot :all}}
  )
