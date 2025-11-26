# RDF for Monodicum viewer

Use `rdf:Class` to define searchable entities (see examples).

Static parts of the page are defined by adding an entity with `:hasContent` and the html content, tagged with the language
(see `static.tt` for examples).

To have their links shown on the page, add the following predicates:

   - `:position [ ?pos ?order; ];` where `?pos` is one of `:left`, `:right`, `:footer` and `?order` is a number for sorting the links
   - `:hasTitle` takes language tagged strings, which become the link text
	- `:hasRoute` takes a string (no language tag), becomes url part for this page

Some fixed strings are expected in the `<http://olyro.de/mondiview/>` namespace (all using `:hasContent`, but no other properties):

- `:tabTitle`: for setting `document.title`
- `:headerTitle`: text in top left for the link directing to the start page
- `:copyright`: shown in footer
- `:footer`: shown in footer after copyright, before links
- `:mainPagePreSearches`: displayed before centered search links on the start page, may contain HTML
- `:mainPagePostSearches`: displayed after centered search links on the start page, may contain HTML
