(ns
  zprint.zprint
  #?@(:cljs [[:require-macros
              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])
  (:require
   #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])
   [clojure.string :as s]
   [zprint.ansi :refer [color-str]]
   [zprint.zutil :refer [add-spec-to-docstring]]))

;;
;; # Utility Functions
;;

(defn blanks
  "Produce a blank string of desired size."
  [n]
  (apply str (repeat n " ")))

(defn dots
  "Produce a dot string of desired size."
  [n]
  (apply str (repeat n ".")))

(defn indent "error" [])

;;
;; # Utility functions for manipulating option maps
;;

(defn merge-with-fn
  "Take two arguments of things to merge and figure it out."
  [val-in-result val-in-latter]
  (if (and (map? val-in-result) (map? val-in-latter))
    (merge-with merge-with-fn val-in-result val-in-latter)
    val-in-latter))

(defn merge-deep
  "Do a merge of maps all the way down."
  [& maps]
  (apply merge-with merge-with-fn maps))

;;
;; # Debugging Assistance
;;

(def fzprint-dbg (atom nil))

(defn log-lines
  "Accept a style-vec that we are about to hand to style-lines, and
  output it if called for, to aid in debugging."
  [{:keys [dbg-print? dbg-indent in-hang?], :as options} dbg-output ind
   style-vec]
  (when dbg-print?
    (if style-vec
      (do (println dbg-indent dbg-output "--------------" "in-hang?" in-hang?)
          (println (apply str (blanks ind) (map first style-vec))))
      (println dbg-indent dbg-output "--------------- no style-vec"))))

;;
;; # What is a function?
;;

(defn showfn?
  "Show this thing as a function?"
  [fn-map f]
  (when (not (string? f))
    (let [f-str (str f)]
      (or
        (fn-map f-str)
        (re-find #"clojure" f-str)
        (if (symbol? f)
          ; This is necessary because f can be a symbol that
          ; resolve will have a problem with.  The obvious ones
          ; were (ns-name <some-namespace>), but there are almost
          ; certainly others.
          (try
            (or (re-find #"clojure"
                         (str (:ns
                               (meta #?(:clj (resolve f)
                                        :cljs f)))))
                (fn-map (name f)))
            (catch #?(:clj Exception
                      :cljs :default)
                   e
                   nil)))))))

(defn show-user-fn?
  "Show this thing as a user defined function?  Assumes that we
  have already handled any clojure defined functions!"
  [options f]
  (when (not (string? f))
    (let [f-str (str f)
          user-fn-map (:user-fn-map options)]
      (or
        (get user-fn-map f-str)
        (if (symbol? f)
          ; This is necessary because f can be a symbol that
          ; resolve will have a problem with.  The obvious ones
          ; were (ns-name <some-namespace>), but there are almost
          ; certainly others.
          (try
            (or (not (empty? (str (:ns
                                   (meta #?(:clj (resolve f)
                                            :cljs f))))))
                (get user-fn-map (name f)))
            (catch #?(:clj Exception
                      :cljs :default)
                   e
                   nil)))))))

(def right-separator-map {")" 1, "]" 1, "}" 1})

;;
;; # Functions to compare alternative printing approaches
;;

(declare fix-rightcnt)
(declare contains-nil?)

(defn good-enough?
  "Given the fn-style, is the first output good enough to be worth
  doing. p is pretty, which is typically hanging, and b is basic, which
  is typically flow. p-count is the number of elements in the hang."
  [caller
   {:keys [width rightcnt dbg?],
    {:keys [hang-flow hang-type-flow hang-flow-limit general-hang-adjust
            hang-if-equal-flow?]}
      :tuning,
    {:keys [hang-expand hang-diff hang-size hang-adjust]} caller,
    :as options} fn-style p-count indent-diff
   [p-lines p-maxwidth p-length-seq p-what] [b-lines b-maxwidth _ b-what]]
  (let [p-last-maxwidth (last p-length-seq)
        hang-diff (or hang-diff 0)
        hang-expand (or hang-expand 1000.)
        hang-adjust (or hang-adjust general-hang-adjust)
        #_(options
            (if (and p-lines
                     p-count
                     (pos? p-count)
                     (not (<= indent-diff hang-diff))
                     (not (<= (/ (dec p-lines) p-count) hang-expand)))
              (assoc options :dbg? true)
              options))
        options (if (or p-what b-what) (assoc options :dbg? true) options)
        result
          (if (not b-lines)
            true
            (and
              p-lines
              ; Does the last line fit, including the collection ending stuff?
              ; Do we really need this anymore?
              (<= p-last-maxwidth (- width (fix-rightcnt rightcnt)))
              ; Does it widest line fit?
              ; Do we have a problem if the widest line has a rightcnt?
              (<= p-maxwidth width)
              ;      (<= p-maxwidth (- width (fix-rightcnt rightcnt)))
              (or
                (zero? p-lines)
                (and
                  ; do we have lines to operate on?
                  (> b-lines 0)
                  (> p-count 0)
                  ; if the hang and the flow are the same size, why not hang?
                  (if (and (= p-lines b-lines) hang-if-equal-flow?)
                    true
                    ; is the difference between the indents so small that
                    ; we don't care?
                    (and
                      (if (<= indent-diff hang-diff)
                        true
                        ; Do the number of lines in the hang exceed the number
                        ; of elements in the hang?
                        (<= (/ (dec p-lines) p-count) hang-expand))
                      (if hang-size (< p-lines hang-size) true)
                      (let [factor
                              (if (= fn-style :hang) hang-type-flow hang-flow)]
                        ; if we have more than n lines, take the shortest
                        (if (> p-lines hang-flow-limit)
                          (<= (dec p-lines) b-lines)
                          ; if we have less then n lines, we don't necessarily
                          ; take the shortest
                          ; once we did (dec p-lines) here, fwiw
                          ; then we tried it w/out the dec, now we let you
                          ; set it in :tuning.  The whole point of having a
                          ; hang-adjust of -1 is to allow hangs when the
                          ; number of lines in a hang is the same as the
                          ; number of lines in a flow.
                          ;(< (/ p-lines b-lines) factor)))))))]
                          (< (/ (+ p-lines hang-adjust) b-lines)
                             factor)))))))))]
    (dbg options
         (if result "++++++" "XXXXXX")
         "p-what" p-what
         "good-enough? caller:" caller
         "fn-style:" fn-style
         "width:" width
         "rightcnt:" rightcnt
         "hang-expand:" hang-expand
         "p-count:" p-count
         "p-lines:" p-lines
         "p-maxwidth:" p-maxwidth
         "indent-diff:" indent-diff
         "hang-diff:" hang-diff
         "p-last-maxwidth:" p-last-maxwidth
         "b-lines:" b-lines
         "b-maxwidth:" b-maxwidth)
    result))

;;
;; # Utility Functions
;;

(defn in-hang
  "Add :in-hang? true to the options map."
  [options]
  (if (:in-hang? options)
    options
    (if (:do-in-hang? options) (assoc options :in-hang? true) options)))

(defn contains-nil?
  "Scan a collection, and return the number of nils present (if any),
  and nil otherwise."
  [coll]
  (let [n (count (filter #(if (coll? %) (empty? %) (nil? %)) coll))]
    (when (not (zero? n)) n)))

(defn concat-no-nil
  "Concatentate multiple sequences, but if any of them are nil, return nil."
  [& rest]
  (let [result (reduce (fn [v o]
                         (if (coll? o)
                           (if (empty? o) (reduced nil) (reduce conj! v o))
                           (if (nil? o) (reduced nil) (conj! v o))))
                 (transient [])
                 rest)]
    (when result (persistent! result))))

(defn force-vector
  "Ensure that whatever we have is a vector."
  [coll]
  (if (vector? coll) coll (into [] coll)))

(defn keyword-fn?
  "Takes a string, and returns the fn-style if it is a keyword and
  without the : it can be found in the fn-map."
  [options s]
  (let [[left right] (clojure.string/split s #"^:")]
    (when right ((:fn-map options) right))))

;;
;; # Work with style-vecs and analyze results
;;

(defn accumulate-ll
  "Take the vector carrying the intermediate results, and
  do the right thing with a new string. Vector is
  [ 0 out - vector accumulating line lengths
    1 cur-len - length of current line
    just-eol? - did we just do an eol?
    ]
  s - string to add to current line
  tag - element type of string (comment's don't count in length)
  eol? - should we terminate line after adding count of s"
  [count-comment? [out cur-len just-eol? just-comment? :as in] s tag eol?]
  (let [comment? (= tag :comment)
        count-s (if (and comment? (not count-comment?)) 0 (count s))]
    (cond
      #_((and comment? (not just-eol?))
          ; if a comment and we didn't just do
          ; a newline, then terminate the previous
          ; line and do a line just with the comment
          (assoc in 0 (conj out cur-len count-s) 1 0 2 true))
      ; if we are told to terminate the line or it
      ; is a comment, we terminate the line with the
      ; size of the string added to it
      (or (and eol? (not (and just-eol? (zero? count-s)))) comment?)
        (assoc in 0 (conj out (+ cur-len count-s)) 1 0 2 true 3 comment?)
      ; no reason to terminate the line, just accumulate
      ; the size in cur-len
      :else (assoc in 1 (+ cur-len count-s) 2 nil 3 comment?))))
        
(defn generate-ll
  [count-comment? [out cur-len just-eol? just-comment? :as in]
   [s _ tag :as element]]
  (let [[l r] (if (= tag :whitespace) (clojure.string/split s #"\n" 2) (list s))
        ; if tag = :comment, shouldn't have \n and
        ; therefore shouldn't have r
        ; if r is non-nil, then we had a newline, so we want to
        ; terminate the current line
        ; if we are already in a comment and we have something
        ; that is not whitespace, then we want to terminate the
        ; current line
        in (accumulate-ll count-comment? in l tag (not (nil? r)))
        in (if (empty? r) in (accumulate-ll count-comment? in r tag nil))]
    in))

(defn line-lengths
  "Take a style-vec, and output a sequence of numbers, one for each
  line, which contains the actual length. Must take the current
  indent to have a prayer of getting this right, but it is used
  only for the first line.  The ind can be an integer or a seq of
  integers, in which case only the first integer is used. Newlines
  can come anywhere in an element in a style-vec, it will account
  for both sides.  Will break lines on comments even if no newlines
  in them.  This doesn't count the length of comment lines unless
  [:comment :count?] is true, so that we don't format based on
  comment size -- that is handled with the wrap-comments at the
  end."
  [options ind style-vec]
  (let [length-vec (first
                     ; this final accumulate-ll is to terminate the last line,
                     ; the one in progress
                     (let [count-comment? (:count? (:comment options))
                           [_ _ just-eol? just-comment? :as result]
                             (reduce (partial generate-ll count-comment?)
                               [[] (if (coll? ind) (first ind) ind) nil nil]
                               style-vec)]
                       (if (and just-eol? (not just-comment?))
                         result
                         (accumulate-ll count-comment?
                                        (assoc result 2 nil)
                                        ""
                                        nil
                                        true))))]
    length-vec))

(defn find-what
  "Given a style-vec, come up with a string that gives some hint of 
  where this style-vec came from."
  [style-vec]
  (loop [s-vec style-vec]
    (when s-vec
      (let [[what _ this] (first s-vec)]
        (if (= this :element) what (recur (next s-vec)))))))

(defn style-lines
  "Take a style output, and tell us how many lines it takes to print it
  and the maximum width that it reaches. Returns 
  [<line-count> <max-width> [line-lengths]].
  Doesn't require any max-width inside the style-vec. Also returns the
  lines lengths in case that is helpful (since we have them anyway).
  If (:dbg-ge options) has value, then uses find-what to see if what it
  finds matches the value, and if it does, place the value in the
  resulting vector."
  [options ind style-vec]
  (when (and style-vec (not (empty? style-vec)) (not (contains-nil? style-vec)))
    (let [lengths (line-lengths options ind style-vec)
          result [(count lengths) (apply max lengths) lengths]
          dbg-ge (:dbg-ge options)
          what (when (and dbg-ge (= (find-what style-vec) dbg-ge)) dbg-ge)]
      (if what (conj result what) result))))

(defn fzfit
  "Given output from style-lines and options, see if it fits the width.  
  Return the number of lines it takes if it fits, nil otherwise."
  [{:keys [width rightcnt dbg?], :as options}
   [line-count max-width :as style-lines-return]]
  (dbg options
       "fzfit: fixed-rightcnt:" (fix-rightcnt rightcnt)
       "line-count:" line-count
       "max-width:" max-width
       "width:" width)
  (when style-lines-return
    (if (<= max-width (- width (fix-rightcnt rightcnt))) line-count nil)))

(defn fzfit-one-line
  "Given a style-vec and options, return true if it fits on a single
  line."
  [options style-lines-return]
  (let [lines (fzfit options style-lines-return)]
    (and (number? lines) (= lines 1))))
        
;;
;; # Handle Rightmost Size
;;

(defn rightmost
  "Increase the rightmost count, if any, and return one if not."
  [options]
  (assoc options :rightcnt (inc (:rightcnt options 0)))) 

(defn not-rightmost
  "Remove the rightmost count."
  [options]
  (dissoc options :rightcnt))

(defn c-r-pair
  "Handle the complexity of commas and rightmost-pair with options.
  If it isn't a rightmost, it loses rightmost status.
  If it is a rightmost, and in the rightmost pair, it gain one rightmost
  since it has the right end thing (and we don't care about the comma).
  If it is the rightmost of the non-rightmost-pair, then the comma
  matters, and we handle that appropriately.  Whew!"
  [commas? rightmost-pair? rightmost? options]
  (if-not rightmost?
    (not-rightmost options)
    (if rightmost-pair?
      options
      (if commas?
        (rightmost (not-rightmost options))
        (not-rightmost options)))))

(defn fix-rightcnt
  "Handle issue with rightcnt."
  [rightcnt]
  (if (number? rightcnt) rightcnt 0))

;;
;; # First pass at color -- turn string or type into keyword color
;;

;;
;; ## Translate from a string to a keyword as needed.
;;

(def str->key
  {"(" :paren,
   ")" :paren,
   "[" :bracket,
   "]" :bracket,
   "{" :brace,
   "}" :brace,
   "#{" :hash-brace,
   "#(" :hash-paren,
   "#_" :uneval,
   "'" :quote,
   "`" :quote,
   "~" :quote,
   "~@" :quote})
  

(defn zcolor-map
  "Look up the thing in the zprint-color-map.  Accepts keywords or
  strings."
  [{:keys [color-map], :as options} key-or-str]
  (color-map (if (keyword? key-or-str) key-or-str (str->key key-or-str))))


;;
;; ## Pretty Printer Code
;;

(declare fzprint*)
(declare fzprint-flow-seq)

(defn fzprint-hang-unless-fail
  "Try to hang something and if it doesn't hang at all, then flow it,
  but strongly prefer hang.  Has hang and flow indents, and fzfn is the
  fzprint-? function to use with zloc."
  [{:keys [width dbg?], {:keys [zseqnws zstring zfirst]} :zf, :as options}
   hindent findent fzfn zloc]
  (dbg options "fzprint-hang-unless-fail:" (zstring (zfirst zloc)))
  (let [hanging (fzfn (in-hang options) hindent zloc)]
    (dbg-form
      options
      "fzprint-hang-unless-fail: exit:"
      (if hanging
        hanging
        ; hang didn't work, do flow
        (do (dbg options "fzprint-hang-unless-fail: hang failed, doing flow")
            (concat-no-nil [[(str "\n" (blanks findent)) :none :whitespace]]
                           (fzfn options findent zloc)))))))
  
(declare fzprint-binding-vec)

(defn fzprint-two-up
  "Print a single pair of things (though it might not be exactly
  a pair, given comments and :extend and the like), 
  like bindings in a let, clauses in a cond, keys and values in a map.  
  Controlled by various maps, the key of which is caller."
  [caller
   {:keys [width one-line? dbg? dbg-indent in-hang? do-in-hang?],
    {:keys [zstring znth zcount zvector?]} :zf,
    {:keys [hang? dbg-local? dbg-cnt? indent indent-arg force-nl?]} caller,
    :as options} ind commas? justify-width rightmost-pair?
   [lloc rloc xloc :as pair]]
  (if dbg-cnt? (println "two-up: caller:" caller "hang?" hang? "dbg?" dbg?))
  (if (or dbg? dbg-local?)
    (println (or dbg-indent "")
             "==========================" (str "\n" (or dbg-indent ""))
             "fzprint-two-up:" (zstring lloc)
             "caller:" caller
             "count:" (count pair)
             "ind:" ind
             "indent:" indent
             "indent-arg:" indent-arg
             "justify-width:" justify-width
             "one-line?:" one-line?
             "hang?:" hang?
             "in-hang?" in-hang?
             "do-in-hang?" do-in-hang?
             "force-nl?" force-nl?
             "commas?" commas?
             "rightmost-pair?" rightmost-pair?))
  (let [local-hang? (or one-line? hang?)
        indent (or indent indent-arg)
        local-options
          (if (not local-hang?) (assoc options :one-line? true) options)
        loptions (c-r-pair commas? rightmost-pair? nil options)
        roptions (c-r-pair commas? rightmost-pair? :rightmost options)
        local-roptions
          (c-r-pair commas? rightmost-pair? :rightmost local-options)
        arg-1 (fzprint* loptions ind lloc)
        arg-1-lines (style-lines options ind arg-1)
        arg-1-fit-oneline? (and (not force-nl?)
                                (fzfit-one-line loptions arg-1-lines))
        arg-1-fit? (or arg-1-fit-oneline?
                       (when (not one-line?) (fzfit loptions arg-1-lines)))]
    ; if arg-1 doesn't fit, maybe that's just how it is!
    ; If we are in-hang, then we can bail, but otherwise, not.
    (when (or arg-1-fit? (not in-hang?))
      (cond
        (= (count pair) 1) (fzprint* roptions ind lloc)
        (= (count pair) 2)
          (concat-no-nil
            arg-1
            ; We used to think:
            ; We will always do hanging, either fully or with one-line? true,
            ; we will then do flow if hanging didn't do anything or if it did,
            ; we will try to see if flow is better.
            ;
            ; But now, we don't do hang if arg-1-fit-oneline? is false, since
            ; we won't use it.
            (let [hanging-width
                    (if justify-width justify-width (count (zstring lloc)))
                  hanging-spaces (if justify-width
                                   (inc (- justify-width
                                           (count (zstring lloc))))
                                   1)
                  hanging-indent (+ 1 hanging-width ind)
                  flow-indent (+ indent ind)]
              (if (and (zstring lloc)
                       (keyword-fn? options (zstring lloc))
                       (zvector? rloc))
                ; This is an embedded :let or :when-let or something
                (concat-no-nil ; TODO: fix this, need to get hang-unless-fail to
                               ; only
                               ; output this if it needs it.
                               [[(blanks hanging-spaces) :none :whitespace]]
                               (fzprint-hang-unless-fail loptions
                                                         hanging-indent
                                                         flow-indent
                                                         fzprint-binding-vec
                                                         rloc))
                ; This is a normal two element pair thing
                (let [; Perhaps someday we could figure out if we are already
                      ; completely in flow to this point, and be smarter about
                      ; possibly dealing with the hang or flow now.  But for
                      ; now, we will simply do hang even if arg-1 didn't fit
                      ; on one line if the flow indent isn't better than the
                      ; hang indent.
                      _ (dbg options
                             "fzprint-two-up: before hang.  in-hang?"
                             (and do-in-hang? (< flow-indent hanging-indent)))
                      hanging (when (or arg-1-fit-oneline?
                                        (>= flow-indent hanging-indent))
                                (fzprint* (if (< flow-indent hanging-indent)
                                            (in-hang local-roptions)
                                            local-roptions)
                                          hanging-indent
                                          rloc))
                      hang-count (zcount rloc)
                      _ (log-lines options
                                   "fzprint-two-up: hanging:"
                                   hanging-indent
                                   hanging)
                      hanging-lines (style-lines options hanging-indent hanging)
                      fit? (fzfit-one-line local-roptions hanging-lines)
                      hanging-lines (if fit?
                                      hanging-lines
                                      (when (and (not one-line?) hang?)
                                        hanging-lines))
                      ; Don't flow if it fit, or it didn't fit and we were doing
                      ; one line on input.  Do flow if we don't have
                      ; hanging-lines
                      ; and we were not one-line on input.
                      _ (log-lines options
                                   "fzprint-two-up: hanging-2:"
                                   hanging-indent
                                   hanging)
                      flow? (and (or (and (not hanging-lines) (not one-line?))
                                     (not (or fit? one-line?)))
                                 ; this is for situations where the first
                                 ; element is short and so the hanging indent
                                 ; is the same as the flow indent, so there is
                                 ; no point in flow -- unless we don't have
                                 ; any hanging-lines, in which case we better
                                 ; do flow
                                 (or (< flow-indent hanging-indent)
                                     (not hanging-lines)))
                      _ (dbg options "fzprint-two-up: before flow. flow?" flow?)
                      flow (when flow? (fzprint* roptions flow-indent rloc))
                      _ (log-lines options
                                   "fzprint-two-up: flow:"
                                   (+ indent ind)
                                   flow)
                      flow-lines (style-lines options (+ indent ind) flow)]
                  (when dbg-local?
                    (prn "fzprint-two-up: local-hang:" local-hang?)
                    (prn "fzprint-two-up: one-line?:" one-line?)
                    (prn "fzprint-two-up: hanging-indent:" hanging-indent)
                    (prn "fzprint-two-up: hanging-lines:" hanging-lines)
                    (prn "fzprint-two-up: flow?:" flow?)
                    (prn "fzprint-two-up: fit?:" fit?)
                    (prn "fzprint-two-up: flow-indent:" flow-indent)
                    (prn "fzprint-two-up: hanging:" (zstring lloc) hanging)
                    (prn "fzprint-two-up: (+ indent ind):" (+ indent ind))
                    (prn "fzprint-two-up: flow:" (zstring lloc) flow))
                  (dbg options "fzprint-two-up: before good-enough")
                  (if fit?
                    (concat-no-nil [[(blanks hanging-spaces) :none :whitespace]]
                                   hanging)
                    (when (or hanging-lines flow-lines)
                      (if (good-enough? caller
                                        roptions
                                        :none-two-up
                                        hang-count
                                        (- hanging-indent flow-indent)
                                        hanging-lines
                                        flow-lines)
                        (concat-no-nil [[(blanks hanging-spaces) :none
                                         :whitespace]]
                                       hanging)
                        (if justify-width
                          nil
                          (concat-no-nil [[(str "\n" (blanks (+ indent ind)))
                                           :none :whitespace]]
                                         flow)))))))))
        :else
          (concat-no-nil
            arg-1
            #_(fzprint* loptions ind lloc)
            [[(str "\n" (blanks (+ indent ind))) :none :whitespace]]
            ; This is a real seq, not a zloc seq
            #_(fzprint-remaining-seq options
                                     (+ indent ind)
                                     nil
                                     :force-nl
                                     (next pair))
            (fzprint-flow-seq options (+ indent ind) (next pair) :force-nl))))))

;;
;; # Two-up printing
;;

(defn map-stop-on-nil
  "A utility function to stop on nil."
  [f out in]
  (when out (let [result (f in)] (when result (conj out result)))))

(defn mapv-no-nil
  "A version of mapv which will stop the map if it gets a nil, and return
  nil."
  [stop-on-nil? f c]
  (if stop-on-nil? (reduce (partial map-stop-on-nil f) [] c) (mapv f c)))

(defn map-stop-on-nil-too-big
  "A utility function to stop on nil or too big or newline."
  [width f [size out :as prev] in]
  (when prev
    (let [result (f in)]
      (when result
        (let [result-str (apply str (mapv first result))
              new-size (+ (count result-str) size)]
          (when (and (not (re-find #"\n" result-str)) (<= new-size width))
            [new-size (conj out result)]))))))

;TODO: needs to be fixed to total the width?  Maybe, I think I fixed that...

(defn mapv-no-nil-too-big
  "A version of mapv which will stop the map if it gets a nil or too
  big, and return nil.  The width-to-stop is the amount left on the line,
  so we don't have to mess with the ind."
  [width-to-stop f c]
  (if width-to-stop
    (let [[_ out]
            (reduce (partial map-stop-on-nil-too-big width-to-stop f) [0 []] c)]
      out)
    (reduce (partial map-stop-on-nil f) [] c)))

(defn fzprint-justify-width
  "Figure the widthfor a justification of a set of pairs in coll.  
  Also, decide if it makes any sense to justify the pairs at all.
  For instance, they all need to be one-line."
  [caller
   {{:keys [zstring]} :zf, {:keys [justify?]} caller, :keys [dbg?], :as options}
   ind coll]
  (let [firsts (remove nil?
                 (map #(when (> (count %) 1) (fzprint* options ind (first %)))
                   coll))
        #_(def just firsts)
        style-seq (map (partial style-lines options ind) firsts)
        #_(def styleseq style-seq)
        each-one-line? (reduce #(when %1 (= (first %2) 1)) true style-seq)
        #_(def eol each-one-line?)
        justify-width (when each-one-line?
                        (reduce #(max %1 (second %2)) 0 style-seq))]
    (when justify-width (- justify-width ind))))

(defn fzprint-map-two-up
  "Accept a sequence of pairs, and map fzprint-two-up across those pairs.
  If you have :one-line? set, this will return nil if it is way over,
  but it can't accurately tell exactly what will fit on one line, since
  it doesn't know the separators and such.  So, :one-line? true is a
  performance optimization, so it doesn't do a whole huge map just to
  find out that it could not possibly have fit on one line."
  [caller
   {{:keys [zstring]} :zf,
    {:keys [justify?]} caller,
    :keys [dbg? width rightcnt one-line?],
    :as options} ind commas? coll]
  (dbg-print options
             "fzprint-map-two-up: one-line?" (:one-line? options)
             "justify?:" justify?)
  (let [len (count coll)
        justify-width (when (and justify? (not one-line?))
                        (fzprint-justify-width caller options ind coll))
        caller-options (when justify-width (options caller))]
    #_(def jo [])
    (loop [justify-width justify-width
           justify-options
             (if justify-width
               (-> options
                 (merge-deep {caller (caller-options :justify-hang)})
                 (merge-deep {:tuning (caller-options :justify-tuning)}))
               options)]
      #_(def jo (conj jo [justify-width justify-options]))
      (let [beginning (mapv-no-nil-too-big
                        (when one-line? (- width ind))
                        (partial fzprint-two-up
                                 caller
                                 justify-options
                                 ind
                                 commas?
                                 justify-width
                                 nil)
                        (butlast coll))
            beginning-lines
              (if one-line? (style-lines options ind (apply concat beginning)))
            end (mapv-no-nil-too-big
                  ; this one estimates the size optimistically
                  (when (and one-line? beginning-lines)
                    (- width (second beginning-lines) rightcnt))
                  ; this one way underestimates the size
                  (partial fzprint-two-up
                           caller
                           justify-options
                           ind
                           commas?
                           justify-width
                           :rightmost-pair)
                  [(last coll)])
            result (cond (= len 1) end :else (concat-no-nil beginning end))]
        (dbg options
             "fzprint-map-two-up: len:" len
             "(nil? end):" (nil? end)
             "(nil? beginning):" (nil? beginning)
             "(count end):" (count end)
             "(count beginnging):" (count beginning)
             "justify-width:" justify-width
             "result:" result)
        ; if we got a result or we didn't but it wasn't because we
        ; were trying to justify things
        (if (or result (not justify-width))
          result
          ; try again, without justify-width
          (recur nil options))))))

;;
;; ## Support sorting of map keys
;;

(defn compare-keys
  "Do a key comparison that works well for numbers as well as
  strings."
  [x y]
  (cond (and (number? x) (number? y)) (compare x y)
        :else (compare (str x) (str y))))

(defn compare-ordered-keys
  "Do a key comparison that places ordered keys first."
  [key-value zdotdotdot x y]
  (cond (and (key-value x) (key-value y)) (compare (key-value x) (key-value y))
        (key-value x) -1
        (key-value y) +1
        (= zdotdotdot x) +1
        (= zdotdotdot y) -1
        :else (compare-keys x y)))

(defn order-out
  "A variety of sorting and ordering options for the output
  of partition-all-2-nc.  It can sort, which is the default,
  but if the caller has a key-order vector, it will extract
  any keys in that vector and place them first (in order) before
  sorting the other keys."
  [caller
   {:keys [dbg?],
    {:keys [zsexpr zdotdotdot]} :zf,
    {:keys [sort? key-order key-value]} caller,
    :as options} out]
  (cond (or sort? key-order)
          (sort #((partial compare-ordered-keys (or key-value {}) (zdotdotdot))
                   (zsexpr (first %1))
                   (zsexpr (first %2)))
                out)
        sort? (sort #(compare-keys (zsexpr (first %1)) (zsexpr (first %2))) out)
        :else (throw (#?(:clj Exception.
                         :cljs js/Error.)
                      "Unknown options to order-out"))))

(defn pair-element?
  "This checks to see if an element should be considered part of a pair.
  Mostly this will trigger on comments, but a #_(...) element will also
  trigger this."
  [{:as options, {:keys [zuneval? zcomment?]} :zf} zloc]
  (or (zcomment? zloc) (zuneval? zloc)))

;;
;; # Ignore keys in maps
;;

(defn remove-key-seq
  "If given a non-collection, simply does a dissoc of the key, but
  if given a sequence of keys, will remove the final one."
  [m ks]
  (if (coll? ks)
    (let [this-key (first ks)
          next-key (next ks)]
      (if next-key
        (let [removed-map (remove-key-seq (get m this-key) (next ks))]
          (if (empty? removed-map)
            (dissoc m this-key)
            (assoc m this-key removed-map)))
        (dissoc m this-key)))
    (dissoc m ks)))

(defn ignore-key-seq-silent
  "Given a map and a key sequence, remove that key sequence if
  it appears in the map, and terminate the reduce if it changes
  the map."
  [m ks]
  (if (coll? ks)
    (if (= (get-in m ks :zprint-not-found) :zprint-not-found)
      m
      (remove-key-seq m ks))
    (if (= (get m ks :zprint-not-found) :zprint-not-found) m (dissoc m ks))))

(defn ignore-key-seq
  "Given a map and a key sequence, remove that key sequence if
  it appears in the map leaving behind a key :zprint-ignored, 
  and terminate the reduce if it changes the map."
  [m ks]
  (if (coll? ks)
    (if (= (get-in m ks :zprint-not-found) :zprint-not-found)
      m
      (assoc-in m ks :zprint-ignored))
    (if (= (get m ks :zprint-not-found) :zprint-not-found)
      m
      (assoc m ks :zprint-ignored))))

(defn map-ignore
  "Take a map and remove any of the key sequences specified from it.
  Note that this only works for sexpressions, not for actual zippers."
  [caller {{:keys [key-ignore key-ignore-silent]} caller, :as options} zloc]
  (let [ignored-silent (if key-ignore-silent
                         (reduce ignore-key-seq-silent zloc key-ignore-silent)
                         zloc)
        ignored (if key-ignore
                  (reduce ignore-key-seq ignored-silent key-ignore)
                  ignored-silent)]
    ignored))

;;
;; # Pre-processing for two-up printing
;;

(defn partition-all-2-nc
  "Just like partition-all 2, but doesn't pair up comments or
  #_(...) unevaled sexpressions.  The ones before the first part of a 
  pair come as a single element in a pair, and the ones between the 
  first and second parts of a pair come inside the pair.  
  There may be an arbitrary number between the first and second elements 
  of the pair (one per line).  If there are any comments or unevaled
  sexpressions, don't sort the keys, as we might lose track of where 
  the comments go."
  ([{:as options,
     :keys [width dbg? in-code? max-length],
     {:keys [zsexpr zstring zfirst zmap zdotdotdot]} :zf} coll caller]
   (dbg options "partition-all-2-nc: caller:" caller)
   (when-not (empty? coll)
     (loop [remaining coll
            no-sort? (or (not (:sort? (options caller)))
                         (and in-code? (not (:sort-in-code? (options caller)))))
            index 0
            out []]
       (if-not remaining
         (if no-sort? out (order-out caller options out))
         (let [[new-remaining pair-vec new-no-sort?]
                 (cond
                   (pair-element? options (first remaining))
                     [(next remaining) [(first remaining)] true]
                   (pair-element? options (second remaining))
                     (let [[comment-seq rest-seq]
                             (split-with (partial pair-element? options)
                                         (next remaining))]
                       [(next rest-seq)
                        (into []
                              (concat [(first remaining)]
                                      comment-seq
                                      [(first rest-seq)])) true])
                   (= (count remaining) 1) [(next remaining) [(first remaining)]
                                            nil]
                   :else [(next (next remaining))
                          [(first remaining) (second remaining)] nil])]
           (recur (if (not= index max-length) new-remaining (list (zdotdotdot)))
                  (or no-sort? new-no-sort?)
                  (inc index)
                  (conj out pair-vec)))))))
  ([options coll] (partition-all-2-nc options coll nil)))

;;
;; ## Multi-up printing pre-processing
;;

(defn cleave-end
  "Take a seq, and if it is contains a single symbol, simply return
  it in another seq.  If it contains something else, remove any non
  collections off of the end and return them in their own double seqs,
  as well as return the remainder (the beginning) as a double seq."
  [{:as options, {:keys [zcoll?]} :zf} coll]
  (if (symbol? (first coll))
    (list coll)
    (let [rev-seq (reverse coll)
          [split-non-coll _] (split-with (comp not zcoll?) rev-seq)
          split-non-coll (map list (reverse split-non-coll))
          remainder (take (- (count coll) (count split-non-coll)) coll)]
      (if (empty? remainder)
        split-non-coll
        (concat (list remainder) split-non-coll)))))

(defn partition-all-sym
  "Similar to partition-all-2-nc, but instead of trying to pair things
  up (modulo comments and unevaled expressions), this begins things
  with a symbol, and then accumulates collections until the next symbol.
  It handles comments before symbols on the symbol indent, and the comments
  before the collections on the collection indent.  Since it doesn't know
  how many collections there are, this is not trivial.  Must be called with
  a sequence of z-things"
  [{:as options, {:keys [zsymbol? zstring zcoll?]} :zf} coll]
  #_(def scoll coll)
  (dbg options "partition-all-sym: coll:" (map zstring coll))
  (let [part-sym (partition-by zsymbol? coll)
        split-non-coll (mapcat (partial cleave-end options) part-sym)]
    #_(def snc split-non-coll)
    (loop [remaining split-non-coll
           out []]
      (if (empty? remaining)
        out
        (let [[next-remaining new-out]
                (cond (and (zsymbol? (ffirst remaining))
                           (not (empty? (second remaining))))
                        [(nthnext remaining 2)
                         (conj out
                               (concat (first remaining) (second remaining)))]
                      :else [(next remaining) (conj out (first remaining))])]
          (recur next-remaining new-out))))))

(defn rstr-vec
  "Create an r-str-vec with, possibly, a newline at the beginning if
  the last thing before it is a comment."
  ([{{:keys [zcomment? zlast]} :zf, :as options} ind zloc r-str r-type]
   (let [nl (when (zcomment? (zlast zloc))
              [[(str "\n" (blanks ind)) :none :whitespace]])]
     (concat nl [[r-str (zcolor-map options (or r-type r-str)) :right]])))
  ([options ind zloc r-str] (rstr-vec options ind zloc r-str nil))) 

(defn fzprint-binding-vec
  [{:keys [width dbg?],
    {:keys [zseqnws zstring zfirst zcount]} :zf,
    :as options} ind zloc]
  (dbg options "fzprint-binding-vec:" (zstring (zfirst zloc)))
  (let [options (rightmost options)
        l-str "["
        r-str "]"
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (inc ind) zloc r-str)]
    (dbg-form
      options
      "fzprint-binding-vec exit:"
      (if (= (zcount zloc) 0)
        (concat-no-nil l-str-vec r-str-vec)
        (concat-no-nil
          l-str-vec
          (apply concat-no-nil
            (interpose [[(str "\n" (blanks (inc ind))) :none :whitespace]]
              (fzprint-map-two-up :binding
                                  options
                                  (inc ind)
                                  false
                                  (partition-all-2-nc options (zseqnws zloc)))))
          r-str-vec)))))

(defn fzprint-hang
  "Try to hang something and try to flow it, and then see which is
  better.  Has hang and flow indents. fzfn is the function to use 
  to do zloc."
  [{:keys [width dbg? one-line?],
    {:keys [zseqnws zstring zfirst zcount]} :zf,
    :as options} caller hindent findent fzfn zloc-count zloc]
  (dbg options "fzprint-hang:" (zstring (zfirst zloc)))
  (let [hanging (when (and (not= hindent findent) ((options caller) :hang?))
                  (concat-no-nil [[(str " ") :none :whitespace]]
                                 (fzfn (in-hang options) hindent zloc)))
        hang-count (or zloc-count (zcount zloc))
        hr-lines (style-lines options (dec hindent) hanging)
        flow (fzfn options findent zloc)]
    (if (or (fzfit-one-line options hr-lines) one-line?)
      hanging
      (let [flow (concat-no-nil [[(str "\n" (blanks findent)) :none
                                  :whitespace]]
                                (fzfn options findent zloc))
            _ (log-lines options "fzprint-hang: flow:" findent flow)
            fd-lines (style-lines options findent flow)
            _ (dbg options
                   "fzprint-hang: ending: hang-count:" hang-count
                   "hanging:" hanging
                   "flow:" flow)
            hr-good? (when (:hang? (caller options))
                       (good-enough? caller
                                     options
                                     :none-hang
                                     hang-count
                                     (- hindent findent)
                                     hr-lines
                                     fd-lines))]
        (if hr-good? hanging flow)))))

(defn fzprint-pairs
  "Always prints pairs on a different line from other pairs."
  [{:keys [width dbg?], {:keys [zmap-right zstring zfirst]} :zf, :as options}
   ind zloc]
  (dbg options "fzprint-pairs:" (zstring (zfirst zloc)))
  (dbg-form
    options
    "fzprint-pairs: exit:"
    (apply concat-no-nil
      (interpose [[(str "\n" (blanks ind)) :none :whitespace]]
        (fzprint-map-two-up
          :pair
          options
          ind
          false
          (let [part (partition-all-2-nc options (zmap-right identity zloc))]
            #_(def fp part)
            (dbg options "fzprint-pairs: partition:" (map zstring part))
            part))))))

(defn fzprint-extend
  "Print things with a symbol and collections following.  Kind of like with
  pairs, but not quite."
  [{:keys [width dbg?], {:keys [zmap-right zstring zfirst]} :zf, :as options}
   ind zloc]
  (dbg options "fzprint-extend:" (zstring (zfirst zloc)))
  (dbg-form
    options
    "fzprint-extend: exit:"
    (apply concat-no-nil
      (interpose [[(str "\n" (blanks ind)) :none :whitespace]]
        (fzprint-map-two-up
          :extend
          (assoc options :fn-style :fn)
          ind
          false
          (let [part (partition-all-sym options (zmap-right identity zloc))]
            #_(def fe part)
            (dbg options
                 "fzprint-extend: partition:"
                 (map #(map zstring %) part))
            part))))))

(defn fzprint-one-line
  "Do a fzprint-seq like thing, but do it incrementally and
  if it gets too big, return nil."
  [{:keys [width dbg?], {:keys [zmap]} :zf, :as options} ind zloc]
  (dbg-print options "fzprint-one-line:")
  (let [seq-right (zmap identity zloc)
        len (count seq-right)
        last-index (dec len)
        gt-1? (> (count seq-right) 1)
        options (assoc options :one-line? true)]
    (loop [zloc-seq seq-right
           new-ind ind
           index 0
           out []]
      (if (empty? zloc-seq)
        (do (dbg options "fzprint-one-line: exiting count:" (count out)) out)
        (let [next-zloc (first zloc-seq)
              [sep next-options]
                (cond ; this needs to come first in case there
                      ; is only one
                      ; element in the list -- it needs to have
                      ; the rightcnt
                      ; passed through
                      (= index last-index) [(if-not (zero? index)
                                              [[" " :none :whitespace]])
                                            options]
                      (= index 0) [nil (not-rightmost options)]
                      :else [[[" " :none :whitespace]] (not-rightmost options)])
              next-out (fzprint* next-options new-ind next-zloc)
              _ (log-lines options "fzprint-one-line:" new-ind next-out)
              [line-count max-width :as next-lines]
                (style-lines options new-ind next-out)]
          (if-not (fzfit-one-line next-options next-lines)
            (do (dbg options
                     "fzprint-one-line: failed, too wide or too many lines!")
                nil)
            (recur (next zloc-seq)
                   (inc max-width)
                   (inc index)
                   (concat out sep next-out))))))))

(defn fzprint-seq
  "Take a seq of a zloc, created by (zmap identity zloc) when zloc
  is a collection, or (zmap-right identity zloc) when zloc is already
  inside of a collection, and return a seq of the fzprint* of each 
  element.  No spacing between any of these elements. Note that this
  is not a style-vec, but a seq of style-vecs of each of the elements.
  These would need to be concatenated together to become a style-vec.
  ind is either a constant or a seq of indents, one for each element in
  zloc-seq."
  [{:keys [max-length], {:keys [zdotdotdot]} :zf, :as options} ind zloc-seq]
  (let [len (count zloc-seq)
        zloc-seq (if (> len max-length)
                   (concat (take max-length zloc-seq) (list (zdotdotdot)))
                   zloc-seq)]
    (dbg options "fzprint-seq: (count zloc-seq):" len)
    (when-not (empty? zloc-seq)
      (let [left (mapv #(fzprint* (not-rightmost options) %1 %2)
                   (if (coll? ind) ind (repeat ind))
                   (butlast zloc-seq))
            right [(fzprint* options
                             (if (coll? ind) (last ind) ind)
                             (last zloc-seq))]]
        (cond (= len 1) right :else (concat-no-nil left right))))))
    
(defn fzprint-flow-seq
  "Take a seq of a zloc, created by (zmap identity zloc) or
  and return a style-vec of the result.  Either it fits on one line, 
  or it is rendered on multiple lines.  You can force multiple lines 
  with force-nl?. If you want it to do less than everything in the 
  original zloc, modify the result of (zmap identity zloc) to just 
  contain what you want to print. ind is either a single indent,
  or a seq of indents, one for each element in zloc-seq."
  ([{:as options, :keys [width], {:keys [zstring zfirst]} :zf} ind zloc-seq
    force-nl?]
   (dbg options "fzprint-flow-seq: count zloc-seq:" (count zloc-seq))
   (let [coll-print (fzprint-seq options ind zloc-seq)
         one-line (apply concat-no-nil
                    (interpose [[" " :none :whitespace]] coll-print))
         _ (log-lines options "fzprint-flow-seq:" ind one-line)
         one-line-lines (style-lines options ind one-line)]
     (dbg-form
       options
       "fzprint-flow-seq: exit:"
       (if (and (not force-nl?) (fzfit-one-line options one-line-lines))
         one-line
         (apply concat-no-nil
           (if (coll? ind)
             (drop 1
                   (interleave (map #(vector [(str "\n" (blanks %)) :none
                                              :whitespace])
                                 ind)
                               coll-print))
             (interpose [[(str "\n" (blanks ind)) :none :whitespace]]
               coll-print)))))))
  ([options ind zloc-seq] (fzprint-flow-seq options ind zloc-seq nil)))
    
(defn fzprint-hang-one
  "Try out the given zloc, and if it fits on the current line, just
  do that. It might fit on the same line, as this may not be the rest
  of the list that we are printing. If not, check it out with good-enough?
  and do the best you can.  Three choices, really: fits on same line, 
  does ok as hanging, or better with flow. hindent is hang-indent, and 
  findent is flow-indent, and each contains the initial separator.  
  Might be nice if the fn-style actually got sent to this fn."
  [caller
   {{:keys [zstring zfirst zcoll? zcount]} :zf, :keys [one-line?], :as options}
   hindent findent zloc]
  (dbg options "fzprint-hang-one: hindent:" hindent "findent:" findent)
  (when (:dbg-hang options)
    (println (dots (:pdepth options))
             "h1 caller:"
             caller
             (zstring (if (zcoll? zloc) (zfirst zloc) zloc))))
  (let [local-options (if (and (not one-line?) (not (:hang? (caller options))))
                        (assoc options :one-line? true)
                        options)
        hanging (fzprint* (in-hang local-options) hindent zloc)
        hang-count (zcount zloc)
        hanging (concat-no-nil [[" " :none :whitespace]] hanging)
        _ (log-lines options "fzprint-hang-one: hanging:" (dec hindent) hanging)
        hr-lines (style-lines options (dec hindent) hanging)]
    _
    (dbg options
         "fzprint-hang-one: hr-lines:" hr-lines
         "hang-count:" hang-count)
    ; if hanging is nil and one-line? is true, then we didn't fit
    ; and should exit
    ;
    ; if hanging is nil and one-line? is nil, and hang? nil,
    ; then we we don't hang and this didn't fit on the same
    ; line and we should contine
    ;
    ; if hanging is true, then if one-line? is true and fzfit-one-line
    ; is true, then we just go with hanging
    ;
    ; if hanging is true and if fzfit-one-line is true, then we go
    ; with hanging.  Which is probably the same as just above.
    ;
    ; if hanging is true and if one-line? is nil, and if hang? is
    ; nil, and fzfit-one-line is true then it fit on one line and we
    ; should go with hanging.
    ;
    ;
    ; Summary:
    ;
    ; go with hanging if:
    ;
    ;  o fzfit-one-line true
    ;  o one-line? true
    ;
    ; Otherwise, see about flow too
    ;
    (if (or (fzfit-one-line options hr-lines) one-line?)
      hanging
      (let [flow (concat-no-nil [[(str "\n" (blanks findent)) :none
                                  :whitespace]]
                                (fzprint* options findent zloc))
            _ (log-lines options "fzprint-hang-one: flow:" findent flow)
            fd-lines (style-lines options findent flow)
            _ (dbg options "fzprint-hang-one: fd-lines:" fd-lines)
            _ (dbg options
                   "fzprint-hang-one: ending: hang-count:" hang-count
                   "hanging:" (pr-str hanging)
                   "flow:" (pr-str flow))
            hr-good? (and
                       (:hang? (caller options))
                       (good-enough? caller
                                     options
                                     :none-hang-one
                                     hang-count
                                     (- hindent findent)
                                     hr-lines
                                     fd-lines))]
        (if hr-good? hanging flow)))))

;;
;; # Constant pair support
;;

(defn count-constant-pairs
  "Given a seq of zlocs, work backwards from the end, and see how
  many elements are pairs of constants (using zconstant?).  So that
  (... :a (stuff) :b (bother)) returns 4, since both :a and :b are
  zconstant? true. This is made more difficult by having to skip
  comments along the way as part of the pair check, but keep track
  of the ones we skip so the count is right in the end.  We don't
  expect any whitespace in this, because this seq should have been
  produced by zmap-right or its equivalent, which already skips the
  whitespace."
  [{{:keys [zcomment? zconstant?]} :zf, :as options} seq-right]
  (loop [seq-right-rev (reverse seq-right)
         element-count 0
         ; since it is reversed, we need a constant second
         constant-required? nil
         pair-size 0]
    (let [element (first seq-right-rev)]
      (if (empty? seq-right-rev)
        ; remove potential elements of this pair,
        ; since we haven't seen the end of it
        (- element-count pair-size)
        (let [comment? (zcomment? element)]
          (if (and (not comment?) constant-required? (not (zconstant? element)))
            ; we counted the right-hand and any comments
            ; of this pair, but it isn't a pair
            (- element-count pair-size)
            (recur (next seq-right-rev)
                   (inc element-count)
                   (if comment? constant-required? (not constant-required?))
                   (if (and constant-required? (not comment?))
                     ; must be a constant, so start count over
                     0
                     (inc pair-size)))))))))

(defn constant-pair
  "Argument is result of (zmap-right identity zloc), that is to say
  a seq of zlocs.  Output is a pair-seq and non-paired-item-count,
  if any."
  [caller
   {{:keys [zconstant?]} :zf,
    {:keys [constant-pair? constant-pair-min]} caller,
    :as options} seq-right]
  (let [seq-right-rev (reverse seq-right)
        keywords (partition 2 2 nil (mapv zconstant? seq-right-rev))
        _ (dbg options "constant-pair: keywords:" keywords)
        #_(def kw keywords)
        #_(def cpsr seq-right)
        keywordsonly (take-while second keywords)
        _ (dbg options
               "constant-pair: keywordsonly:" keywordsonly
               "keywords count:" (count keywords)
               "keywordsonly count:" (count keywordsonly))
        paired-item-count (count-constant-pairs options seq-right)
        non-paired-item-count (- (count seq-right) paired-item-count)
        _ (dbg options "constant-pair: non-paired-items:" non-paired-item-count)
        pair-seq (when (and constant-pair?
                            (>= paired-item-count constant-pair-min))
                   (partition-all-2-nc options
                                       (drop non-paired-item-count seq-right)))]
    [pair-seq non-paired-item-count]))

;;
;; # Take into account constant pairs
;;

(defn fzprint-hang-remaining
  "zloc is already down inside a collection, it is not the collection
  itself. Operate on what is to the right zloc.  We already know
  that the given zloc won't fit on the current line. Besides, we
  ensure that if there are two things remaining anyway. So now, try
  hanging and see if that is better than flow.  Unless :hang? is
  nil, in which case we will just flow.  hindent is hang-indent,
  and findent is flow-indent. This should never be called with
  :one-line because this is only called from fzprint-list* after
  the one-line processing is done. If the hindent equals the flow
  indent, then just do flow."
  ;equals the flow indent, then just do hanging.  Really?
  [caller
   {:keys [dbg?],
    {:keys [zstring zfirst zcount zmap-right zconstant? zseqnws zmap znextnws]}
      :zf,
    {:keys [hang? constant-pair? constant-pair-min hang-expand hang-diff]}
      caller,
    :as options} hindent findent zloc fn-style]
  (when (:dbg-hang options)
    (println (dots (:pdepth options)) "hr" (zstring zloc)))
  (dbg options
       "fzprint-hang-remaining:" (zstring zloc)
       "hindent:" hindent
       "findent:" findent
       "caller:" caller)
  ; (in-hang options) slows things down here, for some reason
  (let [seq-right (zmap-right identity zloc)
        [pair-seq non-paired-item-count]
          (constant-pair caller options seq-right)
        _ (dbg options
               "fzprint-hang-remaining count pair-seq:"
               (count pair-seq))
        flow (apply concat-no-nil
               (interpose [[(str "\n" (blanks findent)) :none :whitespace]]
                 (if-not pair-seq
                   (fzprint-seq options findent seq-right)
                   (if (not (zero? non-paired-item-count))
                     (concat-no-nil
                       (mapv (partial fzprint* (not-rightmost options) findent)
                         (take non-paired-item-count seq-right))
                       (fzprint-map-two-up :pair
                                           ;caller
                                           options
                                           findent
                                           nil
                                           pair-seq))
                     (fzprint-map-two-up :pair
                                         ;caller
                                         options
                                         findent
                                         nil
                                         pair-seq)))))
        flow-lines (style-lines options findent flow)
        ; Now determine if there is any point in doing a hang, because
        ; if the flow is beyond the expand limit, there is really no
        ; chance that the hang is not beyond the expand limit.
        ; This is what good-enough? does:
        ;  (<= (/ (dec p-lines) p-count) hang-expand))
        ;  Also need to account for the indent diffs.
        ; Would be nice to move this into a common routine, since this
        ; duplicates logic in good-enough?
        hang? (and hang?
                   (not= hindent findent)
                   flow-lines
                   (or (<= (- hindent findent) hang-diff)
                       (<= (/ (dec (first flow-lines)) (count seq-right))
                           hang-expand)))
        hanging
          (when hang?
            (if-not pair-seq
              (fzprint-seq (in-hang options) hindent seq-right)
              (if (not (zero? non-paired-item-count))
                (concat-no-nil
                  (dbg-form options
                            "fzprint-hang-remaining: mapv:"
                            (mapv (partial fzprint*
                                           (not-rightmost (in-hang options))
                                           hindent)
                              (take non-paired-item-count seq-right)))
                  (dbg-form
                    options
                    "fzprint-hang-remaining: fzprint-hang:"
                    (fzprint-map-two-up :pair
                                        ;caller
                                        (in-hang options)
                                        hindent
                                        nil
                                        pair-seq)))
                (fzprint-map-two-up :pair
                                    ;caller
                                    (in-hang options)
                                    hindent
                                    nil
                                    pair-seq))))
        ; TODO: can we figure out hang-count from fzprint-seq return?
        ; or do zcount or something?  Seems like we need it for good-enough?
        hang-count (if hang? (count hanging) 0)
        hanging (when hang?
                  (apply concat-no-nil
                    (interpose [[(str "\n" (blanks hindent)) :none :whitespace]]
                      hanging)))
        _ (log-lines options "fzprint-hang-remaining: hanging:" hindent hanging)
        hanging-lines (style-lines options hindent hanging)
        _ (dbg options
               "fzprint-hang-remaining: hanging-lines:" hanging-lines
               "hang-count:" hang-count)
        _ (if (fzfit-one-line options hanging-lines)
            (println "*_*_*_*_*_*_*_*_*_*_"))]
    (dbg options "fzprint-hang-remaining: flow-lines:" flow-lines)
    (when dbg?
      (if (zero? hang-count)
        (println "hang-count = 0:" (str (zmap-right zstring zloc)))))
    (log-lines options "fzprint-hang-remaining: flow" findent flow)
    (when flow-lines
      (if (good-enough? caller
                        options
                        fn-style
                        hang-count
                        (- hindent findent)
                        hanging-lines
                        flow-lines)
        (concat-no-nil [[" " :none :whitespace]] hanging)
        (concat-no-nil [[(str "\n" (blanks findent)) :none :whitespace]]
                       flow)))))

;;
;; # Utilities to modify list printing in various ways
;;

(defn map-nosort
  "Modify the options map to say we don't sort maps unless we are forced to
  do so."
  [options]
  (if (get-in options [:map :sort?])
    (assoc-in options [:map :sort?] nil)
    options))

;;
;; Which fn-styles use :list {:indent n} instead of
;; :list {:indent-arg n}
;;

(def body-set
  #{:binding :arg1-> :arg2 :arg2-pair :pair :fn :arg1-body :arg1-pair-body
    :arg1-extend-body :none-body})

(def body-map
  {:arg1-body :arg1,
   :arg1-pair-body :arg1-pair,
   :arg1-extend-body :arg1-extend,
   :none-body :none})

(defn fzprint-list*
  "Print a list, which might be a list or an anon fn.  
  Lots of work to make a list look good, as that is typically code. 
  Presently all of the callers of this are :list."
  [caller l-str r-str
   {:keys [width fn-map user-fn-map one-line? dbg? fn-style no-arg1],
    {:keys [zstring zmap zfirst zsecond zsexpr zcoll? zcount zvector? znth
            zlist? zcomment? zmap-right zidentity zmeta? zsymbol?]}
      :zf,
    {:keys [indent-arg indent]} caller,
    :as options} ind zloc]
  (let [len (zcount zloc)
        l-str-len (count l-str)
        arg-1-coll? (not (zsymbol? (zfirst zloc)))
        fn-str (if-not arg-1-coll? (zstring (zfirst zloc)))
        fn-style (or fn-style (fn-map fn-str) (user-fn-map fn-str))
        ; if we don't have a function style, let's see if we can get
        ; one by removing the namespacing
        fn-style (if (and (not fn-style) fn-str)
                   (fn-map (last (clojure.string/split fn-str #"/")))
                   fn-style)
        ; set indent based on fn-style
        indent (if (body-set fn-style) indent (or indent-arg indent))
        ; remove -body from fn-style if it was there
        fn-style (or (body-map fn-style) fn-style)
        ; If l-str isn't one char, create an indent adjustment.  Largely
        ; for anonymous functions, which otherwise would have their own
        ; :anon config to parallel :list, which would be just too much
        indent-adj (dec l-str-len)
        default-indent (if (zlist? (zfirst zloc)) indent l-str-len)
        isfn? (not arg-1-coll?)
        arg-1-indent (if-not (or arg-1-coll? (zcomment? (zfirst zloc)))
                       (+ ind (inc l-str-len) (count fn-str)))
        ; Tell people inside that we are in code.
        ; We don't catch places where the first thing in a list is
        ; a collection which yields a function.
        options (if isfn? (assoc options :in-code? true) options)
        options (assoc options :pdepth (inc (or (:pdepth options) 0)))
        _ (when (:dbg-hang options)
            (println (dots (:pdepth options)) "fzs" fn-str))
        new-ind (+ indent ind)
        one-line-ind (+ l-str-len ind)
        options (if fn-style (dissoc options :fn-style) options)
        loptions (not-rightmost options)
        roptions options
        ; All styles except :hang need three elements minimum.
        ; We could put this in the fn-map, but until there is more
        ; than one exception, seems like too much mechanism.
        fn-style (if (= fn-style :hang) fn-style (if (< len 3) nil fn-style))
        fn-style (if no-arg1
                   (case fn-style
                     :arg1 nil
                     :arg1-pair :pair
                     :arg1-extend :extend
                     :arg2 :arg1
                     :arg2-pair :arg1-pair
                     fn-style)
                   fn-style)
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
        _ (dbg options
               "fzprint-list*:" (zstring zloc)
               "fn-str" fn-str
               "fn-style:" fn-style
               "ind:" ind
               "indent:" indent
               "default-indent:" default-indent
               "width:" width
               "arg-1-indent:" arg-1-indent
               "len:" len
               "one-line?:" one-line?
               "rightcnt:" (:rightcnt options))
        one-line
          (if (zero? len) :empty (fzprint-one-line options one-line-ind zloc))]
    (cond
      one-line (if (= one-line :empty)
                 (concat-no-nil l-str-vec r-str-vec)
                 (concat-no-nil l-str-vec one-line r-str-vec))
      ; If we are in :one-line mode, and it didn't fit on one line,
      ; we are done!  We don't see this debugging, below.  Suppose
      ; we never get here?
      one-line?
        (dbg options "fzprint-list*:" fn-str " one-line did not work!!!")
      (dbg options "fzprint-list*: fn-style:" fn-style) nil
      (= len 0) (concat-no-nil l-str-vec r-str-vec)
      (= len 1) (concat-no-nil l-str-vec
                               (fzprint* roptions one-line-ind (zfirst zloc))
                               r-str-vec)
      ; needs (> len 2) but we already checked for that above in fn-style
      (and (= fn-style :binding) (zvector? (zsecond zloc)))
        (concat-no-nil l-str-vec
                       ; TODO: get rid of inc ind
                       (fzprint* loptions (inc ind) (zfirst zloc))
                       [[" " :none :whitespace]]
                       (fzprint-hang-unless-fail loptions
                                                 arg-1-indent
                                                 (+ indent ind)
                                                 fzprint-binding-vec
                                                 (zsecond zloc))
                       #_(fzprint-hang loptions
                                       :binding
                                       arg-1-indent
                                       (+ indent ind)
                                       fzprint-binding-vec
                                       (zsecond zloc))
                       [[(str "\n" (blanks (+ indent ind))) :none :whitespace]]
                       ; here we use options, because fzprint-flow-seq
                       ; will sort it out
                       (fzprint-flow-seq options
                                         (+ indent ind)
                                         (nthnext (zmap identity zloc) 2)
                                         :force-nl)
                       r-str-vec)
      (= fn-style :pair)
        (concat-no-nil l-str-vec
                       (fzprint* loptions (inc ind) (zfirst zloc))
                       ;    [[(str " ") :none :whitespace]]
                       (fzprint-hang options
                                     :pair-fn
                                     arg-1-indent
                                     (+ indent ind)
                                     fzprint-pairs
                                     (count (zmap-right identity (znth zloc 0)))
                                     (znth zloc 0))
                       r-str-vec)
      (= fn-style :extend)
        (concat-no-nil l-str-vec
                       (fzprint* loptions (inc ind) (zfirst zloc))
                       [[(str "\n" (blanks (+ indent ind))) :none :whitespace]]
                       ; I think fzprint-pairs will sort out which
                       ; is and isn't the rightmost because of two-up
                       (fzprint-extend options (+ indent ind) (znth zloc 0))
                       r-str-vec)
      ; needs (> len 2) but we already checked for that above in fn-style
      (or (and (= fn-style :fn) (not (zlist? (zsecond zloc))))
          (= fn-style :arg2)
          (= fn-style :arg2-pair))
        (let [second-element (fzprint-hang-one caller
                                               (if (= len 2) options loptions)
                                               arg-1-indent
                                               (+ indent ind)
                                               (zsecond zloc))
              [line-count max-width]
                (style-lines loptions arg-1-indent second-element)
              third (znth zloc 2)
              first-three
                (concat-no-nil
                  (fzprint* loptions
                            ;(inc ind)
                            (+ indent ind)
                            (zfirst zloc))
                  second-element
                  (if (or (= fn-style :arg2)
                          (= fn-style :arg2-pair)
                          (and (zvector? third) (= line-count 1)))
                    (fzprint-hang-one caller
                                      (if (= len 3) options loptions)
                                      (inc max-width)
                                      (+ indent ind)
                                      third)
                    (concat-no-nil [[(str "\n" (blanks (+ indent ind))) :none
                                     :whitespace]]
                                   (fzprint* (if (= len 3) options loptions)
                                             (+ indent ind)
                                             third))))]
          (if (= len 3)
            (concat-no-nil l-str-vec first-three r-str-vec)
            (concat-no-nil
              l-str-vec
              first-three
              (if (= fn-style :arg2-pair)
                (concat-no-nil
                  [[(str "\n" (blanks (+ indent ind))) :none :whitespace]]
                  (fzprint-pairs options (+ indent ind) (znth zloc 2)))
                (fzprint-hang-remaining caller
                                        options
                                        (+ indent ind)
                                        ; force flow
                                        (+ indent ind)
                                        (znth zloc 2)
                                        fn-style))
              r-str-vec)))
      (or (= fn-style :arg1-pair) (= fn-style :arg1) (= fn-style :arg1->))
        (concat-no-nil
          l-str-vec
          (fzprint* loptions (inc ind) (zfirst zloc))
          (fzprint-hang-one caller
                            (if (= len 2) options loptions)
                            arg-1-indent
                            (+ indent ind)
                            (zsecond zloc))
          ; then either pair or remaining-seq
          ; we don't do a full hanging here.
          (when (> len 2)
            (if (= fn-style :arg1-pair)
              (concat-no-nil
                [[(str "\n" (blanks (+ indent ind))) :none :whitespace]]
                (fzprint-pairs options (+ indent ind) (znth zloc 1)))
              (fzprint-hang-remaining
                caller
                (if (= fn-style :arg1->) (assoc options :no-arg1 true) options)
                (+ indent ind)
                ; force flow
                (+ indent ind)
                (znth zloc 1)
                fn-style)))
          r-str-vec)
      ; we know that (> len 2) if fn-style not= nil
      (= fn-style :arg1-extend)
        (cond
          (zvector? (zsecond zloc))
            (concat-no-nil
              l-str-vec
              (fzprint* loptions (inc ind) (zfirst zloc))
              [[(str "\n" (blanks (+ indent ind))) :none :whitespace]]
              (fzprint* loptions (inc ind) (zsecond zloc))
              [[(str "\n" (blanks (+ indent ind))) :none :whitespace]]
              (fzprint-extend options (+ indent ind) (znth zloc 2))
              r-str-vec)
          :else (concat-no-nil
                  l-str-vec
                  (fzprint* loptions (inc ind) (zfirst zloc))
                  (fzprint-hang-one caller
                                    (if (= len 2) options loptions)
                                    arg-1-indent
                                    (+ indent ind)
                                    (zsecond zloc))
                  [[(str "\n" (blanks (+ indent ind))) :none :whitespace]]
                  (fzprint-extend options (+ indent ind) (znth zloc 1))
                  r-str-vec))
      ;
      ; Unspecified seq, might be a fn, might not.
      ; If (first zloc) is a seq, we won't have an
      ; arg-1-indent.  In that case, just flow it
      ; out with remaining seq.  Since we already
      ; know that it won't fit on one line.  If it
      ; might be a fn, try hanging and flow and do
      ; what we like better.  Note that default-indent
      ; might be 1 here, which means that we are pretty
      ; sure that the (zfirst zloc) isn't a function
      ; and we aren't doing code.
      ;
      :else (concat-no-nil
              l-str-vec
              (fzprint* loptions (+ l-str-len ind) (zfirst zloc))
              (if arg-1-indent
                (fzprint-hang-remaining caller
                                        options
                                        arg-1-indent
                                        (+ indent ind indent-adj)
                                        (znth zloc 0)
                                        fn-style)
                (concat-no-nil
                  [[(str "\n" (blanks (+ default-indent ind indent-adj))) :none
                    :whitespace]]
                  (fzprint-flow-seq options
                                    (+ default-indent ind indent-adj)
                                    (nthnext (zmap identity zloc) 1)
                                    :force-nl)))
              r-str-vec))))

(defn fzprint-list
  "Pretty print and focus style a :list element."
  [options ind zloc]
  (fzprint-list* :list "(" ")" (rightmost options) ind zloc))

(defn fzprint-anon-fn
  "Pretty print and focus style a fn element."
  [options ind zloc]
  (fzprint-list* :list "#(" ")" (rightmost options) ind zloc))

(defn any-zcoll?
  "Return true if there are any collections in the collection."
  [{:keys [width], {:keys [zmap zcoll?]} :zf, :as options} ind zloc]
  (let [coll?-seq (zmap zcoll? zloc)] (reduce #(or %1 %2) nil coll?-seq)))

;;
;; # Put things on the same line
;;

(defn wrap-zmap
  "Given the output from fzprint-seq, which is a style-vec in
  the making without spacing, but with extra [] around the elements,
  wrap the elements to the right margin."
  [caller
   {:keys [width rightcnt max-length],
    {:keys [wrap-after-multi?]} caller,
    :as options} ind coll-print]
  #_(prn "wz:" coll-print)
  (let [last-index (dec (count coll-print))
        rightcnt (fix-rightcnt rightcnt)]
    (loop [cur-seq coll-print
           cur-ind ind
           index 0
           out []]
      (if-not cur-seq
        out
        (let [next-seq (first cur-seq)]
          (when next-seq
            (let [multi? (> (count (first cur-seq)) 1)
                  this-seq (first cur-seq)
                  _ (log-lines options "wrap-zmap:" ind this-seq)
                  _ (dbg options "wrap-zmap: ind:" ind "this-seq:" this-seq)
                  [linecnt max-width lines] (style-lines options ind this-seq)
                  last-width (last lines)
                  len (- last-width ind)
                  len (max 0 len)
                  width (if (= index last-index) (- width rightcnt) width)
                  ; need to check size, and if one line and fits, should fit
                  fit? (or (zero? index)
                           (and (if multi? (= linecnt 1) true)
                                (<= (+ cur-ind len) width)))]
              #_(prn "this-seq:" this-seq
                     "lines:" lines
                     "linecnt:" linecnt
                     "multi?" multi?
                     "linecnt:" linecnt
                     "max-width:" max-width
                     "last-width:" last-width
                     "len:" len
                     "cur-ind:" cur-ind
                     "width:" width
                     "fit?" fit?)
              ; need to figure out what to do with a comment,
              ; want to force next line to not fit whether or not
              ; this line fit.  Comments are already multi-line, and
              ; it is really not clear what multi? does in this routine
              (recur
                (next cur-seq)
                (cond (= (nth (first this-seq) 2) :comment) (inc width)
                      (and multi? (> linecnt 1) (not wrap-after-multi?)) width
                      fit? (+ cur-ind len 1)
                      :else (+ ind len 1))
                (inc index)
                ; TODO: concat-no-nil fails here, why?
                (concat
                  out
                  (if fit?
                    (if (not (zero? index))
                      (concat-no-nil [[" " :none :whitespace]] this-seq)
                      this-seq)
                    (concat-no-nil [[(str "\n" (blanks ind)) :none :whitespace]]
                                   this-seq)))))))))))

(defn fzprint-vec*
  "Print basic stuff like a vector or a set.  Several options for how to
  print them."
  [caller l-str r-str
   {:keys [width rightcnt dbg? one-line?],
    {:keys [zstring zcount zmap]} :zf,
    {:keys [wrap-coll? wrap?]} caller,
    :as options} ind zloc]
  (let [l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options ind zloc r-str)
        new-ind (+ (count l-str) ind)
        _ (dbg-pr options
                  "fzprint-vec*:" (zstring zloc)
                  "new-ind:" new-ind
                  "width:" width)
        coll-print (if (zero? (zcount zloc))
                     [[["" :none :whitespace]]]
                     (fzprint-seq options new-ind (zmap identity zloc)))
        _ (dbg-pr options "fzprint-vec*: coll-print:" coll-print)
        ; If we got any nils from fzprint-seq and we were in :one-line mode
        ; then give up -- it didn't fit on one line.
        coll-print (if-not (contains-nil? coll-print) coll-print)
        one-line (when coll-print
                   ; should not be necessary with contains-nil? above
                   (apply concat-no-nil
                     (interpose [[" " :none :whitespace]] coll-print)))
        _ (log-lines options "fzprint-vec*:" new-ind one-line)
        one-line-lines (style-lines options new-ind one-line)]
    (when one-line-lines
      (if (fzfit-one-line options one-line-lines)
        (concat-no-nil l-str-vec one-line r-str-vec)
        (if (or (and (not wrap-coll?) (any-zcoll? options new-ind zloc))
                (not wrap?))
          (concat-no-nil l-str-vec
                         (apply concat-no-nil
                           (interpose [[(str "\n" (blanks new-ind)) :none
                                        :whitespace]]
                             coll-print))
                         r-str-vec)
          ;
          ; Since there are no collections in this vector or set or
          ; whatever, print it wrapped on the same line as much as
          ; possible: [a b c d e f
          ;            g h i j]
          ;
          (concat-no-nil
            l-str-vec
            (do (dbg options "fzprint-vec*: wrap coll-print:" coll-print)
                (wrap-zmap caller options new-ind coll-print))
            r-str-vec))))))

(defn fzprint-vec
  [options ind zloc]
  (fzprint-vec* :vector "[" "]" (rightmost options) ind zloc))

(defn fzprint-array
  [options ind zloc]
  (fzprint-vec* :array "[" "]" (rightmost options) ind zloc))

(defn fzprint-set
  "Pretty print and focus style a :set element."
  [options ind zloc]
  (fzprint-vec* :set "#{" "}" (rightmost options) ind zloc))

(defn interpose-either
  "Do the same as interpose, but different seps depending on pred?."
  [sep-true sep-nil pred? coll]
  (loop [coll coll
         out []
         interpose? nil]
    (if (empty? coll)
      out
      (recur (next coll)
             (if interpose?
               (conj out sep-true (first coll))
               (if (empty? out)
                 (conj out (first coll))
                 (conj out sep-nil (first coll))))
             (pred? (first coll))))))

(defn fzprint-map*
  [caller l-str r-str
   {:keys [width dbg? one-line? ztype],
    {:keys [zseqnws zstring zfirst]} :zf,
    {:keys [sort? comma? key-ignore key-ignore-silent]} caller,
    :as options} ind zloc]
  (let [zloc (if (and (= ztype :sexpr) (or key-ignore key-ignore-silent))
               (map-ignore caller options zloc)
               zloc)
        pair-seq (partition-all-2-nc options (zseqnws zloc) caller)
        indent (count l-str)
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)]
    (if (empty? pair-seq)
      (concat-no-nil l-str-vec r-str-vec)
      (let [_ (dbg options
                   "fzprint-map*:" (zstring zloc)
                   "ind:" ind
                   "width:" width
                   "rightcnt:" (:rightcnt options))
            ; A possible one line representation of this map, but this is
            ; optimistic and needs to be validated.
            pair-print-one-line
              (fzprint-map-two-up
                caller
                (if one-line? options (assoc options :one-line? true))
                (+ indent ind)
                :commas
                pair-seq)
            one-line (when pair-print-one-line
                       (apply concat-no-nil
                         ;; is this really whitespace?
                         (interpose [[", " :none :whitespace]]
                           pair-print-one-line)))
            one-line-lines (style-lines options (+ indent ind) one-line)
            one-line (when (fzfit-one-line options one-line-lines) one-line)]
        (if one-line
          (concat-no-nil l-str-vec one-line r-str-vec)
          (when (not one-line?)
            (let [pair-print (fzprint-map-two-up caller
                                                 options
                                                 (+ indent ind)
                                                 :commas
                                                 pair-seq)
                  ; If we are in :one-line mode and we got any nils when doing
                  ; the two-up, we're out of here.  It didn't fit, so we need
                  ; to return nil.
                  _ (dbg-pr options "fzprint-map*: pair-print:" pair-print)
                  pair-print (if-not (and one-line? (contains-nil? pair-print))
                               pair-print)
                  _ (dbg-pr options "fzprint-map*: pair-print2:" pair-print)
                  one-line (when pair-print
                             (apply concat-no-nil
                               ;; is this really whitespace?
                               (interpose [[", " :none :whitespace]]
                                 pair-print)))
                  _ (log-lines options "fzprint-map*:" (+ indent ind) one-line)
                  one-line-lines (style-lines options (+ indent ind) one-line)]
              ; In theory it should now be impossible to get a one-line fit
              ; because we already did those above.  The reason we did it
              ; above is because when it is :one-line? true, we don't justify
              ; down in fzprint-map-two-up.  So we needed to see if one-line
              ; would work w/out justification, before we came through here
              ; in case we were justifying.  So, probably, we can take out
              ; all of the one-line stuff here.  I hope.
              ; TODO: Remove one line code here since it is handled above.
              (when one-line-lines
                (if (fzfit-one-line options one-line-lines)
                  (concat-no-nil l-str-vec one-line r-str-vec)
                  (concat-no-nil
                    l-str-vec
                    (apply concat-no-nil
                      (interpose-either
                        [["," ;(str "," (blanks (inc ind)))
                          :none :whitespace]
                         [(str "\n" (blanks (inc ind))) :none :whitespace]]
                        [[(str "\n" (blanks (inc ind))) :none :whitespace]]
                        #(and comma? (not= (nth (first %) 2) :comment))
                        pair-print))
                    r-str-vec))))))))))

(defn fzprint-map
  "Format a real map."
  [options ind zloc]
  (fzprint-map* :map "{" "}" (rightmost options) ind zloc))

(defn object-str?
  "Return true if the string starts with #object["
  [s]
  (re-find #"^#object\[" s))

(defn fzprint-object
  "Print something that looks like #object[...] in a way
  that will acknowledge the structure inside of the [...]"
  ([{:keys [width dbg? one-line?],
     {:keys [zobj-to-vec zstring zfirst]} :zf,
     :as options} ind zloc zloc-value]
   (fzprint-vec* :object
                 "#object["
                 "]"
                 options
                 ind
                 (zobj-to-vec zloc zloc-value)))
  ([{:keys [width dbg? one-line?],
     {:keys [zobj-to-vec zstring zfirst]} :zf,
     :as options} ind zloc]
   (fzprint-vec* :object "#object[" "]" options ind (zobj-to-vec zloc))))

(defn hash-identity-str
  "Find the hash-code identity for an object."
  [obj]
  #?(:clj (Integer/toHexString (System/identityHashCode obj))
     :cljs (str (hash obj))))

; (with-out-str
;    (printf "%08x" (System/identityHashCode obj))))

(defn fzprint-atom
  [{:keys [width dbg? one-line?],
    {:keys [zstring zfirst zderef]} :zf,
    {:keys [object?]} :atom,
    :as options} ind zloc]
  (if (and object? (object-str? (zstring zloc)))
    (fzprint-object options ind zloc (zderef zloc))
    (let [l-str "#<"
          r-str ">"
          indent (count l-str)
          l-str-vec [[l-str (zcolor-map options l-str) :left]]
          r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
          arg-1 (str "Atom@" (hash-identity-str zloc))
          arg-1-indent (+ ind indent 1 (count arg-1))]
      (dbg-pr options
              "fzprint-atom: arg-1:" arg-1
              "zstring arg-1:" (zstring zloc))
      (concat-no-nil l-str-vec
                     [[arg-1 (zcolor-map options :none) :element]]
                     (fzprint-hang-one :unknown
                                       (rightmost options)
                                       arg-1-indent
                                       (+ indent ind)
                                       (zderef zloc))
                     r-str-vec))))

(defn fzprint-future-promise-delay-agent
  "Print out a future or a promise or a delay.  These can only be 
  sexpressions, since they don't exist in a textual representation 
  of code (or data for that matter).  That means that we can use 
  regular sexpression operations on zloc."
  [{:keys [width dbg? one-line?],
    {:keys [zstring zfirst zderef zpromise? zagent? zdelay? zfuture?]} :zf,
    :as options} ind zloc]
  (let [zloc-type (cond (zfuture? zloc) :future
                        (zpromise? zloc) :promise
                        (zdelay? zloc) :delay
                        (zagent? zloc) :agent
                        :else (throw
                                (#?(:clj Exception.
                                    :cljs js/Error.)
                                 "Not a future, promise, or delay:"
                                 (zstring zloc))))]
    (if (and (:object? (options zloc-type)) (object-str? (zstring zloc)))
      (if (or (= zloc-type :agent) (realized? zloc))
        (fzprint-object options ind zloc (zderef zloc))
        (fzprint-object options ind zloc))
      (let [l-str "#<"
            r-str ">"
            indent (count l-str)
            l-str-vec [[l-str (zcolor-map options l-str) :left]]
            r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
            type-str (case zloc-type
                       :future "Future@"
                       :promise "Promise@"
                       :delay "Delay@"
                       :agent "Agent@")
            arg-1 (str type-str (hash-identity-str zloc))
            #?@(:clj [arg-1
                      (if (and (= zloc-type :agent) (agent-error zloc))
                        (str arg-1 " FAILED")
                        arg-1)])
              arg-1-indent
            (+ ind indent 1 (count arg-1)) zloc-realized?
            (if (= zloc-type :agent) true (realized? zloc)) value
            (if zloc-realized?
              (zderef zloc)
              (case zloc-type
                :future "pending"
                :promise "not-delivered"
                :delay "pending"))
              options
            (if zloc-realized? options (assoc options :string-str? true))]
        (dbg-pr options
                "fzprint-fpda: arg-1:" arg-1
                "zstring arg-1:" (zstring zloc))
        (concat-no-nil l-str-vec
                       [[arg-1 (zcolor-map options :none) :element]]
                       (fzprint-hang-one :unknown
                                         (rightmost options)
                                         arg-1-indent
                                         (+ indent ind)
                                         value)
                       r-str-vec)))))

(defn fzprint-fn-obj
  "Print a function object, what you get when you put a function in
  a collection, for instance.  This doesn't do macros, you will notice.
  It also can't be invoked when zloc is a zipper."
  [{:keys [width dbg? one-line?],
    {:keys [zstring zfirst zderef]} :zf,
    {:keys [object?]} :fn-obj,
    :as options} ind zloc]
  (if (and object? (object-str? (zstring zloc)))
    (fzprint-object options ind zloc)
    (let [l-str "#<"
          r-str ">"
          indent (count l-str)
          l-str-vec [[l-str (zcolor-map options :fn) :left]]
          r-str-vec (rstr-vec options (+ indent ind) zloc r-str :fn)
          arg-1-left "Fn@"
          arg-1-right (hash-identity-str zloc)
          arg-1-indent (+ ind indent 1 (count arg-1-left) (count arg-1-right))
          class-str (pr-str #?(:clj (class zloc)
                               :cljs (type zloc)))
          #?@(:clj [[class-name & more]
                    (s/split (s/replace-first class-str #"\$" "/") #"\$") color
                    (if (re-find #"clojure" class-name)
                      (zcolor-map options :fn)
                      :none) arg-2 (str class-name (when more "[fn]"))]
              :cljs [name-js (str (.-name zloc)) color
                     (if (or (re-find #"^clojure" name-js)
                             (re-find #"^cljs" name-js))
                       (zcolor-map options :fn)
                       :none) name-split (clojure.string/split name-js #"\$")
                     arg-2
                     (str (apply str (interpose "." (butlast name-split)))
                          "/"
                          (last name-split))])]
      (dbg-pr options
              "fzprint-fn-obj: arg-1:"
              arg-1-left
              arg-1-right
              "zstring arg-1:"
              (zstring zloc))
      (concat-no-nil l-str-vec
                     [[arg-1-left (zcolor-map options :fn) :element]]
                     [[arg-1-right (zcolor-map options :none) :element]]
                     (fzprint-hang-one :unknown
                                       (rightmost (assoc options
                                                    :string-str? true
                                                    :string-color color))
                                       arg-1-indent
                                       (+ indent ind)
                                       arg-2)
                     r-str-vec))))

(defn fzprint-ns
  [{:keys [width dbg? one-line?],
    {:keys [zseqnws zstring zfirst]} :zf,
    :as options} ind zloc]
  (let [l-str "#<"
        r-str ">"
        indent (count l-str)
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
        arg-1 "Namespace"
        arg-1-indent (+ ind indent 1 (count arg-1))]
    (dbg-pr options
            "fzprint-atom: arg-1:" arg-1
            "zstring arg-1:" (zstring zloc))
    (concat-no-nil l-str-vec
                   [[arg-1 (zcolor-map options :none) :element]]
                   (fzprint-hang-one :unknown
                                     (rightmost options)
                                     arg-1-indent
                                     (+ indent ind)
                                     (ns-name zloc))
                   r-str-vec)))

(defn fzprint-record
  [{:keys [width dbg? one-line?],
    {:keys [zseqnws zstring zfirst]} :zf,
    {:keys [record-type? to-string?]} :record,
    :as options} ind zloc]
  (if to-string?
    (fzprint* options ind (. zloc toString))
    (if-not record-type?
      ; if not printing as record-type, turn it into map
      (fzprint* options ind (into {} zloc))
      (let [l-str "#"
            r-str ""
            indent (count l-str)
            l-str-vec [[l-str (zcolor-map options l-str) :left]]
            r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
            arg-1 (pr-str #?(:clj (class zloc)
                             :cljs (type zloc)))
            arg-1 (let [tokens (clojure.string/split arg-1 #"\.")]
                    (apply str
                      (conj (into [] (interpose "." (butlast tokens)))
                            "/"
                            (last tokens))))
            arg-1-indent (+ ind indent 1 (count arg-1))]
        (dbg-pr options
                "fzprint-record: arg-1:" arg-1
                "zstring zloc:" (zstring zloc))
        (concat-no-nil
          l-str-vec
          [[arg-1 (zcolor-map options :none) :element]]
          (fzprint-hang-one :record
                            options
                            ;(rightmost options)
                            arg-1-indent
                            (+ indent ind)
                            ; this only works because
                            ; we never actually get here
                            ; with a zipper, just an sexpr
                            (into {} zloc))
          r-str-vec)))))

(defn fzprint-uneval
  "Trim the #_ off the front of the uneval, and try to print it."
  [{:keys [width dbg? one-line?],
    {:keys [zparseuneval zstring]} :zf,
    :as options} ind zloc]
  (let [l-str "#_"
        r-str ""
        indent (count l-str)
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
        uloc (zparseuneval zloc)
        #_(def zs (zstring zloc))
        #_(def un uloc)]
    (dbg-pr options
            "fzprint-uneval: zloc:" (zstring zloc)
            "uloc:" (zstring uloc))
    (concat-no-nil l-str-vec
                   (fzprint* (assoc options
                               :color-map (:color-map (:uneval options)))
                             (+ indent ind)
                             uloc)
                   r-str-vec)))

(defn fzprint-meta
  "Print the two items in a meta node.  Different because it doesn't print
  a single collection, so it doesn't do any indent or rightmost.  It also
  uses a different approach to calling fzprint-flow-seq with the
  results zmap, so that it prints all of the seq, not just the rightmost."
  [{:keys [width dbg? one-line?],
    {:keys [zstring zmap zcount]} :zf,
    :as options} ind zloc]
  (let [l-str "^"
        r-str ""
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options ind zloc r-str)]
    (dbg-pr options "fzprint-meta: zloc:" (zstring zloc))
    (concat-no-nil
      l-str-vec
      (fzprint-flow-seq
        ; No rightmost, because this isn't a collection.
        ; This is essentially two separate things.
        options
        ; no indent for second line, as the leading ^ is
        ; not a normal collection beginning
        ; TODO: change this to (+ (count l-str) ind)
        (apply vector (+ (count l-str) ind) (repeat (dec (zcount zloc)) ind))
        ;[(inc ind) ind]
        (zmap identity zloc))
      r-str-vec)))

(defn fzprint-reader-macro
  "Print a reader-macro, often a reader-conditional."
  [{:keys [width dbg? one-line?],
    {:keys [zstring zfirst zsecond ztag zcoll? zmap]} :zf,
    :as options} ind zloc]
  (let [reader-cond? (= (zstring (zfirst zloc)) "?")
        at? (= (ztag (zsecond zloc)) :deref)
        l-str (cond (and reader-cond? at?) "#?@"
                    (and reader-cond? (zcoll? (zsecond zloc))) "#?"
                    reader-cond?
                      (throw
                        (#?(:clj Exception.
                            :cljs js/Error.)
                         (str "Unknown reader macro: '" (zstring zloc)
                              "' zfirst zloc: " (zstring (zfirst zloc)))))
                    :else "#")
        r-str ""
        indent (count l-str)
        ; we may want to color this based on something other than
        ; its actual character string
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
        floc (if at? (zfirst (zsecond zloc)) (zsecond zloc))]
    (dbg-pr options
            "fzprint-reader-macro: zloc:" (zstring zloc)
            "floc:" (zstring floc))
    (concat-no-nil
      l-str-vec
      (if reader-cond?
        ; yes rightmost, this is a collection
        (fzprint-map* :reader-cond
                      "("
                      ")"
                      (rightmost options)
                      (+ indent ind)
                      floc)
        ; not reader-cond?
        (fzprint-flow-seq options (+ indent ind) (zmap identity zloc)))
      r-str-vec)))

(defn fzprint-prefix*
  "Print the single item after a variety of prefix characters."
  [{:keys [width dbg? one-line?],
    {:keys [zstring zfirst zsecond]} :zf,
    :as options} ind zloc l-str]
  (let [r-str ""
        indent (count l-str)
        ; Since this is a single item, no point in figure an indent
        ; based on the l-str length."
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
        floc (zfirst zloc)
        #_(def zqs (zstring zloc))
        #_(def qun floc)]
    (dbg-pr options
            "fzprint-prefix*: zloc:" (zstring zloc)
            "floc:" (zstring floc))
    (concat-no-nil l-str-vec
                   ; no rightmost, as we don't know if this is a collection
                   (fzprint* options (+ indent ind) floc)
                   r-str-vec)))


(def prefix-tags
  {:quote "'",
   :syntax-quote "`",
   :unquote "~",
   :unquote-splicing "~@",
   :deref "@",
   :var "#'",
   :uneval "#_"})

(defn prefix-options
  "Change options as necessary based on prefix tag."
  [options prefix-tag]
  (cond (= prefix-tag :uneval) (assoc options
                                 :color-map (:color-map (:uneval options)))
        :else options))

;; Fix fzprint* to look at cursor to see if there is one, and
;; fzprint to set cursor with binding.  If this works, might pass
;; it around.  Maybe pass ctx to everyone and they can look at it
;; or something.  But for testing, let's just do this.

;;
;; # The center of the zprint universe
;;
;; Looked into alternative ways to dispatch this, but at the end of
;; the day, this looked like the best.
;;

(defn fzprint*
  "The pretty print part of fzprint."
  [{:keys [width rightcnt fn-map hex? shift-seq dbg? dbg-print? in-hang?
           one-line? string-str? string-color depth max-depth],
    {:keys [zfind-path zlist? zvector? zmap? zset? zanonfn? zfn-obj? zuneval?
            zwhitespace? zstring zcomment? zsexpr zcoll? zarray? zexpandarray
            zatom? znumstr zrecord? zns? zmeta? ztag znewline?
            zwhitespaceorcomment? zpromise? zfuture? zreader-macro? zdelay?
            zagent? zarray-to-shift-seq zdotdotdot]}
      :zf,
    :as options} indent zloc]
  (let [avail (- width indent)
        ; note that depth affects how comments are printed, toward the end
        options (assoc options :depth (inc depth))
        options (if (or dbg? dbg-print?)
                  (assoc options
                    :dbg-indent (str
                                  (get options :dbg-indent "")
                                  (cond one-line? "o" in-hang? "h" :else ".")))
                  options)
        _ (dbg options
               "fzprint* **** rightcnt:"
               rightcnt
               (pr-str (zstring zloc)))
        dbg-data @fzprint-dbg
        dbg-focus? (and dbg? (= dbg-data (second (zfind-path zloc))))
        options (if dbg-focus? (assoc options :dbg :on) options)
        _ (if dbg-focus? (println "fzprint dbg-data:" dbg-data))]
    #_(def zlocx zloc)
    (cond (and (> depth max-depth) (zcoll? zloc))
            (if (= zloc (zdotdotdot))
              [["..." (zcolor-map options :none) :element]]
              [["##" (zcolor-map options :keyword) :element]])
          (zrecord? zloc) (fzprint-record options indent zloc)
          (zlist? zloc) (fzprint-list options indent zloc)
          (zvector? zloc) (fzprint-vec options indent zloc)
          (zmap? zloc) (fzprint-map options indent zloc)
          (zset? zloc) (fzprint-set options indent zloc)
          (zanonfn? zloc) (fzprint-anon-fn options indent zloc)
          (zfn-obj? zloc) (fzprint-fn-obj options indent zloc)
          (zarray? zloc) (if (:object? (:array options))
                           (fzprint-object options indent zloc)
                           (fzprint-array
                             #?(:clj (if (:hex? (:array options))
                                       (assoc options
                                         :hex? (:hex? (:array options))
                                         :shift-seq (zarray-to-shift-seq zloc))
                                       options)
                                :cljs options)
                             indent
                             (zexpandarray zloc)))
          (zatom? zloc) (fzprint-atom options indent zloc)
          (zmeta? zloc) (fzprint-meta options indent zloc)
          (prefix-tags (ztag zloc)) (fzprint-prefix*
                                      (prefix-options options (ztag zloc))
                                      indent
                                      zloc
                                      (prefix-tags (ztag zloc)))
          (zns? zloc) (fzprint-ns options indent zloc)
          (or (zpromise? zloc) (zfuture? zloc) (zdelay? zloc) (zagent? zloc))
            (fzprint-future-promise-delay-agent options indent zloc)
          (zreader-macro? zloc) (fzprint-reader-macro options indent zloc)
          :else
            (let [zstr (zstring zloc)
                  overflow-in-hang?
                    (and in-hang?
                         (> (+ (count zstr) indent (or rightcnt 0)) width))]
              (cond
                (zcomment? zloc)
                  (let [zcomment
                          ; Do we have a file-level comment?
                          (if (zero? depth)
                            zstr
                            (clojure.string/replace zstr "\n" ""))]
                    (if (and (:count? (:comment options)) overflow-in-hang?)
                      (do (dbg options "fzprint*: overflow comment ========")
                          nil)
                      [[zcomment (zcolor-map options :comment) :comment]]))
                ; Really just testing for whitespace, comments filtered above
                (zwhitespaceorcomment? zloc) [[zstr :none :whitespace]]
                ; At this point, having filtered out whitespace and
                ; comments above, now we expect zsexpr will work for all of
                ; the remaining things.
                ;
                ; If we are going to overflow, and we are doing a hang, let's
                ; stop now!
                overflow-in-hang?
                  (do (dbg options "fzprint*: overflow <<<<<<<<<<") nil)
                (string? (zsexpr zloc))
                  [[(if string-str?
                      (str (zsexpr zloc))
                      ; zstr
                      (zstring zloc))
                    (if string-color string-color (zcolor-map options :string))
                    :element]]
                (keyword? (zsexpr zloc)) [[zstr (zcolor-map options :keyword)
                                           :element]]
                (showfn? fn-map (zsexpr zloc)) [[zstr (zcolor-map options :fn)
                                                 :element]]
                (show-user-fn? options (zsexpr zloc))
                  [[zstr (zcolor-map options :user-fn) :element]]
                (number? (zsexpr zloc))
                  [[(if hex? (znumstr zloc hex? shift-seq) zstr)
                    (zcolor-map options :number) :element]]
                (nil? (zsexpr zloc)) [[zstr (zcolor-map options :nil) :element]]
                :else [[zstr (zcolor-map options :none) :element]])))))

;;
;; # Comment Wrap Support
;;

(defn last-space
  "Take a string and an index, and look for the last space prior to the
  index. If we wanted to tie ourselves to 1.8, we could use 
  clojure.string/last-index-of, but we don't.  However, we use similar
  conventions, i.e., if no space is found, return nil, and if the index
  is a space return that value, and accept any from-index, including one
  larger than the length of the string."
  [s from-index]
  (let [from-index (min (dec (count s)) from-index)
        rev-seq (reverse (take (inc from-index) s))
        seq-after-space (take-while #(not= % \space) rev-seq)
        space-index (- from-index (count seq-after-space))]
    (if (neg? space-index) nil space-index)))

(defn next-space
  "Take a string and an index, and look for the next space *after* the
  index. If no space is found, return nil. Accept any from-index, 
  including one larger than the length of the string."
  [s from-index]
  (let [from-index (inc from-index)]
    (when (< from-index (count s))
      (let [seq-after-space (take-while #(not= % \space)
                                        (drop from-index (seq s)))
            space-index (+ from-index (count seq-after-space))]
        (if (>= space-index (count s)) nil space-index)))))

(defn wrap-comment
  "If this is a comment, and it is too long, word wrap it to the right width.
  Note that top level comments may well end with a newline, so remove it
  and reapply it at the end if that is the case."
  [width [s color stype :as element] start]
  (if-not (= stype :comment)
    element
    (let [comment-width (- width start)
          semi-str (re-find #";*" s)
          rest-str (subs s (count semi-str))
          space-str (re-find #" *" rest-str)
          rest-str (subs rest-str (count space-str))
          newline? (re-find #"\n$" s)
          comment-width (- comment-width (count semi-str) (count space-str))
          #_(println "\ncomment-width:" comment-width
                     "semi-str:" semi-str
                     "space-str:" space-str
                     "rest-str:" rest-str)]
      (loop [comment-str rest-str
             out []]
        #_(prn "comment-str:" comment-str)
        (if (empty? comment-str)
          (if (empty? out)
            (if newline?
              [[semi-str color stype] ["\n" :none :whitespace]]
              [[semi-str color stype]])
            (if newline? (conj out ["\n" :none :whitespace]) out))
          (let [last-space-index (if (<= (count comment-str) comment-width)
                                   (dec (count comment-str))
                                   (if (<= comment-width 0)
                                     (or (next-space comment-str 0)
                                         (dec (count comment-str)))
                                     (or (last-space comment-str comment-width)
                                         (next-space comment-str comment-width)
                                         (dec (count comment-str)))))
                ;        (dec comment-width)))
                next-comment (clojure.string/trimr
                               (subs comment-str 0 (inc last-space-index)))]
            #_(prn "last-space-index:" last-space-index
                   "next-comment:" next-comment)
            (recur
              (subs comment-str (inc last-space-index))
              (if (empty? out)
                (conj out [(str semi-str space-str next-comment) color stype])
                (conj out
                      [(str "\n" (blanks start)) :none :whitespace]
                      [(str semi-str space-str next-comment) color
                       stype])))))))))
            
(defn loc-vec
  "Takes the start of this vector and the vector itself."
  [start [s]]
  (let [split (clojure.string/split s #"\n")]
    (if (= (count split) 1) (+ start (count s)) (count (last split)))))

(defn style-loc-vec
  "Take a style-vec and produce a style-loc-vec with the starting column
  of each element in the style-vec."
  [style-vec]
  (butlast (reductions loc-vec 0 style-vec)))

(defn lift-vec
  "Take a output vector and a vector and lift any style-vec elements
  out of the input vector."
  [out-vec element]
  (if (string? (first element))
    (conj out-vec element)
    (loop [element-vec element
           out out-vec]
      (if-not element-vec
        out
        (recur (next element-vec) (conj out (first element-vec)))))))

(defn lift-style-vec
  "Take a style-vec [[s color type] [s color type] [[s color type]
  [s color type]] [s color type] ...] and lift out the inner vectors."
  [style-vec]
  (reduce lift-vec [] style-vec))
    
(defn fzprint-wrap-comments
  "Take the final output style-vec, and wrap any comments which run over
  the width. Looking for "
  [{:keys [width dbg?], :as options} style-vec]
  #_(def wcsv style-vec)
  (let [start-col (style-loc-vec style-vec)
        #_(def stc start-col)
        sv (pr-str style-vec)
        _ (dbg options "fzprint-wrap-comments: style-vec:" sv)
        _ (dbg options "fzprint-wrap-comments: start-col:" start-col)
        wrap-style-vec (mapv (partial wrap-comment width) style-vec start-col)
        #_(def wsv wrap-style-vec)
        sv (pr-str wrap-style-vec)
        _ (dbg options "fzprint-wrap-comments: wrap:" sv)
        out-style-vec (lift-style-vec wrap-style-vec)]
    out-style-vec))

;;
;; # External interface to all fzprint functions
;;

(defn fzprint
  "The pretty print part of fzprint."
  [options indent zloc]
  #_(def opt options)
  ; if we are doing specs, find the docstring and modify it with
  ; the spec output.
  #_(println "fn-name:" (:fn-name options))
  #_(println "spec:" (:value (:spec options)))
  (let [zloc (if-not (and (= (:ztype options) :zipper) (:value (:spec options)))
               zloc
               (add-spec-to-docstring zloc (:value (:spec options))))
        style-vec (fzprint* (assoc options :depth 0) indent zloc)]
    (if (= (:ztype options) :sexpr)
      style-vec
      (if (:wrap? (:comment options))
        (fzprint-wrap-comments options style-vec)
        style-vec))))



;;
;; # Basic functions for testing results
;;

(defn line-count "Count lines in a string." [s] (inc (count (re-seq #"\n" s))))

(defn line-widths
  "Return a vector the lengths of lines."
  [s]
  (map count (clojure.string/split s #"\n")))

(defn max-width
  "Split a string into lines, and figure the max width."
  [s]
  (reduce max (line-widths s)))

;;
;; # Tab Expansion
;;

(defn expand-tabs
  "Takes a string, and expands tabs inside of the string based
  on a tab-size argument."
  ([tab-size s]
   (apply str
     (loop [char-seq (seq s)
            cur-len 0
            out []]
       (if (empty? char-seq)
         out
         (let [this-char (first char-seq)
               tab-expansion (if (= this-char \tab)
                               (- tab-size (mod cur-len tab-size))
                               nil)]
           (recur (rest char-seq)
                  (cond (= char \newline) 0
                        tab-expansion (+ cur-len tab-expansion)
                        :else (inc cur-len))
                  (if tab-expansion
                    (apply conj out (seq (blanks tab-expansion)))
                    (conj out this-char))))))))
  ([s] (expand-tabs 8 s)))

;;
;; # Needed for expectations testing
;;
;; Seems defrecord doesn't work in test environment, which is pretty odd.
;;

(defrecord r [left right])
(defn make-record [l r] (new r l r))

;;
;; End of testing functions
;;