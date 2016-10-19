(ns
  zprint.zprint-test
  (:require
   [expectations :refer :all]
   [zprint.core :refer :all]
   [zprint.core-test :refer :all]
   [zprint.zprint :refer :all]
   [zprint.finish :refer :all]
   [clojure.repl :refer :all]
   [clojure.string :as str]
   [rewrite-clj.parser :as p :only [parse-string parse-string-all]]
   [rewrite-clj.node :as n]
   [rewrite-clj.zip :as z :only [edn*]]))

;; Keep some of the test on wrapping so they still work
;!zprint {:comment {:wrap? false}}

;;
;; # Pretty Tests
;;
;; See if things print the way they are supposed to.
;;

; 
; Test with size of 20
;
; with and without do
;

(def iftestdo
  '(if (and :abcd :efbg) (do (list xxx yyy zzz) (list ccc ddd eee))))

(def iftest '(if (and :abcd :efbg) (list xxx yyy zzz) (list ccc ddd eee)))

(def iftest-19-str
  "(if (and :abcd\n         :efbg)\n  (list xxx\n        yyy\n        zzz)\n  (list ccc\n        ddd\n        eee))")

(def iftest-20-str
  "(if (and :abcd\n         :efbg)\n  (list xxx yyy zzz)\n  (list ccc\n        ddd\n        eee))")

(def iftest-21-str
  "(if (and :abcd :efbg)\n  (list xxx yyy zzz)\n  (list ccc ddd eee))")

(expect iftest-19-str (zprint-str iftest 19 {:list {:constant-pair? nil}}))

(defn lines "Turn a string into lines." [s] (str/split s #"\n"))

(expect (lines iftest-20-str) (lines (zprint-str iftest 20)))
(expect iftest-21-str (zprint-str iftest 21))

;;
;; Another couple of fidelity tests of two of our actual functions
;;

(def y1 (source-fn 'fzprint-map-two-up))
(expect (read-string y1) (read-string (zprint-str y1 {:parse-string? true})))

(def y2 (source-fn 'partition-all-2-nc))
(expect (trim-gensym (read-string y2))
        (trim-gensym (read-string (zprint-str y2 {:parse-string? true}))))

;;
;; Check out line count
;;

(expect 1 (line-count "abc"))

(expect 2 (line-count "abc\ndef"))

(expect 3 (line-count "abc\ndef\nghi"))


(def r {:aaaa :bbbb, :cccc '({:eeee :ffff, :aaaa :bbbb, :cccccccc :dddddddd})})

(def r-str (pr-str r))

;;
;; r should take 29 characters
;;{:aaaa :bbbb,
;; :cccc ({:aaaa :bbbb,
;;         :cccccccc :dddddddd,
;;         :eeee :ffff})}
;;
;; The comma at the end of the :dddd should be in
;; column 29.  So this should fit with a width of 29, but not 28.
;;
;; Check both sexpression printing and parsing into a zipper printing.
;;

(expect 29 (max-width (zprint-str r 30)))
(expect 4 (line-count (zprint-str r 30)))

(expect 29 (max-width (zprint-str r 29)))
(expect 4 (line-count (zprint-str r 29)))

(expect 25 (max-width (zprint-str r 28 {:map {:hang-adjust 0}})))
(expect 23 (max-width (zprint-str r 28 {:map {:hang-adjust -1}})))
(expect 5 (line-count (zprint-str r 28 {:map {:hang-adjust 0}})))

(expect 29 (max-width (zprint-str r-str 30 {:parse-string? true})))
(expect 4 (line-count (zprint-str r-str 30 {:parse-string? true})))

(expect 29 (max-width (zprint-str r-str 29 {:parse-string? true})))
(expect 4 (line-count (zprint-str r-str 29 {:parse-string? true})))

(expect 25
        (max-width
          (zprint-str r-str 28 {:parse-string? true, :map {:hang-adjust 0}})))
(expect 23
        (max-width
          (zprint-str r-str 28 {:parse-string? true, :map {:hang-adjust -1}})))
(expect 5
        (line-count
          (zprint-str r-str 28 {:parse-string? true, :map {:hang-adjust 0}})))

(def t {:cccc '({:dddd :eeee, :fffffffff :ggggggggggg}), :aaaa :bbbb})

(def t-str (pr-str t))

;;
;;{:aaaa :bbbb,
;; :cccc ({:dddd :eeee,
;;         :fffffffff
;;           :ggggggggggg})}
;;
;; This takes 26 characters, and shouldn't fit on a 25
;; character line.

(expect 26 (max-width (zprint-str t 26)))
(expect 4 (line-count (zprint-str t 26)))

(expect 22 (max-width (zprint-str t 25)))
(expect 5 (line-count (zprint-str t 25)))

(expect 26 (max-width (zprint-str t-str 26 {:parse-string? true})))
(expect 4 (line-count (zprint-str t-str 26 {:parse-string? true})))

(expect 22 (max-width (zprint-str t-str 25 {:parse-string? true})))
(expect 5 (line-count (zprint-str t-str 25 {:parse-string? true})))

;;
;; Test line-lengths
;;

(def ll
  [["{" :red :left] [":aaaa" :purple :element] [" " :none :whitespace]
   [":bbbb" :purple :element] [",\n        " :none :whitespace]
   [":ccccccccccc" :purple :element] ["\n          " :none :whitespace]
   [":ddddddddddddd" :purple :element] [",\n        " :none :whitespace]
   [":eeee" :purple :element] [" " :none :whitespace] [":ffff" :purple :element]
   ["}" :red :right]])

(expect [20 20 25 20] (line-lengths {} 7 ll))

(def u
  {:aaaa :bbbb, :cccc {:eeee :ffff, :aaaa :bbbb, :ccccccccccc :ddddddddddddd}})

(def u-str (pr-str u))

;;
;;{:aaaa :bbbb,
;; :cccc {:aaaa :bbbb,
;;        :ccccccccccc
;;          :ddddddddddddd,
;;        :eeee :ffff}}
;;
;; This takes 25 characters and shouldn't fit ona  24 character line
;;

(expect 25 (max-width (zprint-str u 25)))
(expect 5 (line-count (zprint-str u 25)))

(expect 21 (max-width (zprint-str u 24)))
(expect 6 (line-count (zprint-str u 24)))

(expect 25 (max-width (zprint-str u-str 25 {:parse-string? true})))
(expect 5 (line-count (zprint-str u-str 25 {:parse-string? true})))

(expect 21 (max-width (zprint-str u-str 24 {:parse-string? true})))
(expect 6 (line-count (zprint-str u-str 24 {:parse-string? true})))

(def d
  '(+ :aaaaaaaaaa
      (if :bbbbbbbbbb
        :cccccccccc
        (list :ddddddddd
              :eeeeeeeeee :ffffffffff
              :gggggggggg :hhhhhhhhhh
              :iiiiiiiiii :jjjjjjjjjj
              :kkkkkkkkkk :llllllllll
              :mmmmmmmmmm :nnnnnnnnnn
              :oooooooooo :pppppppppp))))

(def d-str (pr-str d))

(expect 16 (line-count (zprint-str d 80 {:list {:constant-pair? nil}})))
(expect
  16
  (line-count
    (zprint-str d-str 80 {:parse-string? true, :list {:constant-pair? nil}})))

(def e {:aaaa :bbbb, :cccc :dddd, :eeee :ffff})
(def e-str (pr-str e))

(expect 39 (max-width (zprint-str e 39)))
(expect 1 (line-count (zprint-str e 39)))
(expect 13 (max-width (zprint-str e 38)))
(expect 3 (line-count (zprint-str e 38)))

(expect 39 (max-width (zprint-str e-str 39 {:parse-string? true})))
(expect 1 (line-count (zprint-str e-str 39 {:parse-string? true})))
(expect 13 (max-width (zprint-str e-str 38 {:parse-string? true})))
(expect 3 (line-count (zprint-str e-str 38 {:parse-string? true})))

(expect {:a nil} (read-string (zprint-str {:a nil})))

;;
;; Check out zero argument functions
;;

(expect 3 (max-width (zprint-str '(+))))

;;
;; Printing atoms
;;

(def atm (atom {:a :b}))

(expect (str "#<Atom " @atm ">")
        (let [result (zprint-str atm)]
          (clojure.string/replace result (re-find #"@[a-fA-F0-9]+" result) "")))

;;
;; Atom printing -- prints the contents of the atom
;; in a pretty way too.
;;

(def atm2 (atom u))

(expect 5 (line-count (zprint-str atm2 48)))
(expect 1 (line-count (pr-str atm2)))

;;
;; Byte arrays
;;

(def ba (byte-array [1 2 3 4 -128]))

(expect "[01 02 03 04 80]" (zprint-str ba {:array {:hex? true}}))

(def ba1
  (byte-array [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25
               26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47
               48 49 50]))

(expect 51 (max-width (zprint-str ba1 51 {:array {:hex? true}})))
(expect 3 (line-count (zprint-str ba1 51 {:array {:hex? true}})))



;;
;; # Rightmost tests
;;

(def atm1 (atom [:aaaaaaaaa :bbbbbbbbb {:a :b}]))
(def atm1-length (count (zprint-str atm1)))
(expect 1 (line-count (zprint-str atm1 (inc atm1-length))))
(expect 1 (line-count (zprint-str atm1 atm1-length)))
(expect 2 (line-count (zprint-str atm1 (dec atm1-length))))

(def map1 {:abc :def, :hij :klm, "key" "value", :x :y})
(expect 1 (line-count (zprint-str map1 45)))
(expect 1 (line-count (zprint-str map1 44)))
(expect 4 (line-count (zprint-str map1 43)))
;
; Test zipper version
;
(def maps (str map1))
(expect 1 (line-count (zprint-str maps 45 {:parse-string? true})))
(expect 1 (line-count (zprint-str maps 44 {:parse-string? true})))
(expect 4 (line-count (zprint-str maps 43 {:parse-string? true})))


(def vec1 [:asdf :jklx 1 2 3 4 5 "abc" "def"])
(expect 1 (line-count (zprint-str vec1 36)))
(expect 1 (line-count (zprint-str vec1 35)))
(expect 2 (line-count (zprint-str vec1 34)))
;
; Test zipper version
;
(def vecs (str vec1))
(expect 1 (line-count (zprint-str vecs 36 {:parse-string? true})))
(expect 1 (line-count (zprint-str vecs 35 {:parse-string? true})))
(expect 2 (line-count (zprint-str vecs 34 {:parse-string? true})))

(def lis1 '(asdf jklsemi :aaa :bbb :ccc 1 2 3 4 5 "hello"))
(expect 1 (line-count (zprint-str lis1 48)))
(expect 1 (line-count (zprint-str lis1 47)))
(expect 10 (line-count (zprint-str lis1 46 {:list {:constant-pair? nil}})))
(expect 6 (line-count (zprint-str lis1 46 {:list {:constant-pair? true}})))
;
; Test zipper version
;
(def liss (str lis1))
(expect 1 (line-count (zprint-str liss 48 {:parse-string? true})))
(expect 1 (line-count (zprint-str liss 47 {:parse-string? true})))
(expect
  10
  (line-count
    (zprint-str liss 46 {:parse-string? true, :list {:constant-pair? nil}})))
(expect
  6
  (line-count
    (zprint-str liss 46 {:parse-string? true, :list {:constant-pair? true}})))

(def lis2 '(aaaa bbbb cccc (dddd eeee ffff)))
(expect 1 (line-count (zprint-str lis2 34)))
(expect 1 (line-count (zprint-str lis2 33)))
(expect 3 (line-count (zprint-str lis2 32)))

(def set1 #{:aaa :bbb :ccc :ddd "stuff" "bother" 1 2 3 4 5})
(expect 1 (line-count (zprint-str set1 50)))
(expect 1 (line-count (zprint-str set1 49)))
(expect 2 (line-count (zprint-str set1 48)))

(def set2 #{{:a :b} {:c :d} {:e :f} {:g :h}})
(expect 1 (line-count (zprint-str set2 35)))
(expect 1 (line-count (zprint-str set2 34)))
(expect 2 (line-count (zprint-str set2 33)))
;
; Test zipper version
;
(def sets (str set2))
(expect 1 (line-count (zprint-str sets 35 {:parse-string? true})))
(expect 1 (line-count (zprint-str sets 34 {:parse-string? true})))
(expect 2 (line-count (zprint-str sets 33 {:parse-string? true})))

(def rs (make-record :reallylongleft :r))
(expect 1 (line-count (zprint-str rs 53)))
(expect 1 (line-count (zprint-str rs 52)))
(expect 1 (line-count (zprint-str rs 51)))
(expect 2 (line-count (zprint-str rs 50)))
(expect 2 (line-count (zprint-str rs 49)))

;;
;; Lest these look like "of course" tests, remember that
;; the zprint functions can parse a string into a zipper and then
;; print it, so these are all getting parsed and then handled by
;; the zipper code.
;;

(expect "()" (zprint-str "()" {:parse-string? true}))
(expect "[]" (zprint-str "[]" {:parse-string? true}))
(expect "{}" (zprint-str "{}" {:parse-string? true}))
(expect "#()" (zprint-str "#()" {:parse-string? true}))
(expect "#{}" (zprint-str "#{}" {:parse-string? true}))
(expect "#_()" (zprint-str "#_()" {:parse-string? true}))
(expect "#_[]" (zprint-str "#_[]" {:parse-string? true}))
(expect "~{}" (zprint-str "~{}" {:parse-string? true}))
(expect "^{:a :b} stuff" (zprint-str "^{:a :b} stuff" {:parse-string? true}))

;;
;; # Constant pairing
;;

(expect
  "(if (and\n      :abcd :efbg)\n  (list xxx\n        yyy\n        zzz)\n  (list ccc\n        ddd\n        eee))"
  (zprint-str iftest
              19
              {:list {:constant-pair-min 2, :constant-pair? true},
               :tuning {:general-hang-adjust 0}}))

(expect
  "(if (and :abcd\n         :efbg)\n  (list xxx\n        yyy\n        zzz)\n  (list ccc\n        ddd\n        eee))"
  (zprint-str iftest 19 {:list {:constant-pair-min 4, :constant-pair? true}}))

(expect
  6
  (line-count
    (zprint-str
      "(println :aaaa :bbbb :cccc :dddd :eeee :ffff 
                :gggg :hhhh :iiii :jjjj :kkkk)"
      50
      {:parse-string? true, :list {:constant-pair? true}})))

(expect
  11
  (line-count
    (zprint-str
      "(println :aaaa :bbbb :cccc :dddd :eeee :ffff 
                :gggg :hhhh :iiii :jjjj :kkkk)"
      50
      {:parse-string? true, :list {:constant-pair? nil}})))

(expect "{:a :b, :c nil}" (zprint-str "{:a :b :c nil}" {:parse-string? true}))

;;
;; # Line Lengths
;;

(expect [3 0] (zprint.zprint/line-lengths {} 3 [["; stuff" :none :comment]]))

(expect
  [14 30 20]
  (zprint.zprint/line-lengths
    {}
    12
    [[":c" :magenta :element] ["\n            " :none :whitespace]
     ["(" :green :left] ["identity" :blue :element] [" " :none :whitespace]
     ["\"stuff\"" :red :element] [")" :green :right]
     ["\n            " :none :whitespace] ["\"bother\"" :red :element]]))

(expect
  [2 30 20]
  (zprint.zprint/line-lengths
    {}
    0
    [[":c" :magenta :element] ["\n            " :none :whitespace]
     ["(" :green :left] ["identity" :blue :element] [" " :none :whitespace]
     ["\"stuff\"" :red :element] [")" :green :right]
     ["\n            " :none :whitespace] ["\"bother\"" :red :element]]))

(expect
  [12 30 20]
  (zprint.zprint/line-lengths
    {}
    12
    [[";" :green :comment] ["\n            " :none :whitespace]
     ["(" :green :left] ["identity" :blue :element] [" " :none :whitespace]
     ["\"stuff\"" :red :element] [")" :green :right]
     ["\n            " :none :whitespace] ["\"bother\"" :red :element]]))

(expect "(;a\n list\n :b\n :c\n ;def\n  )"
        (zprint-str "(;a\nlist\n:b\n:c ;def\n)" {:parse-string? true}))

(expect
  [6 1 8 1 9 1 11]
  (zprint.zprint/line-lengths
    {}
    0
    [["(" :green :left] ["cond" :blue :element] [" " :none :whitespace]
     ["; one" :green :comment] [" " :none :whitespace]
     ["; two   " :green :comment] [" " :none :whitespace]
     [":stuff" :magenta :element] [" " :none :whitespace]
     ["; middle" :green :comment] [" " :none :whitespace]
     ["; second middle" :green :comment] [" " :none :whitespace]
     [":bother" :magenta :element] [" " :none :whitespace]
     ["; three" :green :comment] [" " :none :whitespace]
     ["; four" :green :comment] [" " :none :whitespace]
     [":else" :magenta :element] [" " :none :whitespace]
     ["nil" :yellow :element] [")" :green :right]]))

(expect
  [1 1 1 1 16]
  (zprint.zprint/line-lengths
    {}
    0
    [["[" :purple :left] [";a" :green :comment] [" " :none :whitespace]
     [";" :green :comment] [" " :none :whitespace] [";b" :green :comment]
     [" " :none :whitespace] [";c" :green :comment] ["\n " :none :whitespace]
     ["this" :black :element] [" " :none :whitespace] ["is" :black :element]
     [" " :none :whitespace] ["a" :black :element] [" " :none :whitespace]
     ["test" :blue :element] ["]" :purple :right]]))

 
;;
;; # Comments handling
;;

(expect "[;a\n ;\n ;b\n ;c\n this is a test]"
        (zprint-str "[;a\n;\n;b\n;c\nthis is a test]" {:parse-string? true}))

(expect
  "(defn testfn8\n  \"Test two comment lines after a cond test.\"\n  [x]\n  (cond\n    ; one\n    ; two\n    :stuff\n      ; middle\n      ; second middle\n      :bother\n    ; three\n    ; four\n    :else nil))"
  (zprint-fn-str zprint.core-test/testfn8 {:pair-fn {:hang? nil}}))

(defn zctest3
  "Test comment forcing things"
  [x]
  (cond (and (list ;
                   (identity "stuff")
                   "bother"))
          x
        :else (or :a :b :c)))

(defn zctest4
  "Test comment forcing things"
  [x]
  (cond (and (list :c (identity "stuff") "bother")) x :else (or :a :b :c)))

(defn zctest5
  "Model defn issue."
  [x]
  (let [abade :b
        ceered (let [b :d]
                 (if (:a x)
                   ; this is a very long comment that should force things way to the left
                   (assoc b :a :c)))]
    (list :a
          (with-meta name x)
          ; a short comment that might be long if we wanted it to be
          :c)))


(expect
  "(defn zctest4\n  \"Test comment forcing things\"\n  [x]\n  (cond\n    (and (list :c\n               (identity \"stuff\")\n               \"bother\"))\n      x\n    :else (or :a :b :c)))"
  (zprint-fn-str zprint.zprint-test/zctest4 40 {:pair-fn {:hang? nil}}))

(expect
  "(defn zctest3\n  \"Test comment forcing things\"\n  [x]\n  (cond\n    (and (list ;\n               (identity \"stuff\")\n               \"bother\"))\n      x\n    :else (or :a :b :c)))"
  (zprint-fn-str zprint.zprint-test/zctest3 40 {:pair-fn {:hang? nil}}))

(expect
  "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let \n    [abade :b\n     ceered\n       (let [b :d]\n         (if (:a x)\n           ; this is a very long comment that should force things way\n           ; to the left\n           (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5
                 70
                 {:comment {:count? true, :wrap? true}}))

(expect
  "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a very long comment that should force\n                   ; things way to the left\n                   (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5
                 70
                 {:comment {:count? nil, :wrap? true}}))

(expect
  "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a very long comment that should force things way to the left\n                   (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5
                 70
                 {:comment {:wrap? nil, :count? nil}}))

;;
;; # Tab Expansion
;;

(expect "this is a tab   test to see if it works"
        (zprint.zprint/expand-tabs 8 "this is a tab\ttest to see if it works"))

(expect "this is a taba  test to see if it works"
        (zprint.zprint/expand-tabs 8 "this is a taba\ttest to see if it works"))

(expect "this is a tababc        test to see if it works"
        (zprint.zprint/expand-tabs 8
                                   "this is a tababc\ttest to see if it works"))

(expect "this is a tabab test to see if it works"
        (zprint.zprint/expand-tabs 8
                                   "this is a tabab\ttest to see if it works"))

;;
;; # File Handling
;;

(expect ["\n\n" ";;stuff\n" "(list :a :b)" "\n\n"]
        (zprint.zutil/zmap-all (partial zprint-str-internal {:zipper? true})
                               (z/edn* (p/parse-string-all
                                         "\n\n;;stuff\n(list :a :b)\n\n"))))

;;
;; #Deref
;;

(expect
  "@(list this\n       is\n       a\n       test\n       this\n       is\n       only\n       a\n       test)"
  (zprint-str "@(list this is a test this is only a test)"
              30
              {:parse-string? true}))

;;
;; # Reader Conditionals
;;

(expect "#?(:clj (list :a :b)\n   :cljs (list :c :d))"
        (zprint-str "#?(:clj (list :a :b) :cljs (list :c :d))"
                    30
                    {:parse-string? true}))

           
(expect "#?@(:clj (list :a :b)\n    :cljs (list :c :d))"
        (zprint-str "#?@(:clj (list :a :b) :cljs (list :c :d))"
                    30
                    {:parse-string? true}))

;;
;; # Var
;;

(expect "#'(list this\n        is\n        :a\n        \"test\")"
        (zprint-str "#'(list this is :a \"test\")" 20 {:parse-string? true}))

;;
;; # Ordered Options in maps
;;

(expect "{:a :test, :second :more-value, :should-be-first :value, :this :is}"
        (zprint-str
          {:this :is, :a :test, :should-be-first :value, :second :more-value}))

(expect "{:should-be-first :value, :second :more-value, :a :test, :this :is}"
        (zprint-str
          {:this :is, :a :test, :should-be-first :value, :second :more-value}
          {:map {:key-order [:should-be-first :second]}}))

;;
;; # Ordered Options in reader-conditionals
;;

(expect "#?(:clj (list :c :d), :cljs (list :a :b))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true, :reader-cond {:sort? true}}))

(expect "#?(:cljs (list :a :b), :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true, :reader-cond {:sort? nil}}))

(expect "#?(:cljs (list :a :b), :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true,
                     :reader-cond {:sort? nil, :key-order [:clj :cljs]}}))

(expect "#?(:clj (list :c :d), :cljs (list :a :b))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true,
                     :reader-cond {:sort? true, :key-order [:clj :cljs]}}))

;;
;; # Rightmost in reader conditionals
;;

(expect "#?(:cljs (list :a :b)\n   :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    40
                    {:parse-string? true}))

(expect "#?(:cljs (list :a :b), :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    41
                    {:parse-string? true}))

;;
;; # Reader Literals
;;

(expect "#stuff/bother\n (list :this\n       \"is\"\n       a\n       :test)"
        (zprint-str "#stuff/bother (list :this \"is\" a :test)"
                    20
                    {:parse-string? true}))

(expect "#stuff/bother (list :this \"is\" a :test)"
        (zprint-str "#stuff/bother (list :this \"is\" a :test)"
                    {:parse-string? true}))

;;
;; # The third cond clause in fzprint-two-up
;;

(expect
  "(let \n  [:a\n     ;x\n     ;y\n     :b\n   :c :d]\n  (println\n    :a))"
  (zprint-str "(let [:a ;x\n;y\n :b :c :d] (println :a))"
              10
              {:parse-string? true})) 

;;
;; # Promise, Future, Delay
;;

(def dd (delay [:d :e]))

(expect "#<Delay pending>"
        (clojure.string/replace (zprint-str dd) #"\@[0-9a-f]*" ""))

(def ee (delay [:d :e]))
(def ff @ee)

(expect "#<Delay [:d :e]>"
        (clojure.string/replace (zprint-str ee) #"\@[0-9a-f]*" ""))

(def pp (promise))

(expect "#<Promise not-delivered>"
        (clojure.string/replace (zprint-str pp) #"\@[0-9a-f]*" ""))

(def qq (promise))
(deliver qq [:a :b])

(expect "#<Promise [:a :b]>"
        (clojure.string/replace (zprint-str qq) #"\@[0-9a-f]*" ""))

(def ff (future [:f :g]))

(Thread/sleep 500)

(expect "#<Future [:f :g]>"
        (clojure.string/replace (zprint-str ff) #"\@[0-9a-f]*" ""))

;;
;; # Agents
;;

(def ag (agent [:a :b]))

(expect "#<Agent [:a :b]>"
        (clojure.string/replace (zprint-str ag) #"\@[0-9a-f]*" ""))

(def agf (agent [:c :d]))
(send agf + 5)

(expect "#<Agent FAILED [:c :d]>"
        (clojure.string/replace (zprint-str agf) #"\@[0-9a-f]*" ""))

;;
;; # Sorting maps in code
;;

; Regular sort

(expect "{:a :b, :g :h, :j :k}"
        (zprint-str "{:g :h :j :k :a :b}" {:parse-string? true}))

; Still sorts in a list, not code 

(expect "({:a :b, :g :h, :j :k})"
        (zprint-str "({:g :h :j :k :a :b})" {:parse-string? true}))

; Doesn't sort in code (where stuff might be a function)

(expect "(stuff {:g :h, :j :k, :a :b})"
        (zprint-str "(stuff {:g :h :j :k :a :b})" {:parse-string? true}))

; Will sort in code if you tell it to

(expect "(stuff {:a :b, :g :h, :j :k})"
        (zprint-str "(stuff {:g :h :j :k :a :b})"
                    {:parse-string? true, :map {:sort-in-code? true}}))

; mapv-no-nil-too-big

(expect [[["ldfkdfj"]] [["jfkldjs"]] [["kldjlfsdj"]] [["ldslk"]]]
        (mapv-no-nil-too-big 28
                             identity
                             [[["ldfkdfj"]] [["jfkldjs"]] [["kldjlfsdj"]]
                              [["ldslk"]]]))

(expect nil
        (mapv-no-nil-too-big 27
                             identity
                             [[["ldfkdfj"]] [["jfkldjs"]] [["kldjlfsdj"]]
                              [["ldslk"]]]))
(def e2 {:aaaa :bbbb, :ccc :ddddd, :ee :ffffff})

(expect "{:aaaa :bbbb,\n :ccc  :ddddd,\n :ee   :ffffff}"
        (zprint-str e2 38 {:map {:justify? true}}))
  
(expect "{:aaaa :bbbb, :ccc :ddddd, :ee :ffffff}"
        (zprint-str e2 39 {:map {:justify? true}}))

;;
;; :wrap? for vectors
;;

(def vba1 (apply vector ba1))

(expect 48 (max-width (zprint-str vba1 48)))
(expect 3 (line-count (zprint-str vba1 48)))

(expect 4 (max-width (zprint-str vba1 48 {:vector {:wrap? nil}})))
(expect 50 (line-count (zprint-str vba1 48 {:vector {:wrap? nil}})))

;;
;; :wrap? for sets
;;

(def svba1 (set vba1))

(expect 47 (max-width (zprint-str svba1 48)))
(expect 4 (line-count (zprint-str svba1 48)))

(expect 5 (max-width (zprint-str svba1 48 {:set {:wrap? nil}})))
(expect 50 (line-count (zprint-str svba1 48 {:set {:wrap? nil}})))

;;
;; :wrap? for arrays
;;

(expect 48 (max-width (zprint-str ba1 48)))
(expect 3 (line-count (zprint-str ba1 48)))

(expect 4 (max-width (zprint-str ba1 48 {:array {:wrap? nil}})))
(expect 50 (line-count (zprint-str ba1 48 {:array {:wrap? nil}})))

;;
;; # indent-arg and indent-body
;;

(defn zctest5a
  "Test indents."
  [x]
  (let [abade :b
        ceered (let [b :d]
                 (if (:a x)
                   ; this is a slightly long comment
                   ; a second comment line
                   (assoc b :a :c)))]
    (list :a
          (with-meta name x)
          (vector :thisisalongkeyword :anotherlongkeyword
                  :ashorterkeyword :reallyshort)
          ; a short comment that might be long
          :c)))

(expect
  "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n      :a\n      (with-meta name x)\n      (vector\n        :thisisalongkeyword :anotherlongkeyword\n        :ashorterkeyword :reallyshort)\n      ; a short comment that might be long\n      :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5a {:list {:hang? false}}))

(expect
  "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n     :a\n     (with-meta name x)\n     (vector\n      :thisisalongkeyword :anotherlongkeyword\n      :ashorterkeyword :reallyshort)\n     ; a short comment that might be long\n     :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5a
                 {:list {:hang? false, :indent-arg 1}}))

(expect
  "(defn zctest5a\n \"Test indents.\"\n [x]\n (let [abade :b\n       ceered (let [b :d]\n               (if (:a x)\n                ; this is a slightly long comment\n                ; a second comment line\n                (assoc b :a :c)))]\n  (list\n   :a\n   (with-meta name x)\n   (vector\n    :thisisalongkeyword :anotherlongkeyword\n    :ashorterkeyword :reallyshort)\n   ; a short comment that might be long\n   :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5a
                 {:list {:hang? false, :indent-arg 1, :indent 1}}))

(expect
  "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n     :a\n     (with-meta name x)\n     (vector\n      :thisisalongkeyword :anotherlongkeyword\n      :ashorterkeyword :reallyshort)\n     ; a short comment that might be long\n     :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5a
                 {:list {:hang? false, :indent-arg 1, :indent 2}}))

;;
;; # key-ignore, key-ignore-silent
;;

;;
;; ## Basic routines
;;

(def ignore-m {:a :b, :c {:e {:f :g}, :h :i}})

(expect {:a :b, :c {:e {:f :g}, :h :zprint-ignored}}
        (map-ignore :map {:map {:key-ignore [[:c :h]]}} ignore-m))

(expect {:a :b, :c {:e {:f :g}}}
        (map-ignore :map {:map {:key-ignore-silent [[:c :h]]}} ignore-m))

(expect {:a :b, :c {:e {:f :g}}}
        (map-ignore :map {:map {:key-ignore-silent [:f [:c :h]]}} ignore-m))

(expect
  {:a :b}
  (map-ignore :map {:map {:key-ignore-silent [[:c :h] [:c :e]]}} ignore-m))

(expect {:a :b, :c {:e :zprint-ignored, :h :zprint-ignored}}
        (map-ignore :map {:map {:key-ignore [[:c :h] [:c :e]]}} ignore-m))

(expect "{:a :b, :c {:e :zprint-ignored, :h :zprint-ignored}}"
        (zprint-str ignore-m {:map {:key-ignore [[:c :h] [:c :e]]}}))

(expect "{:a :zprint-ignored, :c {:e :zprint-ignored, :h :i}}"
        (zprint-str ignore-m {:map {:key-ignore [[:c :e] :a]}}))

(expect "{:c {:h :i}}"
        (zprint-str ignore-m {:map {:key-ignore-silent [:a [:c :e :f]]}}))
