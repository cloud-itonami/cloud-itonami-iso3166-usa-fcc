#!/usr/bin/env nbb
;; Live gate for `src/statute/facts.cljc`.
;;
;; Re-fetches the official eCFR APIs and checks four things:
;;
;;   1. POSITIVE (headings) -- for every catalog entry and every absence
;;      `see-instead`, walking `:statute/cfr-node` from the title root lands on
;;      a node whose `label_description` is byte-identical to
;;      `:statute/verified-label`.
;;
;;   2. POSITIVE (text) -- for every entry carrying `:statute/verified-quote`,
;;      that span occurs byte-exactly in the section text returned by the
;;      versioner full-text API. A heading can survive a repeal of the sentence
;;      underneath it, so a heading-only gate reports `verified` for a catalog
;;      whose operative claims have been gutted. This half is what makes the
;;      notes in facts.cljc, not just its citations, falsifiable.
;;
;;   3. NEGATIVE -- for every absence:
;;        * `:absence/absent-part`  -- scanning the subtree under
;;          `:statute/under` finds NO part with that identifier;
;;        * `:absence/absent-label` -- scanning that subtree finds NO node whose
;;          label matches the pattern, AND the paired `:absence/control-label`
;;          pattern DOES match there.
;;      The control is not decoration. A negative's failure mode is that it
;;      passes for free: fetch the wrong tree, or an empty one, and `nothing
;;      matched` looks exactly like `confirmed still absent`. A control that
;;      must match turns that silence into an explicit could-not-answer.
;;
;;   4. FLOOR -- a run that checked fewer than `--min` headings or fewer than
;;      `--min-quotes` quotes, or zero of any kind, is a could-not-answer.
;;
;; Why walk the tree rather than match the URL. Hierarchical CFR identifiers
;; nest as substrings of one another (part `2` is a prefix of part `20`, and
;; section `1.2110` of `1.21100`), so a string match can succeed against the
;; wrong node. Walking explicit [type identifier] steps cannot pass by accident.
;; One step may be `"*"`, used only for `subject_group`, whose identifiers eCFR
;; generates (`ECFR1c76b0f5e5569c9`) and which are not citable addresses; a
;; wildcard that resolves to more than one node is reported as a failure, not
;; silently taken.
;;
;; EVIDENCE FLOOR. This script refuses to report a pass it did not earn:
;;   * exit 2 -- could not answer (network failure, unparseable catalog, a
;;               title whose API endpoint is not declared, a control that
;;               stopped matching, zero checks of some kind). NOT a pass, and
;;               deliberately neither 0 nor 1.
;;   * exit 1 -- answered, and at least one citation, quote or absence is wrong.
;;   * exit 0 -- answered, everything checked, and both floors were met.
;; "Nothing was checked" and "nothing was wrong" must not share an exit code.
;;
;; Usage:  nbb tools/verify_citations.cljs [--min N] [--min-quotes N] [--quiet]

(ns verify-citations
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            ["fs" :as fs]
            ["path" :as path]))

(def argv (vec (drop 2 (js->clj js/process.argv))))
(defn flag? [f] (boolean (some #{f} argv)))
(defn flag-val [f default]
  (let [i (.indexOf argv f)]
    (if (neg? i) default (get argv (inc i) default))))

(def quiet? (flag? "--quiet"))
(def min-citations (js/parseInt (flag-val "--min" "40") 10))
(def min-quotes    (js/parseInt (flag-val "--min-quotes" "6") 10))

(defn say [& xs] (when-not quiet? (println (str/join " " xs))))
(defn die [code & xs]
  (binding [*print-fn* *print-err-fn*] (println (str/join " " xs)))
  (js/process.exit code))

;; ---------------------------------------------------------------- catalog ---
;; facts.cljc is Clojure source, not EDN. Rather than depend on a reader that
;; would have to evaluate `ns`/`defn` forms, pull the literal values out by
;; locating their `(def ...)` heads and reading the first EDN form after each.

(def facts-path
  "Locate `src/statute/facts.cljc` by walking up from the working directory, so
  the gate works whether it is invoked from the repo root or from `tools/`."
  (let [rel (path/join "src" "statute" "facts.cljc")]
    (loop [dir (js/process.cwd) hops 0]
      (let [cand (path/join dir rel)]
        (cond
          (fs/existsSync cand) cand
          (> hops 3) (die 2 "CANNOT-ANSWER: could not locate" rel
                          "from working directory" (js/process.cwd))
          :else (recur (path/dirname dir) (inc hops)))))))

(def ^:private ws #{" " "\t" "\n" "\r" ","})

(defn- skip-ws [s i]
  (let [n (count s)]
    (loop [i i] (if (and (< i n) (ws (nth s i))) (recur (inc i)) i))))

(defn- skip-string
  "Index just past the string literal starting at `i` (which must be a quote)."
  [s i]
  (let [n (count s)]
    (loop [i (inc i)]
      (cond
        (>= i n)             i
        (= (nth s i) "\\")  (recur (+ i 2))
        (= (nth s i) "\"")   (inc i)
        :else                (recur (inc i))))))

(defn- value-start
  "Given source starting just after a `(def name`, return the index of the first
  character of the value form, stepping over an optional docstring.

  Two things this must get right, both learned by getting them wrong:

  * Scanning naively for the first `[` or `{` lands inside the docstring, which
    contains bracket characters (`[type identifier]` appears in more than one
    of them), and reads a malformed form.

  * Skipping *every* leading string is also wrong. For `(def x \"doc\" \"val\")`
    that is right, but for `(def x \"val\")` -- a def whose value simply IS a
    string -- it skips the value and lands on `)`. So: skip at most one string,
    then look. If what follows it is the closing paren, the string was the
    value after all."
  [s]
  (let [i (skip-ws s 0)]
    (cond
      (>= i (count s))    nil
      (not= (nth s i) "\"") i
      :else (let [after (skip-ws s (skip-string s i))]
              (if (or (>= after (count s)) (= (nth s after) ")"))
                i
                after)))))

(defn read-form-after
  "Read the value form of the `(def ...)` whose head is `marker`."
  [src marker]
  (let [i (.indexOf src marker)]
    (when (neg? i) (die 2 "CANNOT-ANSWER: marker not found in facts.cljc:" marker))
    (let [after (subs src (+ i (count marker)))
          start (value-start after)]
      (when (nil? start)
        (die 2 "CANNOT-ANSWER: could not find the value form after" marker))
      (try
        (edn/read-string (subs after start))
        (catch :default e
          (die 2 "CANNOT-ANSWER: unreadable form after" marker "--" (.-message e)))))))

(def src
  (try (fs/readFileSync facts-path "utf8")
       (catch :default e
         (die 2 "CANNOT-ANSWER: cannot read" facts-path "--" (.-message e)))))

(def api-endpoints (read-form-after src "(def ecfr-structure-api"))
(def full-text-api (read-form-after src "(def ecfr-full-text-api"))
(def catalog       (read-form-after src "(def catalog"))
(def absences      (read-form-after src "(def absences"))

;; ------------------------------------------------------------------ fetch ---

(def gap-ms (js/parseInt (flag-val "--gap-ms" "400") 10))

(defn- sleep [ms] (js/Promise. (fn [resolve] (js/setTimeout resolve ms))))

(def ^:private retryable #{429 500 502 503 504})

(defn- fetch-body
  "Fetch `url`, retrying a rate-limit or transient server error with a growing
  pause. A gate that reports CANNOT-ANSWER because the server asked it to slow
  down is a gate people learn to ignore, so this distinguishes `the server said
  no` from `the server said wait`."
  [url as]
  (letfn [(attempt [n]
            (-> (js/fetch url)
                (.then (fn [r]
                         (cond
                           (.-ok r) (if (= as :json) (.json r) (.text r))
                           (and (< n 4) (retryable (.-status r)))
                           (do (say "  (HTTP" (.-status r) "-- retry" n "of 3 for" (str url ")"))
                               (.then (sleep (* 2000 n)) (fn [_] (attempt (inc n)))))
                           :else (die 2 "CANNOT-ANSWER: HTTP" (.-status r) "from" url))))
                (.catch (fn [e]
                          (if (< n 4)
                            (.then (sleep (* 2000 n)) (fn [_] (attempt (inc n))))
                            (die 2 "CANNOT-ANSWER: fetch failed for" url "--"
                                 (.-message e)))))))]
    (attempt 1)))

(defn- fetch-sequential
  "Fetch `[{:k :url :as}]` one at a time with `gap-ms` between requests, and
  return a promise of {k body}. Issued in parallel these requests draw HTTP 429
  from the eCFR API -- measured, not assumed."
  [specs]
  (reduce (fn [p {:keys [k url as]}]
            (.then p (fn [acc]
                       (.then (fetch-body url as)
                              (fn [v] (.then (sleep gap-ms)
                                             (fn [_] (assoc acc k v))))))))
          (js/Promise.resolve {})
          specs))

;; -------------------------------------------------------------------- xml ---

(defn section-text
  "Strip tags and collapse whitespace, so a recorded quote can be compared
  against running text without depending on the XML's line wrapping. Entity
  unescaping puts `&amp;` last, otherwise `&amp;lt;` would decode twice."
  [xml]
  (-> xml
      (str/replace #"<[^>]*>" "")
      (str/replace "&lt;" "<")
      (str/replace "&gt;" ">")
      (str/replace "&quot;" "\"")
      (str/replace "&#8217;" "’")
      (str/replace "&amp;" "&")
      (str/replace #"\s+" " ")
      (str/trim)))

;; ------------------------------------------------------------------- walk ---

(defn walk-node
  "Descend `tree` following [[type identifier] ...]. An identifier of `\"*\"`
  matches any child of that type; the walk branches and must converge on
  exactly one node. Returns {:node n} | {:missing path} | {:ambiguous n}."
  [tree node-path]
  (loop [frontier [tree] steps node-path]
    (if (empty? steps)
      (cond
        (= 1 (count frontier)) {:node (first frontier)}
        :else {:ambiguous (count frontier)})
      (let [[t i] (first steps)
            nxt (vec (for [n frontier
                           c (get n "children")
                           :when (and (= (get c "type") t)
                                      (or (= i "*") (= (get c "identifier") i)))]
                       c))]
        (if (empty? nxt)
          {:missing (first steps)}
          (recur nxt (rest steps)))))))

(defn- descendants [node]
  (tree-seq #(seq (get % "children")) #(get % "children") node))

(defn find-parts
  "Every descendant of `node` of type `part` whose identifier is `id`.
  Recursive on purpose: a part can be re-adopted under a different subchapter
  than the one it used to live in, and an absence that only checked the old
  address would keep passing."
  [node id]
  (filterv #(and (= "part" (get % "type")) (= id (get % "identifier")))
           (rest (descendants node))))

(defn find-labels
  "Every descendant of `node` whose `label_description` matches `re`."
  [node re]
  (filterv #(re-find re (or (get % "label_description") "")) (descendants node)))

;; ------------------------------------------------------------------- main ---

(defn positives
  "Every heading this run must confirm EXISTS."
  []
  (concat
   (for [[iso es] catalog, e es]
     {:what (str iso " " (:statute/id e))
      :title (:statute/cfr-title e) :node (:statute/cfr-node e)
      :label (:statute/verified-label e)})
   (for [a absences
         :let [s (:absence/see-instead a)]
         :when s]
     {:what (str "absence " (:absence/id a) " see-instead")
      :title (:statute/cfr-title s) :node (:statute/cfr-node s)
      :label (:statute/verified-label s)})))

(defn quotes
  "Every section span this run must confirm is still in the live text."
  []
  (for [[iso es] catalog, e es
        :when (:statute/verified-quote e)]
    {:what (str iso " " (:statute/id e))
     :cfr-title (:statute/cfr-title e)
     :url (when-let [base (get full-text-api (:statute/cfr-title e))]
            (str base "?part=" (:statute/quote-part e)
                 "&section=" (:statute/quote-section e)))
     :quote (:statute/verified-quote e)}))

(defn negatives
  "Every part, and every label pattern, this run must confirm is NOT there."
  []
  (concat
   (for [a absences :let [p (:absence/absent-part a)] :when p]
     {:kind :part :what (str "absence " (:absence/id a))
      :title (:statute/cfr-title p) :under (:statute/under p) :part (:statute/part p)})
   (for [a absences :let [l (:absence/absent-label a)] :when l]
     {:kind :label :what (str "absence " (:absence/id a))
      :title (:statute/cfr-title l) :under (:statute/under l)
      :pattern (:statute/pattern l)
      :control (get-in a [:absence/control-label :statute/pattern])})))

(defn- check-positive [trees {:keys [what title node label]}]
  (let [r (walk-node (get trees title) node)]
    (cond
      (:missing r)   {:ok false :what what
                      :why (str "node path did not resolve; first unmatched step "
                                (pr-str (:missing r)) " in " (pr-str node))}
      (:ambiguous r) {:ok false :what what
                      :why (str "wildcard resolved to " (:ambiguous r)
                                " nodes -- an ambiguous citation is not a verified one")}
      :else
      (let [got (get (:node r) "label_description")]
        (if (= got label)
          {:ok true :what what}
          {:ok false :what what
           :why (str "label drift\n     recorded: " (pr-str label)
                     "\n     live:     " (pr-str got))})))))

(defn- check-negative [trees {:keys [kind what title under part pattern control]}]
  (let [r (walk-node (get trees title) under)]
    (if-not (:node r)
      {:ok false :cannot-answer? true :what what
       :why (str "the root this absence is scoped to did not resolve: " (pr-str under)
                 " -- the negative was never actually searched")}
      (let [root (:node r)]
        (case kind
          :part
          (let [hits (find-parts root part)]
            (if (seq hits)
              {:ok false :what what
               :why (str "recorded as absent, but title " title " part " part " EXISTS: "
                         (pr-str (get (first hits) "label_description"))
                         ". The negative is stale.")}
              {:ok true :what what}))

          :label
          (cond
            (nil? control)
            {:ok false :cannot-answer? true :what what
             :why "no :absence/control-label, so a vacuous scan would look like a pass"}

            (empty? (find-labels root (re-pattern control)))
            {:ok false :cannot-answer? true :what what
             :why (str "control pattern " (pr-str control) " matched nothing under "
                       (pr-str under) ". The scan is not reading the tree it claims "
                       "to read, so the absence below proves nothing.")}

            :else
            (let [hits (find-labels root (re-pattern pattern))]
              (if (seq hits)
                {:ok false :what what
                 :why (str "recorded as absent, but " (count hits)
                           " node(s) in title " title " match " (pr-str pattern)
                           ", e.g. " (pr-str (get (first hits) "label_description"))
                           ". The negative is stale.")}
                {:ok true :what what}))))))))

(defn -main []
  (let [pos (positives) neg (negatives) qs (vec (quotes))]
    (when (empty? pos)
      (die 2 "CANNOT-ANSWER: catalog parsed but yielded zero citations."
           "Refusing to report a pass."))
    (when (empty? neg)
      (die 2 "CANNOT-ANSWER: no absence carries :absence/absent-part or"
           ":absence/absent-label, so the negative half of this gate checked"
           "nothing. Refusing to report a pass."))
    (when (empty? qs)
      (die 2 "CANNOT-ANSWER: no entry carries :statute/verified-quote, so the"
           "text half of this gate checked nothing. Headings alone cannot tell"
           "you whether the sentence this catalog relies on still exists."))
    (doseq [q qs]
      (when-not (:url q)
        (die 2 "CANNOT-ANSWER: no declared full-text endpoint for CFR title"
             (:cfr-title q) "needed by" (:what q))))
    (let [titles (distinct (concat (map :title pos) (map :title neg)))]
      (doseq [t titles]
        (when-not (get api-endpoints t)
          (die 2 "CANNOT-ANSWER: no declared structure endpoint for CFR title" t)))
      (-> (fetch-sequential
           (concat (for [t titles] {:k [:tree t] :url (get api-endpoints t) :as :json})
                   (for [u (distinct (map :url qs))] {:k [:text u] :url u :as :text})))
          (.then
           (fn [fetched]
             (let [trees (into {} (for [[[kind t] v] fetched :when (= kind :tree)]
                                    [t (js->clj v)]))
                   texts (into {} (for [[[kind u] v] fetched :when (= kind :text)]
                                    [u (section-text v)]))
                   pos-results (mapv #(check-positive trees %) pos)
                   quo-results (mapv (fn [{:keys [what url quote]}]
                                       (let [t (get texts url)]
                                         (cond
                                           (nil? t)
                                           {:ok false :cannot-answer? true :what what
                                            :why (str "no text fetched for " url)}
                                           (str/includes? t quote) {:ok true :what what}
                                           :else
                                           {:ok false :what what
                                            :why (str "quote no longer present in the live"
                                                      " section text\n     recorded: "
                                                      (pr-str quote))})))
                                     qs)
                   neg-results (mapv #(check-negative trees %) neg)
                   results (concat pos-results quo-results neg-results)
                   bad     (remove :ok results)
                   unknown (filter :cannot-answer? bad)
                   np (count pos-results) nq (count quo-results) nn (count neg-results)]
               (say (str "CHECKED\t" np " headings, " nq " quotes, " nn " absences"))
               (doseq [r results] (when-not (:ok r) (say "  FAIL " (:what r) "--" (:why r))))
               (cond
                 (seq unknown)
                 (die 2 (str "CANNOT-ANSWER: " (count unknown)
                             " check(s) could not be performed at all (see FAIL lines above)."
                             " Refusing to report either a pass or a drift."))

                 (seq bad)
                 (die 1 (str "FAIL: " (count bad) " of " (+ np nq nn) " checks wrong."))

                 (< np min-citations)
                 (die 2 (str "CANNOT-ANSWER: only " np " headings checked, below --min "
                             min-citations ". A shrunken catalog must not pass silently."))

                 (< nq min-quotes)
                 (die 2 (str "CANNOT-ANSWER: only " nq " quotes checked, below --min-quotes "
                             min-quotes ". A catalog that stopped pinning section text"
                             " must not pass silently."))

                 :else
                 (do (say (str "OK\t" np " headings byte-exact, " nq
                               " quoted spans still present, " nn
                               " absences confirmed still absent, against the live eCFR API."))
                     (js/process.exit 0)))))))))
  nil)

(-main)
