(ns
  zprint.core
  ;#?@(:cljs [[:require-macros [zprint.macros :refer [dbg dbg-pr dbg-form
  ;dbg-print]]]])
  (:require
   #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])
   clojure.string
   #?@(:cljs [[cljs.reader :refer [read-string]]])
   #?@(:clj [[clojure.repl :refer [source-fn]]])
   [zprint.zprint :as zprint :refer
    [fzprint blanks line-count max-width line-widths expand-tabs merge-deep]]
   [zprint.finish :refer
    [cvec-to-style-vec compress-style no-style-map color-comp-vec cursor-style]]
   [zprint.config :as config :refer
    [config-set-options! get-options config-configure-all! help-str
     get-explained-options get-explained-all-options get-default-options
     validate-options apply-style]]
   [zprint.zutil :refer [zmap-all zcomment? edn* whitespace? string]]
   [zprint.sutil]
   [rewrite-clj.parser :as p]
   #_[clojure.spec :as spec :refer [get-spec describe]]))

;;
;; zprint
;;
;; A complete pretty printing package for Clojure.
;;
;; Prints both structures and code at the repl, and code in files.
;; Highly configurable, doesn't lose comments.  Completely ignores
;; any incoming whitespace and newlines -- produces its own idea of
;; the best output possible for a given output width.
;;
;; There are a number of namespaces:
;;
;; core     user visible API
;; config   configuration processing and storage
;; zprint   actual pretty printing logic, relies on zutil or sutil
;; zutil    zipper manipulation routines
;; sutil    sexpression manipulation routines
;; focus    add focus to output of zprint
;; finish   process result of zprint into desired type
;; ansi     do coloring for ansi terminal output
;; repl     contains a bunch test cases for zprint development
;; 
;; Basic code flow:
;;
;; The user visible API in zprint.core determines whether the thing
;; to be pretty printed is an sexpression which should be pretty
;; printed directly, or a string which should be parsed into a
;; zipper and then printed (based on :parse-string?).
;; It also handles some exceptional calls directly (e.g.,
;; (czprint nil :describe)), but generally calls
;; zprint.zprint/fzprint* to do the pretty printing.  The options
;; map has been properly configured to use the routines for
;; sexpressions (in sutil) or for zippers (in zutil).
;;
;; zprint.zprint/fzprint* is the routine that handles pretty
;; printing anything -- it dispatches to a variety of fzprint-
;; routines, each one handling a different type of structure.
;; Each of the fzprint- routines takes an option map, which contains
;; not only the configured options and proper utility routines
;; (configured by zprint.core), but also additional information useful
;; during the run-time processing of the structure.
;;
;; zprint/fzprint* returns a str-style-vec, which is a structure
;; like this:
;;
;; [[<string> <color> <type>][<string> <color> <type>] ...]
;;
;; The strings are the actual things to be output, the color is the
;; color in which to output them (if using color), and the type is
;; the type of the information in the string, which is one of:
;;
;; :whitespace   blanks and newlines
;; :element      actual strings containing output
;; :left, :right signals left or right end of a collection
;;
;; This information is processed into useable output by the
;; zprint.core functions by calling functions in zprint.finish and
;; zprint.ansi.
;;
;; zprint.focus is used when calling the zprint.core functions with
;; a zipper, and will assist the user in creating output which shows
;; a focus on some internal structure inside a containing structure.
;; Presently, the API for this is not documented.
;;
;;

;;
;; Clean up the API a bit by putting all of the public functions
;; in zprint.core
;;

(defn set-options!
  "Add some options to the current options, checking to make
  sure that they are correct."
  ([new-options doc-str] (do (config-set-options! new-options doc-str) nil))
  ([new-options] (do (config-set-options! new-options) nil)))

(defn configure-all!
  "Do external configuration if it has not already been done, 
  replacing any internal configuration.  Returns nil if successful, 
  a vector of errors if not."
  []
  (config-configure-all!))

;;
;; # Zipper determination and handling
;;

(defn rewrite-clj-zipper?
  "Is this a rewrite-clj zipper node? A surprisingly hard thing to 
  determine, actually."
  [z]
  (when (and (coll? z)
             (let [type-str (pr-str (type (first z)))]
               (and (> (count type-str) 16)
                    (= "rewrite_clj.node" (subs type-str 0 16)))))
    ;  (= "rewrite_clj.node" (subs (pr-str (type (first z))) 0 16)))
    z))

(defn zipper?
  "Is this a zipper?"
  [z]
  (when (coll? z) (or (rewrite-clj-zipper? z) (:tag (first z)))))

(defn get-zipper
  "If it is a zipper or a string, return a zipper, else return nil."
  [options x]
  (if (string? x)
    (let [x (if (:expand? (:tab options))
              (expand-tabs (:size (:tab options)) x)
              x)
          n (p/parse-string x)]
      (when n (edn* n)))
    (when (zipper? x) x)))

;;
;; # Interface into zprint.zprint namespace
;;

(defn fzprint-style
  "Do a basic zprint and output the style vector and the options used for
  further processing: [<style-vec> options]"
  [coll options]
  (let [input
          (cond (:zipper? options) (if (zipper? coll)
                                     coll
                                     (throw
                                       (#?(:clj Exception.
                                           :cljs js/Error.)
                                        (str "Collection is not a zipper"
                                             " yet :zipper? specified!"))))
                (:parse-string? options)
                  (if (string? coll)
                    (get-zipper options coll)
                    (throw
                      (#?(:clj Exception.
                          :cljs js/Error.)
                       (str "Collection is not a string yet"
                            " :parse-string? specified!")))))
        z-type (if input :zipper :sexpr)
        input (or input coll)]
    (if (nil? input)
      [[["" :b]] options]
      (let [options
              (assoc options
                :ztype z-type
                :zf (if (= z-type :zipper) zprint.zutil/zf zprint.sutil/sf))]
        #_(def coreopt options)
        [(fzprint options 0 input) options]))))

(declare get-docstring-spec)

;;
;; # Fundemental interface for fzprint-style, does configuration
;;

(defn zprint*
  "Basic setup for fzprint call, used by all top level fns. Third
  argument can be either a number or a map, and if the third is a
  number, the fourth (if any) must be a map.  If there is a fifth
  argument, it is the focus (which must be a zipper), but for now
  that isn't implemented.  The internal-options is either an empty
  map or {:parse-string? true} for the -fn functions, and cannot
  be overridden by an options argument. Returns a vector with
  the style-vec and the options used: [<style-vec> options]"
  [coll internal-options & [width-or-options options]]
  (if (= width-or-options :default)
    (fzprint-style coll (get-default-options))
    (let [[width-or-options special-option]
            (if (#{:explain :support :explain-justified :help} width-or-options)
              [nil width-or-options]
              [width-or-options nil])
          configure-errors (when-not (:configured? (get-options))
                             (configure-all!))
          width (when (number? width-or-options) width-or-options)
          options (cond (and width (map? options)) options
                        (map? width-or-options) width-or-options)
          width-map (if width {:width width} {})
          new-options (merge-deep options width-map internal-options)
          auto-width
            (when (and (not width)
                       (:auto-width? new-options (:auto-width? (get-options))))
              (let [actual-width #?(:clj (#'table.width/detect-terminal-width)
                                    :cljs nil)]
                (when (number? actual-width) {:width actual-width})))
          new-options
            (if auto-width (merge-deep new-options auto-width) new-options)
          #_(def nopt new-options)
          ; Do what config-and-validate does, minus the doc-map
          errors (validate-options new-options)
          [updated-map _ style-errors]
            (apply-style nil nil (get-options) new-options)
          errors (if style-errors (str errors " " style-errors) errors)
          combined-errors
            (str (when configure-errors
                   (str "Global configuration errors: " configure-errors))
                 (when errors (str "Option errors in this call: " errors)))
          actual-options (if (not (empty? combined-errors))
                           (throw (#?(:clj Exception.
                                      :cljs js/Error.)
                                   combined-errors))
                           (config/add-calculated-options
                             (merge-deep updated-map new-options)))]
      (if special-option
        (case special-option
          :explain (fzprint-style (get-explained-options) (get-default-options))
          :explain-justified (fzprint-style (get-explained-options)
                                            (merge-deep (get-default-options)
                                                        {:map {:justify?
                                                                 true}}))
          :support (fzprint-style (get-explained-all-options)
                                  (get-default-options))
          :help (println help-str)
          (println (str "Unknown keyword option: " special-option)))
        (fzprint-style
          coll
          (if-let [fn-name (:fn-name actual-options)]
            (if (:docstring? (:spec actual-options))
              #?(:clj (assoc-in actual-options
                                [:spec :value]
                                (get-docstring-spec actual-options fn-name))
                 :cljs actual-options)
              actual-options)
            actual-options))))))

;;
;; # API Support
;;

(defn zprint-str-internal
  "Take a zipper or string and pretty print it with fzprint, 
  output a str."
  [coll internal-options & rest]
  (apply str (map first (first (apply zprint* internal-options coll rest)))))

(defn czprint-str-internal
  "Take a zipper or string and pretty print with color with fzprint, 
  output a str."
  [coll internal-options & rest]
  (let [[cvec options] (apply zprint* internal-options coll rest)
        #_(def cv cvec)
        str-style-vec (cvec-to-style-vec {:style-map no-style-map} cvec)
        #_(def ssv str-style-vec)
        comp-style (compress-style str-style-vec)
        #_(def cps comp-style)
        color-style (color-comp-vec comp-style)]
    color-style))

(defn get-fn-source
  "Call source-fn, and if it isn't there throw an exception."
  [fn-name]
  (or
    (try #?(:clj (source-fn fn-name))
         (catch #?(:clj Exception
                   :cljs :default)
                e
                nil))
    (throw (#?(:clj Exception.
               :cljs js/Error.)
            (str "No definition found for a function named: " fn-name)))))

;;
;; # User level printing functions
;;
;; (*zprint <to-print> <width> <options-map>)
;;
;; zprint       pretty print to *out*
;; czprint      pretty print to *out* with ansi colors
;;
;; zprint-str   pretty print to string
;; czprint-str  pretty print to string with ansi colors
;;
;; options:
;;
;;   See config.clj
;;

(defn zprint-str
  "Take a zipper or string and pretty print it with fzprint, 
  output a str."
  [coll & rest]
  (apply zprint-str-internal {} coll rest))

(defn czprint-str
  "Take a zipper or string and pretty print it with fzprint, 
  output a str that has ansi color in it."
  [coll & rest]
  (apply czprint-str-internal {} coll rest))

(defn zprint
  "Take a zipper or string and pretty print it."
  [coll & rest]
  (println (apply zprint-str-internal {} coll rest)))

(defn czprint
  "Take a zipper or string and pretty print it."
  [coll & rest]
  (println (apply czprint-str-internal {} coll rest)))

#?(:clj (defmacro zprint-fn-str
          "Take a fn name, and print it."
          [fn-name & rest]
          `(apply zprint-str-internal
             {:parse-string? true}
             (get-fn-source '~fn-name)
             ~@rest
             [])))

#?(:clj (defmacro czprint-fn-str
          "Take a fn name, and print it with syntax highlighting."
          [fn-name & rest]
          `(apply czprint-str-internal
             {:parse-string? true}
             (get-fn-source '~fn-name)
             ~@rest
             [])))

#?(:clj (defmacro zprint-fn
          "Take a fn name, and print it."
          [fn-name & rest]
          `(println
             (apply zprint-str-internal
               {:parse-string? true}
               (get-fn-source '~fn-name)
               ~@rest
               []))))

#?(:clj (defmacro czprint-fn
          "Take a fn name, and print it with syntax highlighting."
          [fn-name & rest]
          `(println
             (apply czprint-str-internal
               {:parse-string? true, :fn-name '~fn-name}
               (get-fn-source '~fn-name)
               ~@rest
               []))))

;;
;; # File operations
;;

;;
;; ## Parse a comment to see if it has an options map in it
;;

(defn get-options-from-comment
  "s is string containing a comment.  See if it starts out ;!zprint, 
  and if it does, attempt to parse it as an options map.  
  Return [options error-str] with only one of the two populated 
  if it started with ;!zprint, and nil otherwise."
  [zprint-num s]
  (let [comment-split (clojure.string/split s #"^;!zprint ")]
    (when-let [possible-options (second comment-split)]
      (try
        [(read-string possible-options) nil]
        (catch
          #?(:clj Exception
             :cljs :default)
          e
          [nil
           (str "Unable to create zprint options map from: '" possible-options
                "' found in !zprint directive number: " zprint-num
                " because: " e)])))))

;;
;; ## Process the sequences of forms in a file
;;
;!zprint {:format :next :vector {:wrap-after-multi? false}}

(defn process-form
  "Take one form from a file and process it.  The primary goal is
  of course to produce a string to put into the output file.  In
  addition, see if that string starts with ;!zprint and if it does,
  pass along that information back to the caller.  The input is a 
  [[next-options <previous-string>] form], where next-options accumulates
  the information to be applied to the next non-comment/non-whitespace
  element in the file.  The output is [next-options output-str zprint-num], 
  since reductions is used to call this function.  See process file-forms
  for what is actually done with the various :format values."
  [file-name [next-options _ zprint-num] form]
  (let [comment? (zcomment? form)
        whitespace? (whitespace? form)
        [new-options error-str] (when comment?
                                  (get-options-from-comment (inc zprint-num)
                                                            (string form)))
        ; If this was a ;!zprint line, don't wrap it
        internal-options (if new-options
                           {:comment {:wrap? false}, :zipper? true}
                           {:zipper? true})
        output-str
          ; Should we zprint this form?
          (if (or (= :off (:format (get-options)))
                  (and (not (or comment? whitespace?))
                       (= :skip (:format next-options))))
            (string form)
            (zprint-str-internal
              (if (or comment? whitespace? (empty? next-options))
                internal-options
                (do #_(def io internal-options)
                    #_(def no next-options)
                    (merge-deep internal-options next-options)))
              form))
        local? (or (= :skip (:format new-options))
                   (= :next (:format new-options)))]
    (when (and new-options (not local?))
      (set-options! new-options
                    (str ";!zprint number " (inc zprint-num)
                         " in file: " file-name)))
    (when error-str (println "Warning: " error-str))
    [(cond local? (merge-deep next-options new-options)
           (or comment? whitespace?) next-options
           :else {})
     output-str (if new-options (inc zprint-num) zprint-num)]))

;;
;; # File comment API
;;
;; In order to properly process a file, sometimes you want to alter
;; the value of the zprint options map for a single function definition,
;; or turn it off completely and the on again later.  Or, possibly,
;; set some defaults which hold while formatting only this file.
;;
;; This is all possible because of the zprint comment API.
;;
;; If a comment starts with the string ";!zprint ", then the rest
;; of the string will be parsed as a zprint options map.
;;
;; For example:
;;
;;   ;!zprint {:vector {:wrap? false}}
;;
;; will turn off vector wrapping in the file and it will stay that way
;; until the end of the file (or another ;!zprint comment alters it).
;;
;; The API:
;;
;; ;!zprint <options>   perform a (set-options! <options>) which will
;;                      be used until altered or the end of the file is
;;                      reached
;;
;; ;!zprint {:format :off} Do not format successive forms with zprint to
;;                         the end of the file
;;
;; ;!zprint {:format :on}  Format successive forms with zprint (default)
;;
;; ;!zprint {:format :skip} Do not format the next non-comment/non-whitespace
;;                          element with zprint.
;;
;; ;!zprint {:format :next <other-options>} Format the next non-comment
;;                                          non-whitespace element with the
;;                                          specified <other-options>
;;

(defn process-file-forms
  "Take a sequence of forms (which are zippers of the elements of
  a file somewhere), and not only format them for output but
  also handle comments containing ;!zprint that affect the options
  map throughout the processing."
  [file-name form-seq]
  (apply str
    (map second
      (reductions (partial process-form file-name) [{} "" 0] form-seq))))

;;
;; ## Process an entire file
;;

#?(:clj
     (defn zprint-file
       "Take an input filename and an output filename, and do a zprint
  on every form in the input file and write it to the output file.
  Note that this uses whatever comes from (get-options).  If you
  want to have each file be separate, you should call (configure-all!)
  before calling this function."
       [infile file-name outfile]
       (let [wholefile (slurp infile)
             lines (clojure.string/split wholefile #"\n")
             lines (if (:expand? (:tab (get-options)))
                     (map (partial expand-tabs (:size (:tab (get-options))))
                       lines)
                     lines)
             filestring (clojure.string/join "\n" lines)
             ; If file ended with a \newline, make sure it still does
             filestring (if (= (last wholefile) \newline)
                          (str filestring "\n")
                          filestring)
             forms (edn* (p/parse-string-all filestring))
             form-seq (zmap-all identity forms)
             #_(def fileform form-seq)
             outputstr (process-file-forms file-name form-seq)]
         (spit outfile outputstr))))

;;
;; # Process specs to go into a doc-string
;;

(defn format-spec
  "Take a spec and a key, and format the output as a string. Width is
  because the width isn't really (:width options)."
  [options describe-fn fn-spec indent key]
  (when-let [key-spec (get fn-spec key)]
    (let [key-str (str (name key) ": ")
          total-indent (+ (count key-str) indent)
          ; leave room for double-quote at the end
          width (dec (- (:width options) total-indent))
          key-spec-data (describe-fn key-spec)
          spec-str (zprint-str key-spec-data width)
          spec-no-nl (clojure.string/split spec-str #"\n")
          spec-shift-right
            (apply str (interpose (str "\n" (blanks total-indent)) spec-no-nl))]
      (str (blanks indent) key-str spec-shift-right))))

#?(:clj
     (defn get-docstring-spec
       "Given a function name (which, if used directly, needs to be quoted)
  return a string which is contains the spec information that could go
  in the doc string."
       [{:keys [width rightcnt], {:keys [indent]} :list, :as options} fn-name]
       (let [{n :ns, nm :name, :as m} (meta (resolve fn-name))
             get-spec-fn (resolve 'clojure.spec/get-spec)
             describe-fn (resolve 'clojure.spec/describe)]
         (when (and get-spec-fn describe-fn)
           (when-let [fn-spec (get-spec-fn (symbol (str (ns-name n))
                                                   (name nm)))]
             (apply str
               "\n\n" (blanks indent)
               "Spec:\n" (interpose "\n"
                           (remove nil?
                             (map (partial format-spec
                                           options
                                           describe-fn
                                           fn-spec
                                           (+ indent indent))
                               [:args :ret :fn])))))))))