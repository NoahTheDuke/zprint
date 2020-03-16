# zprint

__zprint__ is a library and command line tool providing a variety
of pretty printing capabilities for both Clojure code and Clojure/EDN
structures.  It can meet almost anyone's needs.  As such, it supports
a number of major source code formattng approaches.

[![cljdoc badge](https://cljdoc.org/badge/zprint/zprint)](https://cljdoc.org/d/zprint/zprint/CURRENT)

## See zprint:

  * [__classic zprint__](./doc/types/classic.md) -- ignores whitespace 
  in function definitions and formats code with a variety of heuristics 
  to look as good as hand-formatted code 
  ([_see examples_](./doc/types/classic.md))
  * [__respect blank lines__](./doc/types/respectbl.md) -- similar to 
  classic zprint, but blank lines inside of function defintions are retained, 
  while code is otherwise formatted to look beautiful
  ([_see examples_](./doc/types/respectbl.md))
  * [__indent only__](./doc/types/indentonly.md) -- very different from 
  classic zprint -- no code ever changes lines, it is only correctly 
  indented on whatever line it was already on
  ([_see examples_](./doc/types/indentonly.md))

In addition, zprint is very handy [__to use at the REPL__](./doc/types/repl.md).

## Use zprint:

  * [to format whole files](./doc/using/files.md)
  * [while using an editor](./doc/using/editor.md)
  * [at the REPL](./doc/using/repl.md)
  * [from inside a Clojure(script) program](./doc/using/library.md)

## Get zprint:

  * [a standalone binary for macOS](./doc/getting/macos.md)    _starts in <50 ms_
  * [a standalone binary for Linux](./doc/getting/linux.md)    _starts in <50 ms_
  * [an uberjar for any Java enabled platform](./doc/getting/uberjar.md)    _starts in several seconds_
  * [an accelerated uberjar for any Java enabled platform](./doc/getting/appcds.md)    _starts in about 1s_
  * [a library to use at the REPL](./doc/using/repl.md)

## Get something other than the default formatting:

### Without learning how to configure zprint:

Maybe one of the existing "styles" will meet your needs.  All you have to
do is put `{:style ...}` on the command line or as the third argument
to a zprint call.  For example, `{:style :community}` or 
`{:style :respect-bl}`.

Some commonly used styles:

  * [Format using "community" standards](./doc/reference.md#community)
  * [Respect blank lines](./doc/reference.md#respect-bl)
  * [Indent Only](./doc/reference.md#indent-only)
  * [Respect all newlines](./doc/reference.md#respect-nl)
  * [Detect and format hiccup vectors](./doc/reference.md#hiccup)
  * [Justify all pairs](./doc/reference.md#justified)
  * [Backtranslate `quote`, `deref`, `var`, `unquote` in structures](./doc/reference.md#backtranslate)
  * [Detect keywords in vectors, if found respect newlines](./doc/reference.md#keyword-respect-nl)
  * [Sort dependencies in project.clj](./doc/reference.md#sort-dependencies)
  * [Support "How to ns"](./doc/reference.md#how-to-ns)

## Learn how to alter zprint's formatting behavior:

  * [How do I change zprint's behavior?](./doc/altering.md)

### I want to change...

  * [how user defined functions are formatted](./doc/options/fns.md)
  * [the indentation in lists](./doc/options/indent.md)
  * [the configuration to track the "community" standard](./doc/options/community.md)
  * [how blank lines in source are handled](./doc/options/blank.md)
  * [how map keys are formatted](./doc/options/maps.md)
  * [the colors used for formatting source](./doc/options/colors.md)
  * [how the second element of a pair is indented](./doc/options/pairs.md)
  * [how comments are handled](./doc/options/comments.md)
  * [anything else...](./doc/reference.md#introduction-to-configuration)

## Usage

[![cljdoc badge](https://cljdoc.org/badge/zprint/zprint)](https://cljdoc.org/d/zprint/zprint/CURRENT)

### Clojure 1.9, 1.10, 1.10.1:

__Leiningen ([via Clojars](http://clojars.org/zprint))__

[![Clojars Project](http://clojars.org/zprint/latest-version.svg)](http://clojars.org/zprint)

[![Clojars Project](https://img.shields.io/clojars/v/zprint/zprint.svg)](https://clojars.org/zprint)


### Clojurescript:

zprint has been tested in each of the following environments:

  * Clojurescript 1.10.520
    - figwheel 0.5.19
    - shadow-cljs 2.8.62
  * `lumo` 1.10.1
  * `planck` 2.24.0

It requires `tools.reader` at least 1.0.5, which all of the environments
above contain.

### Clojure 1.8:

The last zprint release built with Clojure 1.8 was [zprint "0.4.15"].

In addition to the zprint dependency, you also need to
include the following library when using Clojure 1.8:

```
[clojure-future-spec "1.9.0-alpha17"]
```

## The zprint Reference

  * [Entire reference document](./doc/reference.md)
  * [What does zprint do?](./doc/reference.md#what-does-zprint-do)
  * [Features](./doc/reference.md#features)
  * The zprint [API](./doc/reference.md#api)
  * Configuration
    * [ Configuration uses an options map](./doc/reference.md#configuration-uses-an-options-map)
    * [ Where to put an options map](./doc/reference.md#where-to-put-an-options-map)
    * [ __Simplified Configuration__ -- using `:style`](./doc/reference.md#style-and-style-map)
      * [  Respect blank lines](./doc/reference.md#respect-bl)
      * [  Indent Only](./doc/reference.md#indent-only)
      * [  Format using "community" standards](./doc/reference.md#community)
      * [  Respect all newlines](./doc/reference.md#respect-nl)
      * [  Detect and format hiccup vectors](./doc/reference.md#hiccup)
      * [  Justify all pairs](./doc/reference.md#justified)
      * [  Backtranslate `quote`, `deref`, `var`, `unquote` in structures](./doc/reference.md#backtranslate)
      * [  Detect keywords in vectors, if found respect newlines](./doc/reference.md#keyword-respect-nl)
      * [  Sort dependencies in project.clj](./doc/reference.md#sort-dependencies)
      * [  Support "How to ns"](./doc/reference.md#how-to-ns)
      * [  Add newlines between pairs in `let` binding vectors](./doc/reference.md#map-nl-pair-nl-binding-nl)
      * [  Add newlines between `cond`, `assoc` pairs](./doc/reference.md#map-nl-pair-nl-binding-nl)
      * [  Add newlines between extend clauses](./doc/reference.md#extend-nl)
      * [  Add newlines between map pairs](./doc/reference.md#map-nl-pair-nl-binding-nl)
    * [ Options map format](./doc/reference.md#options-map-format)
      * [  Option Validation](./doc/reference.md#option-validation)
      * [  What is Configurable](./doc/reference.md#what-is-configurable)
	* [   Generalized Capabilities](./doc/reference.md#generalized-capabilites)
	* [   Syntax Coloring](./doc/reference.md#syntax-coloring)
	* [   Function Classification for Pretty Printing](./doc/reference.md#function-classification-for-pretty-printing)
	  * [    Changing or Adding Function Classifications](./doc/reference.md#changing-or-adding-function-classifications)
	  * [    Replacing functions with reader-macros](./doc/reference.md#replacing-functions-with-reader-macros)
	  * [    Controlling single and multi-line output](./doc/reference.md#controlling-single-and-multi-line-output)
	  * [    A note about two-up printing](./doc/reference.md#a-note-about-two-up-printing)
	  * [    A note on justifying two-up printing](./doc/reference.md#a-note-on-justifying-two-up-printing)
    * [ Formatting large or deep collections](./doc/reference.md#formatting-large-or-deep-collections)
    * [ Widely Used Configuration Parameters](./doc/reference.md#widely-used-configuration-parameters)
    * [ __Configurable Elements__](./doc/reference.md#configurable-elements)
      * [:agent](./doc/reference.md#agent-atom-delay-fn-future-promise)
      * [:array](./doc/reference.md#array)
      * [:atom](./doc/reference.md#agent-atom-delay-fn-future-promise)
      * [:binding](./doc/reference.md#binding)
      * [:comment](./doc/reference.md#comment)
      * [:delay](./doc/reference.md#agent-atom-delay-fn-future-promise)
      * [:extend](./doc/reference.md#extend)
      * [:fn](./doc/reference.md#agent-atom-delay-fn-future-promise)
      * [:future](./doc/reference.md#agent-atom-delay-fn-future-promise)
      * [:list](./doc/reference.md#list)
      * [:map](./doc/reference.md#map)
      * [:object](./doc/reference.md#object)
      * [:pair](./doc/reference.md#pair)
      * [:pair-fn](./doc/reference.md#pair-fn)
      * [:promise](./doc/reference.md#agent-atom-delay-fn-future-promise)
      * [:reader-cond](./doc/reference.md#reader-cond)
      * [:record](./doc/reference.md#record)
      * [:set](./doc/reference.md#set)
      * [:spec](./doc/reference.md#spec)
      * [:style](./doc/reference.md#style-and-style-map)
      * [:style-map](./doc/reference.md#style-and-style-map)
      * [:tab](./doc/reference.md#tab)
      * [:vector](./doc/reference.md#vector)
      * [:vector-fn](./doc/reference.md#vector-fn)


### Contributors

A number of folks have contributed to zprint, not all of whom
show up on GitHub because I have integrated the code or suggestions manually.
__Thanks for all of the great contributions!__

  * `--url` and `--url-only`: @coltnz
  * Suggestion/encouragement to implement `:respect-bl`: @griffis
  * `:option-fn` and `:fn-format` for enhanced vector formatting: @milankinen
  * Fixed missing require in `spec.cljc`: @Quezion
  * Corrected readme: @griffis
  * Fixed nested reader conditional: @rgould1
  * Clarified and added useful example for clj usage: @bherrmann7
  * Suggested fix for international chars and graalVM native image: @huahaiy

Thanks to everyone who has contributed fixes as well as everyone who has
reported an issue.  I really appreciate all of the help making zprint better
for everybody!

### Acknowledgements

At the core of `zprint` is the `rewrite-clj` library by Yannick
Scherer, which will parse Clojure source into a zipper.  This is a
great library!  I would not have attempted `zprint` if `rewrite-clj`
didn't exist to build upon.  The Clojurescript port relies on Magnus
Rundberget's port of `rewrite-clj` to Clojurescript, `rewrite-cljs`.
It too worked with no issues when porting to Clojurescript!


## License

Copyright © 2016-2020 Kim Kinnear

Distributed under the MIT License.  See the file LICENSE for details.
