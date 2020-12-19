(ns ^:no-doc zprint.main
  (:require ;[clojure.string :as str]
    [zprint.core :refer
     [zprint-str czprint zprint-file-str set-options! load-options!]]
    [zprint.config :refer
     [get-options get-explained-options config-and-validate-all
      select-op-options vec-str-to-str merge-deep try-to-load-string]])
  #?(:clj (:gen-class)))

;;
;; This is the root namespace to run zprint as an uberjar
;;
;; # How to use this:
;;
;; java -jar zprint-filter {<options-map-if-any>} <infile >outfile
;;

;!zprint {:vector {:wrap? false}}

(def main-help-str
  (vec-str-to-str
    [(:version (get-options))
     ""
     " zprint <options-map> <input-file >output-file"
     " zprint <switches> <input-file >output-file"
     " zprint -w input-and-output-file(s)"
     " zprint <options-map> -w input-and-output-file(s)"
     " zprint <switches> -w input-and-output-file(s)"
     ""
     " Where zprint is any of:"
     ""
     (str "  " (clojure.string/replace (:version (get-options)) "-" "m-"))
     (str "  " (clojure.string/replace (:version (get-options)) "-" "l-"))
     (str "  "
          "java -jar zprint-filter-"
          (second (clojure.string/split (:version (get-options)) #"-")))
     ""
     " <options-map> is a Clojure map containing zprint options. Must be first."
     "               Note that since it contains spaces, it must be"
     "               wrapped in quotes, for example:"
     "               '{:width 120}'"
     ""
     "               Use the -e switch to see the total options"
     "               map, which will show you what is configurable."
     ""
     " <switches> which do no formatting, only one allowed:"
     ""
     "  -h  --help         Output this help text."
     "  -v  --version      Output the version of zprint."
     "  -e  --explain      Output configuration, showing where"
     "                     non-default values (if any) came from."
     ""
     " <switches> which control configuration, only one allowed:"
     ""
     "  -d  --default      Accept no configuration input."
     "  -u  --url URL      Load options from URL."
     "      --url-only URL Load only options found from URL,"
     "                     ignore all .zprintrc, .zprint.edn files."
     ""
     " <switches> which process named files:  May follow a configuration switch"

     "                                        or an options map, but not both!"
     ""
     "  -w  --write FILE                read, format, and write to FILE (or FILEs),"
     "                                  -w *.clj is supported."
     "  -c  --check FILE                read and check format of FILE (or FILEs)"
     "                                  -c *.clj is supported."
     ""
     " Variations on -w, --write and -c, -check:"
     ""
     "  -lw  --list-write      FILE  like -w, but indicate which files processed."
     "  -fw  --formatted-write FILE  like -w, but indicate which files changed."
     "  -sw  --summary-write   FILE  like -w, but include a summary of the number"
     "                               of files processed and how many required a"
     "                               format change, as well as any errors."
     ""
     " Combinations are allowed, w/write and c/check must always be last,"
     " and order matters for -- switches.  Examples:"
     ""
     "   -lfw, -lfsw, -fsw, -flw, -sflw, etc." 
     "   --list-formatted-write, --list-formatted-summary-write, etc."
     ""
     " All combinations of -w and --write switches are also allowed "
     " with -c and --check switches:"
     ""
     "   -lfc, -lfsc, -fsc, -flc, -sflc, etc." 
     "   --list-formatted-check, --list-formatted-summary-check, etc."
     ""
     " The -w and -c switches are the only switches where you may also"
     " have an options map!"
     ""]))

(defn write-to-stderr
  "Take a string, and write it to stderr."
  [s]
  (let [^java.io.Writer w (clojure.java.io/writer *err*)]
    (.write w (str s "\n"))
    (.flush w)))

(defn format-file
  "Take a single argument, a filename string, and format it.  Read
  from the file, then rewrite the file with the formatted source.
  Return [exit-status required-format], where exit-status is
  either 0 if successful, or 1 if there were problems, and
  required-format is 0 if not, 1 if it did.  In the event of
  problems, don't change the contents of the file, but output text
  describing the problem to stderr."
  [filename check? list? formatted?]
  (let [[exit-status required-format in-str format-str format-stderr]
          (as-> [0 0 nil nil nil] running-status
            (let [[exit-status required-format in-str format-str format-stderr]
                    running-status]
              (when list? (write-to-stderr (str "Processing file " filename)))
              (try [0 0 (slurp filename) nil nil]
                   (catch Exception e
                     [1 0 nil nil
                      (str "Failed to open file " filename " because: " e)])))
            (let [[exit-status required-format in-str format-str format-stderr]
                    running-status]
              (if (= exit-status 1)
                ; We are done, move on
                running-status
                (try [0 0 in-str (zprint-file-str in-str filename) nil]
                     (catch Exception e
                       [1 0 nil nil
                        (str "Failed to format file: " filename
                             " because " e)]))))
            ;
            ; See comment in -main about graalVM issues with this code
            ;
            ; Write whatever is supposed to go to stdout
            (let [[exit-status required-format in-str format-str format-stderr]
                    running-status]
              (if (= exit-status 1)
                ; We are done, move on
                running-status
                ; Did we actually change anything?
                (if (not= in-str format-str)
                  ; Yes, write it out
                  (if check?
                    (do (when formatted?
                          (write-to-stderr (str "Formatting required in file "
                                                filename)))
                        [0 1 nil nil nil])
                    (try
                      (let [^java.io.Writer w (clojure.java.io/writer filename)]
                        (.write w (str format-str))
                        (.flush w)
                        (.close w)
                        (when formatted?
                          (write-to-stderr (str "Formatted file " filename)))
                        [0 1 nil nil nil])
                      (catch Exception e
                        [1 1 nil nil
                         (str "Failed to write output file: " filename
                              " because " e)])))
                  ; No, we didn't actually change anything, just move on
                  [0 0 nil nil nil])))
            ; Write whatever is supposed to go to stderr, if anything
            (let [[exit-status required-format in-str format-str format-stderr]
                    running-status]
              (when format-stderr (write-to-stderr format-stderr))
              ; We don't change the running-status because we wrote to stderr
              running-status))]
    [exit-status required-format]))

(defn elements-before-last-switch
  "Given the args from the command line, find the last arg that
  starts with -, and return [count-of-elements-before-last-switch
  pointer-to-last-switch-in-the-seq-of-args]."
  [args]
  (let [arg-rev (reverse args)
        len (count args)
        switch-count (reduce #(if (clojure.string/starts-with? %2 "-")
                                (reduced %1)
                                (inc %1))
                       0
                       arg-rev)
        count-before-last-switch (if (= len switch-count)
                                   ; No switch
                                   nil
                                   (- len (inc switch-count)))]
    (println "len:" len
             "switch-count:" switch-count
             "count-before-last-switch" count-before-last-switch)
    count-before-last-switch))

(defn pair-sum
  "Sum two vector pairs."
  [[a b] [c d]]
  [(+ a c) (+ b d)])

;;
;; # Main
;;

(defn -main
  "Read a file from stdin, format it, and write it to sdtout.  
  Process as fast as we can using :parallel?"
  [& args]
  ; Turn off multi-zprint locking since graalvm can't handle it, and
  ; we only do one zprint at a time here in the uberjar.
  (zprint.redef/remove-locking)
  (let [before-last-switch (elements-before-last-switch args)
        last-switch-in-seq (when before-last-switch
                             (nthnext args before-last-switch))
        possible-wc-switch (when before-last-switch
                             (clojure.string/trim (first last-switch-in-seq)))
        write-set #{"-w" "-lw" "-lfw" "-lsw" "-lfsw" "-fw" "-fsw" "-sw" "-flw"
                    "-flsw" "-fslw" "-sfw" "-slw" "-sflw" "-slfw" "--write"
                    "--list-write" "--list-formatted-write"
                    "--list-format-write" "--list-formatted-summary-write"
                    "--formatted-write" "--list-format-summary-write"
                    "--format-write" "--formatted-summary-write"
                    "--summary-write" "--format-summary-write"}
        check-set #{"-c" "-lc" "-lfc" "-lsc" "-lfsc" "-fc" "-fsc" "-sc" "-flc"
                    "-flsc" "-fslc" "-sfc" "-slc" "-sflc" "-slfc" "--check"
                    "--list-check" "--list-formatted-check"
                    "--list-format-check" "--list-formatted-summary-check"
                    "--formatted-check" "--list-format-summary-check"
                    "--format-check" "--formatted-summary-check"
                    "--format-summary-check" "--summary-check"}
        write? (write-set possible-wc-switch)
        check? (check-set possible-wc-switch)
        [list? formatted? summary?]
          (when (or write? check?)
            (if (clojure.string/starts-with? possible-wc-switch "--")
              [(clojure.string/includes? possible-wc-switch "list")
               (or (clojure.string/includes? possible-wc-switch "formatted")
                   (clojure.string/includes? possible-wc-switch "format"))
               (clojure.string/includes? possible-wc-switch "summary")]
              [(clojure.string/includes? possible-wc-switch "l")
               (clojure.string/includes? possible-wc-switch "f")
               (clojure.string/includes? possible-wc-switch "s")]))
        files (when (or write? check?) (next last-switch-in-seq))
	; Remove write or check switch and files, if any
        args (if (or write? check?) (take before-last-switch args) args)
        options (first args)
        _ (prn "args:" args)
        _ (println)
        _ (prn "before-last-switch:" before-last-switch
               "possible-wc-switch:" possible-wc-switch
               "write?" write?
               "check?" check?
               "list?" list?
               "formatted?" formatted?
               "summary?" summary?
               "files:" files)
        ; Some people wanted a zprint that didn't take configuration.
        ; If you say "--default" or "-d", that is what you get.
        ; --default or -d means that you get no configuration read from
        ; $HOME/.zprintrc or anywhere else.  You get the defaults.
        ;
        ; Basic support for "-s" or "--standard" is baked in, but
        ; not turned on.
        arg-count (count args)
        version? (or (= options "--version") (= options "-v"))
        help? (or (= options "--help") (= options "-h"))
        explain? (or (= options "--explain") (= options "-e"))
        default? (or (= options "--default") (= options "-d"))
        standard? (or (= options "--standard") (= options "-s"))
        url? #?(:clj (or (= options "--url") (= options "-u"))
                :default nil)
        url-only? #?(:clj (= options "--url-only")
                     :default nil)
        valid-switch?
          (or version? help? explain? default? standard? url? url-only?)
        arg-count-ok? (cond (or version? help? explain? default? standard?)
                              (= arg-count 1)
                            (or url? url-only?) (= arg-count 2)
                            :else (= arg-count 1))
        [option-status exit-status option-stderr op-options]
          ; [option-status exit-status option-stderr op-options]
          ; options-status :incomplete
          ;                :complete
          ; exit-status 0 or 1 (only interesting if option-stderr non-nil)
          ; option-stderr string to output to stderr
          ; op-options are options which don't affect formatting but do
          ; affect how things operate.
          (as-> [:incomplete 0 nil {}] running-status
            ; Is this an invalid switch?
            (if (and (not (clojure.string/blank? options))
                     (not valid-switch?)
                     (clojure.string/starts-with? options "-"))
              [:complete 1
               (str "Unrecognized switch: '" options "'" "\n" main-help-str) {}]
              running-status)
            ; Handle options with extraneous data
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                ; Do we have a options, and if so, is that all?
                (if (and options
                         (not (clojure.string/starts-with? options "-"))
                         (not arg-count-ok?))
                  ; No, we have something that could be options, and other
                  ; stuff after it, which is not allowed.  If we had -w
                  ; it would have been removed before this.
                  [:complete 1
                   (str "Error processing command line '"
                        (clojure.string/join " " args)
                        "', providing "
                        arg-count
                        " argument"
                        (if (= (dec arg-count) 1) "" "s")
                        " was incorrect!"
                        "\n"
                        main-help-str) {}]
                  running-status)))
            ; Handle switches with extraneous data
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                ; Does this switch have too much data
                (if (and valid-switch? (not arg-count-ok?))
                  [:complete 1
                   (str "Error processing switch '"
                        options
                        "', providing "
                        (dec arg-count)
                        " additional argument"
                        (if (= (dec arg-count) 1) "" "s")
                        " was incorrect!"
                        "\n"
                        main-help-str) {}]
                  running-status)))
            ; Handle switches
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if (or version? help?)
                  [:complete 0
                   (cond version? (:version (get-options))
                         help? main-help-str) op-options]
                  running-status)))
            ; If this is not a switch, get any operational options off
            ; of the command line
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (or (= option-status :complete)
                      valid-switch?
                      (empty? options))
                running-status
                (try
                  [:incomplete 0 nil (select-op-options (read-string options))]
                  (catch Exception e
                    [:complete 1
                     (str "Failed to use command line operational options: '"
                          options
                          "' because: "
                          e
                          ".") {}]))))
            ; Get all of the operational-options (op-options),
            ; merging in any that were on the command line
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              #_(println "pre config-and-validate-all"
                         "\noption-status:" option-status
                         "\nexit-status:" exit-status
                         "\noption-stderr:" option-stderr
                         "\nop-options:" op-options)
              (if (= option-status :complete)
                running-status
                (let [[new-map doc-map errors]
                        (config-and-validate-all nil nil op-options)
                      #_(println "post config-and-validate-all"
                                 "\nnew-map selections:" (select-op-options
                                                           new-map)
                                 "\ncolor:" (:color? (get-options))
                                 "\nerrors:" errors)
                      new-map (select-op-options (merge-deep new-map
                                                             op-options))]
                  #_(println "post merge"
                             "\new-map:" new-map
                             "\ncolor:" (:color? (get-options))
                             "\nerrors:" errors)
                  (if errors
                    [:complete 1 errors nil]
                    [:incomplete 0 nil (select-op-options new-map)]))))
            ; We now have the op-options, so process -e to explain what
            ; we have for a configuration from the various command files.
            ; This won't include any command-line options since we either
            ; do switches or command-line options.
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if explain?
                  ; Force set-options to configure using op-options
                  (do (set-options! {} "" op-options)
                      [:complete 0 (zprint-str (get-explained-options))
                       op-options])
                  running-status)))
            ; If --url try to load the args - along with other args
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if url?
                  #?(:clj (try (load-options! op-options (second args))
                               [:complete 0 nil op-options]
                               (catch Exception e
                                 [:complete 1
                                  (str "Unable to process --url switch value: '"
                                         (second args)
                                       "' because " e) op-options]))
                     :default running-status)
                  running-status)))
            ; If --url-only try to load the args - with no other options
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if url-only?
                  #?(:clj (try
                            (set-options! {:configured? true})
                            (load-options! op-options (second args))
                            [:complete 0 nil op-options]
                            (catch Exception e
                              [:complete 1
                               (str
                                 "Unable to process --url-only switch value: '"
                                   (second args)
                                 "' because " e) op-options]))
                     :default running-status)
                  running-status)))
            ; if --default or --standard just use what we have, nothing else
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if (or default? standard?)
                  (do (cond default? (set-options! {:configured? true})
                            standard? (set-options! {:configured? true,
                                                     #_:style,
                                                     #_:standard}))
                      [:complete 0 nil op-options])
                  [:incomplete 0 nil op-options])))
            ; Configure any command line options.  If we get here, that
            ; is all that is left to do -- all switches have been handled
            ; at this point and we would be complete by now.
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (or (= option-status :complete) (empty? options))
                running-status
                ; Accept fns from command-line options map
                (try (set-options! (try-to-load-string options)
                                   "command-line options"
                                   op-options)
                     [:complete 0 nil op-options]
                     (catch Exception e
                       [:complete 1
                        (str "Failed to use command line options: '"
                             options
                             "' because: "
                             e
                             ".") {}]))))
            ; We could nitialize using the op-options if we haven't done
            ; so already, but if we didn't have any command line options, then
            ; there are no op-options that matter.
          )]
    #_(prn "option-status" option-status
           "exit-status" exit-status
           "option-stderr" option-stderr
           "op-options" op-options)
    ; If option-stderr has something in it, we have either had some
    ; kind of a problem or we have processed the switch and it has
    ; output.  In either case, if option-stderr is non-nil we need
    ; to exit, and the exit status will be used.  Conversely, if
    ; option-stderr is nil, the exit status has no meaning.
    (if option-stderr
      (do (write-to-stderr option-stderr)
          ;;;;; NOTE FIX THIS AND THE END TOO!!!
          #_(shutdown-agents)
          #_(System/exit exit-status))
      ; Do whatever formatting we should do
      (if (or write? check?)
        ; We have one or more filenames to deal with
        (let [[total-exit-status total-required-format]
                (reduce #(pair-sum %1 (format-file %2 check? list? formatted?))
                  [0 0]
                  files)
              file-count (count files)]
          (prn "total-exit-status:" total-exit-status
               "total-required-format:" total-required-format)
          (when summary?
            (write-to-stderr
              (str
                "Processed "
                file-count
                " file"
                (if (> file-count 1) "s, " ", ")
                (if (pos? total-exit-status)
                  (str "with "
                       total-exit-status
                       " error"
                       (if (> total-exit-status 1) "s" "")
                       ", ")
                  "")
                (if (pos? total-required-format) total-required-format "none")
                " of which "
                (if check? "require" "required")
                " formatting.")))
          ;;;  NOTE FIX THIS ONE TOO
          #_(shutdown-agents)
          #_(System/exit (if write?
                           (if (> total-exit-status 0) 1 0)
                           ; For check? we return the number of files that
                           ; needed formatting, even though we didn't
                           ; actually format them, and the errors as well
                           (+ total-required-format total-exit-status))))
        ; We are operating on stdin and stdout
        (let [in-str (slurp *in*)
              [format-status stdout-str format-stderr]
                (try [0 (zprint-file-str in-str "<stdin>") nil]
                     (catch Exception e
                       [1 in-str (str "Failed to zprint: " e)]))]
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
          (when format-stderr (write-to-stderr format-stderr))
          ; Since we did :parallel? we need to shut down the pmap threadpool
          ; so the process will end!
          ;;; NOTE THERE ARE THREE EXITS TO BE FIXED
          #_(shutdown-agents)
          #_(System/exit format-status))))))
