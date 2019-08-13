(ns ^:no-doc zprint.main
  (:require ;[clojure.string :as str]
            [zprint.core :refer
             [zprint-str czprint zprint-file-str set-options!]])
  #?(:clj (:gen-class)))

;;
;; This is the root namespace to run zprint as an uberjar
;;
;; # How to use this:
;;
;; java -jar zprint-filter {<options-map-if-any>} <infile >outfile
;;


;;
;; # Main
;;

(defn -main
  "Read a file from stdin, format it, and write it to sdtout.  Do
  not load any additional libraries for configuration, and process
  as fast as we can using :parallel?"
  [& args]
  ; Turn off multi-zprint locking since graalvm can't handle it, and
  ; we only do one zprint at a time here in the uberjar.
  (zprint.redef/remove-locking)
  (let [options (first args)
	; Some people wanted a zprint that didn't take configuration.
	; If you say "-standard" or "-s", that is what you get.
	; -standard or -s means that you get no configuration read from
	; $HOME/.zprintrc or anywhere else.  You get the defaults 
	; (or whatever is set in the (if standard? ...) below)
        standard? (or (= options "-standard") (= options "-s"))
        _ (if standard?
            (set-options! {:configured? true,
                           :additional-libraries? false,
                           :parallel? true})
            (set-options! {:additional-libraries? false, :parallel? true}))
        [option-status option-stderr]
          (if (and (not standard?)
                   options
                   (not (clojure.string/blank? options)))
            (try [0 (set-options! (read-string options))]
                 (catch Exception e
                   [1
                    (str "Failed to use command line options: '"
                         options
                         "' because: "
                         (if (clojure.string/starts-with? options "-")
                           "it is not a recognized command line switch."
                           e)
                         ".")]))
            [0 nil])
        in-str (slurp *in*)
        ; _ (println "-main:" in-str)
        [format-status stdout-str format-stderr]
          (if (= option-status 0)
            (try [0 (zprint-file-str in-str "<stdin>") nil]
                 (catch Exception e [1 in-str (str "Failed to zprint: " e)]))
            [0 nil])
        exit-status (+ option-status format-status)
        stderr-str (cond (and option-stderr format-stderr)
                           (str option-stderr ", " format-stderr)
                         (not (or option-stderr format-stderr)) nil
                         :else (str option-stderr format-stderr))]
    ;
    ; We used to do this: (spit *out* fmt-str) and it worked fine
    ; in the uberjar, presumably because the JVM eats any errors on
    ; close when it is exiting.  But when using clj, spit will close
    ; stdout, and when clj closes stdout there is an error and it will
    ; bubble up to the top level.
    ;
    ; Now, we write and flush explicitly, sidestepping that particular
    ; problem. In part because there are plenty of folks that say that
    ; closing stdout is a bad idea.
    ;
    ; Getting this to work with graalvm was a pain.  In particular,
    ; w/out the (str ...) around fmt-str, graalvm would error out
    ; with an "unable to find a write function" error.  You could
    ; get around this by offering graalvm a reflectconfig file, but
    ; that is just one more file that someone needs to have to be
    ; able to make this work.  You could also get around this (probably)
    ; by type hinting fmt-str, though I didn't try that.
    ;
    ; Write whatever is supposed to go to stdout
    (let [^java.io.Writer w (clojure.java.io/writer *out*)]
      (.write w (str stdout-str))
      (.flush w))
    ; Write whatever is supposed to go to stderr, if any
    (when stderr-str
      (let [^java.io.Writer w (clojure.java.io/writer *err*)]
        (.write w (str stderr-str "\n"))
        (.flush w)))
    ; Since we did :parallel? we need to shut down the pmap threadpool
    ; so the process will end!
    (shutdown-agents)
    (System/exit exit-status)))
