(ns dashdash.example
  (:use dashdash)
  (:gen-class))

(defopt some-variable :initial "default value")
(defopt port :parser int-parser :multiple? true :help "A port.")
(defopt level :parser double-parser :initial 1.0 :help "The level")
(defopt verbose :count? true)

(defn -main 
  [& args] 
  (println (process-args args))
  (println "verbose:" @verbose)
  (println "port:" @port))

