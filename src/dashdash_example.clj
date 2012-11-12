(ns dashdash-example
  (:gen-class)
  (:use dashdash))

(def-property something
              :initial 25)

(def-property iprop
              :initial 3
              :type :integer)

(defn -main
  [& args]
  (dashdash/run-args args
                     :program-name "dashdash-example")
  (println "args:" @dashdash/args)
  (println "iprop" @iprop)
  (println "something:" @something))

