(ns dashdash
  (:require clojure.string)
  (:import java.util.regex.Pattern))

(defonce all-options (atom []))

(defmacro defopt 
  "Define an option"
  [argname & kwopts]
  `(let [opts# (hash-map ~@kwopts)
         name# (get opts# :name (str (quote ~argname)))
         to-opt-name# #(clojure.string/replace % #"[^a-zA-Z0-9]+" "-")
         long-opt# (get opts# 
                        :long-opt 
                        (format "--%s-%s" 
                                (to-opt-name# (name (ns-name *ns*))) 
                                (to-opt-name# name#)))
         cell# (atom (get opts# :initial))
         option# {:long-opt long-opt#
                  ; short options arent supported
                  ;:short-opt (get opts# :short-opt)
                  :multiple? (get opts# :multiple? false)
                  :switch? (get opts# :switch? false)
                  :initial (get opts# :initial)
                  :increment (get opts# :increment 1)
                  :count? (get opts# :count? false)
                  :parser (get opts# :parser string-parser)
                  :help (get opts# :help)
                  :cell cell#
                  :name name#}]
     (swap! all-options conj option#)
     (def ~argname cell#)))

(defn print-usage
  "Print usage to STDERR"
  [& opts]
  (let [{:keys [error]} (apply hash-map opts)]
    (binding [*out* *err*]
      (when error
        (println "error:" error))
      (println)
      (println "Options:")
      (doseq [{:keys [short-opt long-opt initial help]} @all-options]
        (let [opt-msg (format "   %-35s" long-opt)
              default-msg (cond 
                         (nil? initial) nil
                         :else (format "[%s]" initial))
              msg (apply str (interpose \space (filter #(not (nil? %)) [opt-msg help default-msg])))]
          (println msg))))))

(defn parse-error 
  "Handle a parse error

  This prints the usage message with error argument and exits with return code 1"
  [msg]
  (print-usage :error msg)
  (System/exit 1))

(defn string-parser
  "Parser for strings"
  [opt ^String val]
  (if (nil? val) "" (.trim val)))

(defn int-parser
  "Parser for integer arguments"
  [{:keys [long-opt]} ^String s]
  (try
    (Integer. s)
    (catch NumberFormatException e 
      (let [msg (format "%s unexpected integer \"%s\"" long-opt s)]
        (parse-error msg)))))

(defn double-parser
  "Parser for double arguments"
  [{:keys [long-opt]} ^String s]
  (try
    (Double. s)
    (catch NumberFormatException e 
      (let [msg (format "%s unexpected double \"%s\"" long-opt s)]
        (parse-error msg)))))

(defn- opt-pattern
  [opt]
  (re-pattern (format "^%s(?:=(.+))?$" (Pattern/quote opt))))

(defn- handle-opt
  [opt opt-val]
  (let [{:keys [count? switch? multiple? long-opt cell parser increment]} opt] 
    (cond
      count? (swap! cell (fn [a] (cond (nil? a) increment
                                       :else (+ increment a))))
      switch? (reset! cell true)
      multiple? (swap! cell conj (parser opt opt-val))
      :else (reset! cell (parser opt opt-val)))))

(declare process-args0)

(defn- process-opt 
  [opt-arg args]
  (cond 
    (= opt-arg "--") args
    :else (let []
            (loop [opts @all-options]
              (cond 
                (empty? opts) (parse-error (format "unexpected option: %s" opt-arg))
                :else (let [[opt & topts] opts
                            {:keys [long-opt switch? count?]} opt
                            match (re-find (opt-pattern long-opt) opt-arg)
                            [_ opt-val] match
                            [harg & targs] args]
                        (cond 
                          (nil? match) (recur topts)
                          (or (not (nil? opt-val)) 
                              switch?
                              count?) (let []
                                         (handle-opt opt opt-val)
                                         (process-args0 args))
                          :else (let []
                                  (handle-opt opt (if (nil? harg) "" harg))
                                  (process-args0 targs)))))))))

(defn- process-args0
  [args]
  (let [[h & t] args]
    (cond
      (empty? args) []
      (nil? args) []
      (.startsWith h "-") (process-opt h t)
      :else (concat [h] (process-args0 t)))))

(defopt help 
        :help "Display this message" 
        :long-opt "--help"
        :switch? true)

(defn process-args 
  "Process arguments"
  [args]
  (let [ret (process-args0 args)]
    (when @help 
      (print-usage))
    ret))

