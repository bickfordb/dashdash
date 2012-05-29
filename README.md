dashdash
--------

dashdash is a Clojure option parsing library designed to make option declaration easy.  

Instead of declaring all of the programs options in one executable, options may be declared in the modules they affect.  Each "defopt" call defines an atom with the same symbol name in the module.  During option processing the atoms are filled in with the parsed options from the command line.  Options are automatically prefixed with the namespace name.  For example, an option definition like  "(defopt baz)" in namespace "foo.bar" will create a command line option "--foo-bar-baz" which will fill in the atom at "foo.bar/baz" 
### Usage

#### src/animals/elephant.clj

```clojure
(ns animals.elephant
  (:use dashdash))

; define an integer option
(defopt num :parser int-parser :initial 1 :help "The number of elephants")

; define a double option 
(defopt loudness :parser double-parser :initial -50 :help "The loudness in decibels")

; define a switch option (an option that takes no arguments) with a custom option name
(defopt eats-peanuts :switch? true :help "Do the elephants eat peanuts" :long-opt "--eats-peanuts")

; define an option that increments a count each time it is mentioned 
(defopt verbose :increment? true :initial 0 :help "increase the verbosity")

(defn thunder 
  []
  (when (> @verbose 1)
    (println @num "thundering elephants"))
  (when @eats-peanuts
    (println "eats peanuts")))
```

#### src/animals/run.clj

```clojure
(ns animals.run
  (:gen-class)
  (:require animals.elephants)
  (:use dashdash))

(defn rumpus 
  [program-args] 
  (animals.elephants/thunder)
  (println "the program args are:" program-args)

(defn -main 
  [& args]
  (rumpus (process-args args)))
```
  
#### Example invocations

```bash
java -jar $myjar animals.run --animals-elephants-verbose --animals-elephants-num=5 arg1 arg2 arg3 
# should print "5 thundering elephants"

java -jar $myjar animals.run --animals-elephants-num=5 arg1 arg2 arg3 
# should print nothing since @animals.elephants/verbose is 0
```

### License

Copyright (C) 2012 Brandon Bickford

This work is licensed under the Apache License Version 2.0.  Please refer to LICENSE

