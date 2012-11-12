(ns dashdash
  (:require clojure.string)
  (:import java.util.regex.Pattern))

(def all-options (atom #{}))
(def args (atom []))
(def program-name (atom ""))

(defn *create-parser
  [tokens]
  {:tokens (atom tokens)
   :help? (atom false)
   :error (atom nil)
   :positional (atom [])
   :offset (atom 0)})

(defn *eof?
   [parser]
   (empty? @(:tokens parser)))

(defn *peek
  [parser]
  (first @(:tokens parser)))

(defn *advance!
  [parser]
  (let [{:keys [tokens offset]} parser]
    (when (not (empty? @tokens))
      (swap! tokens rest)
      (swap! offset inc))))

(defn *print-option
  [params]
  (let [{:keys [help long-option initial]} params
        msg (format "  %-35s" long-option)
        msg (if help
              (format "%s %s" msg help)
              msg)
        msg (if initial
              (format "%s [%s]" msg initial)
              msg)]
    (println msg)))

(defn *print-option-env
  [params]
  (let [{:keys [help environment-key initial]} params
        msg (format "  %-35s" environment-key)
        msg (if help
              (format "%s %s" msg help)
              msg)
        msg (if initial
              (format "%s [%s]" msg initial)
              msg)]
    (println msg)))

(defn print-usage
  "Print usage to STDERR"
  [& opts]
  (let [{:keys [error]} (apply hash-map opts)]
    (binding [*out* *err*]
      (println @program-name)
      (when error
        (println "error: " error))
      (println)
      (println "Options:")
      (*print-option {:long-option "--help" :help "Print this usage message"})
      (doseq [option @all-options]
        (*print-option option))
      (when @all-options
        (println)
        (println "Environment Variables:")
        (doseq [option @all-options]
          (*print-option-env option)))
      )))

(defn parse-string-option
  "Parser for strings"
  [option parser value]
  (let [{:keys [long-option cell]} option]
    (reset! cell value)))

(defn parse-integer-option
  "Parser for strings"
  [option parser value]
  (let [{:keys [long-option cell]} option
        value (try
               (Integer. #^String value)
               (catch Exception error nil))]
    (if value
      (reset! cell value)
      (reset! (:error parser) (format "unexpected integer for option \"%s\"" (:long-option option))))))

(defn parse-double-option
  "Parser for doubles"
  [option parser value]
  (let [{:keys [long-option cell]} option
        value (try
               (Double. #^String value)
               (catch Exception error nil))]
    (if value
      (reset! cell value)
      (reset! (:error parser) (format "unexpected double for option \"%s\"" (:long-option option))))))

(def option-types (atom {:string parse-string-option
                         :int parse-integer-option
                         :float parse-double-option
                         :double parse-double-option
                         :integer parse-integer-option}))

(defn parse-long-option
  "Parse a long option"
  [parser]
  (let [token (*peek parser)
        _ (*advance! parser)
        {:keys [error]} parser
        match (re-matches #"^(--[^=]+)(?:=(.+))?$" token)
        [_ opt-part val-part] match
        _ (assert match)
        _ (assert opt-part)
        option (first (filter #(= (:long-option %) opt-part) @all-options))
        unary? (get option :unary? false)
        val-part (if (and (empty? val-part) (not unary?))
                   (let [x (*peek parser)]
                     (*advance! parser)
                     x)
                   val-part)
        parse-function (:parser option)]
    (cond
      (not option)
      (reset! error (format "unexpected option \"%s\"" opt-part))
      (and (not unary?) (not val-part))
      (reset! error (format "expecting a value for option \"%s\"" opt-part))
      :otherwise
      (parse-function option parser val-part))))

(defn *process-env
  [option-parser]
  (let [env (System/getenv)]
    (doseq [option @all-options]
      (let [{:keys [parser environment-key cell parser]} option
            env-value (get (System/getenv) environment-key)]
        (when env-value
          (parser option option-parser env-value))))))

(defn *process-args

  "Process command line arguments"
  [parser]
  (let [{:keys [help? error positional offset tokens]} parser]
    (loop []
      (let [token (*peek parser)]
        (cond
          (*eof? parser) parser
          @help? parser
          @error parser
          (= "--" token) (do
                           (while (not (empty? @tokens))
                             (swap! positional conj (*peek parser))
                             (*advance! parser))
                           (recur))
          (contains? #{"-h" "--help"} token) (do
                                               (reset! help? true)
                                               (recur))
          (re-matches #"^--.+" token) (do
                                        (parse-long-option parser)
                                        (recur))
          (re-matches #"^-.+" token) (do
                                       (reset! error "short options arent supported")
                                       (recur))
          :otherwise (do
                       (swap! positional conj token)
                       (*advance! parser)
                       (recur)))))))

(defmacro def-property
  "Define an option"
  [property-name & opts]
  `(let [opts# (hash-map ~@opts)
         name# (.toLowerCase #^String (name (quote ~property-name)))
         long-option# (str "--"
                           (clojure.string/replace
                              (str *ns* "-" name#)
                             #"[^a-zA-Z0-9]+" "-"))
         long-option# (get opts# :long-option long-option#)
         environment-key# (.toUpperCase #^String (clojure.string/replace
                                                   (str *ns* "_" name#)
                                                   #"[^a-zA-Z0-9]+" "_"))
         environment-key# (get opts# :environment-key environment-key#)
         short-option# (get opts# :short-option)
         cell# (atom (get opts# :initial))
         type# (get opts# :type)
         option# {:long-option long-option#
                  ; short options arent supported
                  ;:short-option (get opts# :short-opt)
                  :multiple? (get opts# :multiple? false)
                  :switch? (get opts# :switch? false)
                  :initial (get opts# :initial)
                  :environment-key environment-key#
                  :increment (get opts# :increment 1)
                  :count? (get opts# :count? false)
                  :parser (get @dashdash/option-types (get opts# :type :string))
                  :help (get opts# :help (format "set %s/%s" *ns* (name (quote ~property-name))))
                  :cell cell#}]
     (swap! dashdash/all-options conj option#)
     (def ~property-name cell#)))

(defn run-args
  [argv & opts]
  (let [opts (apply hash-map opts)
        parser (*create-parser argv)
        {:keys [help? positional error]} parser]
    (when (:program-name opts)
      (reset! program-name (:program-name opts)))
    ; Process environment variables
    (*process-env parser)
    (*process-args parser)
    (if (or @help? @error)
      (do
        (print-usage :error @error)
        (System/exit 1))
      (reset! args @positional))))

(defmacro
  def-program
  [program-name-symbol arg-bind & body]
  `(defn ~(symbol "-main")
     [& args#]
     (let [program-name# (name (quote ~program-name-symbol))]
       (dashdash/run-args args# :program-name program-name#)
       (let [~arg-bind @dashdash/args]
         ~@body))))
