# Hierarchical classes

Monodi supports a hierarchy between classes. A class may define a "referece class", from which it can inherit
properties. Each class may only have a single reference class, but multiple classes can have the same reference class.

As in the main documentation, this page assumes the following prefixes defined in Turtle:
```turtle
@prefix : <http://olyro.de/mondiview/> .
@prefix data: <http://monodicum/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
```

## Usage scenarios

Reference classes can be used to include metadata from a common class. In Corpus Monodicum for example, one class is
used for sources (e.g. a book from a library), another class is used for songs. As all song documents come from one of
the sources, the "source" class is set as the reference class of the "song" class, so that songs can display some
information about their source without duplicating the data.

It is also possible to create a nested search, i.e. search the larger class, then search all documents in a smaller
class that refer to the same document of the larger class. For example, the Camerarius project has books with large
numbers of pages. Finding a book by a text match isn't helpful without being able to search inside the book. Also, the
PDFs are very large, so you don't want to always load the whole document. Therefore, single pages are added as a class,
with the book class as reference. By providing a label for the relation, the search for the book class can now display a
button to go to the page search for a specific book.

## Structure overview

Class references are encoded as predicates on the *referencing class*. The *referenced class* (i.e. the "parent") does
not require any specific predicates. The referencing class also specifies an attribute, that holds the reference, i.e.
the IRIs of instances of the parent class. Additionaly, the referencing class can have attributes that refer to
attributes of the parent class. The values for these are then retrieved from the referenced instance instead of being
specified with the instance of the subclass.


## Defining a reference class

A class needs two predicates to refer to another class. The `:referenceClass` predicate specifies the IRI of the parent
class. The `:referenceAttribute` specifies the IRI of an attribute of the referencing class, which will hold the
references. For example, a simple class for a page in a book could look like this:

```turtle
data:page
  a rdfs:Class;
  rdfs:label "Seiten"@de,"Pages"@en;
  # this specifies, that a data:page is part of a data:book (another rdfs:Class)
  :referenceClass     data:book;
  # in this attribute, a page will specify the URI of the book it belongs to
  :referenceAttribute data:pageBook.
```

The attribute `data:pageBook` will also have to be defined. It should use an `rdfs:range` of `:entity` or `:string`
(only differ in UI rendering, equivalent if no document position is given). Anything else is the same as for other
attributes. E.g. for our page class:

```turtle
data:pageBook
  a rdf:Property;
  rdfs:label "aus Buch"@de,"from book"@en;
  rdfs:domain data:page;
  rdfs:range :entity.
```

An instance would then specify the IRI of the book the page belongs to, e.g.

```turtle
data:page-1234789 data:pageBook data:book-09876.
```

## Using attributes from the referenced class

With the reference class defined, we can reuse data from the referenced class. In our book / page example, we may want
to display the book title in the header, when viewing a single page.

Define reference attributes like you would normal attributes, but set `rdfs:range` to `:reference` and specify which
parent attribute should be used with the `:referenceField` predicate, which expects the IRI of the parent attribute.
For example, if our book class has an attribute `data:bookTitle`, we can reuse it this way:

```turtle
data:pageRefBook
  a rdf:Property;
  rdfs:domain data:page;
  rdfs:label "Buchtitel"@de,"Book title"@en;
  :headerOrder 0;
  :searchOrder 0;
  rdfs:range :reference;
  :referenceField data:bookTitle;
  :documentPosition [ :header 0 ].
```

As you can see, other predicates like `rdfs:label` or `:searchOrder` can be specified like with other attributes. These
do not have to match the parent attribute, for example the label may different (useful, if your subclass has a similar
attribute, especially with common things like "title").

When defining an instance of `data:page`, the `:reference` attributes like `data:pageRefBook` should not be used as
predicates. All values for these attributes come from the referenced class.

## Nested search

You can create a link from the parent class search to the referencing class search. Like described above, one could
search for books and then jump to the page search for a result to search only pages from that book. To enable this, the
referencing class needs to specify the text to display for this link via the `:referenceLabel` attribute. For example:

```turtle
data:page :referenceLabel "Seiten durchsuchen"@de, "Seiten durchsuchen"@en.
```
