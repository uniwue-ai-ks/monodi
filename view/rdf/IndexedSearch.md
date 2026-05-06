# Enabling indexed search

To implement indexed fulltext search on german text attributes, add the
following to the `text:map` collection in `fuseki.ttl` (note: entries in Turtle
collections are *not* separated by commas):

```turtle
[
   text:field "text" ;
   text:predicate <http://monodicum/diwDocument:text> ;
   text:analyzer [ a text:GenericAnalyzer ;
		   text:class "de.olyro.monodi.GermanCompoundAnalyzer" ]
]
```

replace `<http://monodicum/diwDocument:text>` with the URI of the attribute to index.

## Custom analyzer

The custom analyzer can be found in `analyzer/` and is built using maven. It
requires a german word list, which can be created using aspell.
See `./01_create_tdb2_from_rdf.sh`

## TODOs:

- [ ] test correct loading of analyzer in container
- [ ] implement queries in viewer. An example query using this index can be
  found in `./search-indexed.sparql` (with the equivalent regex query in
  `./search-basic.sparql`).
  Note that `text:search` is not implemented as a FILTER, but appears as a WHERE
  clause. This means the performance is very dependent on the location of the
  query, it should be at the top of the WHERE block. Simply moving the query for
  `?obj a <http://monodicum/diwDocument> .` before it makes it slower than regex
  search, as it will load all documents twice.
