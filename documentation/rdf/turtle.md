# Intro to the Turtle RDF format

This intro explains the basics of the Turtle RDF format. This isn't a comprehensive description, only parts used in the
Monodi project are covered (e.g. specifying data types of literals is left out).

RDF is a directed graph, represented by triples: a subject node, a predicate (edge) and an object node. Each is
represented by a IRI.

Comments are started with `#` and continue to the end of the line.

## IRIs

In Turtle, IRIs are written in angle brackets, and literals are written in double quotes. A triple is written as
`subject predicate object.`. Common prefixes of IRIs can be defined with a `@prefix` directive like `label:
<http://prefix_IRI/>` directive, which allows to write `label:foo` instead of `<http://prefix_IRI/foo>`.

The label may be empty, i.e. you can define a `@prefix :`.

As a special case, the token `a` in the predicate position can be used for the IRI ` http://www.w3.org/1999/02/22-rdf-syntax-ns#type`, which is used to specify a node's type in RDF Schema.

## Triples

A simple triple is written as:

```turtle
<http://example.org/my-subject> <http://example.org/my-predicate> <http://example.org/my-object>.
```

or using a prefix:
```turtle
@prefix pre: <http://example.org/>
pre:my-subject pre:my-predicate pre:my-object.
```


For the common case of adding several predicates to a subject, multiple predicate-object-pairs can follow a subject,
separated by `;` and terminated by `.`, which is equivalent to writing several triples with the same subject as separate
lines:

```turtle
@prefix pre: <http://example.org/>
pre:my-subject pre:predicate1 pre:object1;
	pre:predicate2 pre:object2;
	#...
	pre:predicateN pre:objectN.
```

For repeating the same predicate with different objects, multiple objects can be listed separated by `,`, which is
equivalent to writing several triples with the same subject and predicate as separate lines. E.g:

```turtle
@prefix pre: <http://example.org/>
pre:subject pre:predicate pre:object1, pre:object2, pre:object3.
```

## Literals

An object may also be a literal value instead of an IRI. The possible types include numbers, booleans and strings.

Numbers are written as in most programming languages, e.g. `123`, `3.14`, `-42`.

Booleans are written as `true` or `false` and are case-sensitive.

### Strings

Strings can be written in single or double quotes, e.g. `'hello'`, `"world"`. They may contain any character except line
feeds, carriage returns or the used quote. Multi-line strings can be written by using triple quotes, e.g.

```turtle
'''
multi
line
string
'''
```

A `\` in a string starts an escape sequence, which work as in most programming languages. See the [Turtle
Spec](https://www.w3.org/TR/turtle/#numeric) for details.

Strings can be localized by adding a language tag: an `@` followed by the language code, directly following the closing
quote , e.g. `"hello"@en`, `"""bonjour"""@fr`. Note that this only has an effect for strings, where Monodi expects
a language tag. If you list multiple languages where this is not expected, this will have the same effect as specifying
several triples with the same subject and predicate and may have unintended effects in the viewer.


## Blank nodes

RDF can contain blank nodes, which are nodes without an IRI or literal. They are written as `_:label`, where `label` is
a unique identifier for the blank node. Blank nodes can be used as subjects or objects in triples. They can also be
written inline using square brackets, containing predicate-object pairs separated by `;`, if they are only required in
one place. Turtle also allows nesting blank nodes written this way.

```turtle
@prefix pre: <http://example.org/>
_:blank1
	pre:hasName "foo";
	pre:hasValue 23.

pre:subject pre:predicate _:blank1.

# can also be written as

pre:subject pre:predicate [
	pre:hasName "foo";
	pre:hasValue 23
].
```

In Monodi, they are often used when an object requires more structure than a simple label and can always be written in
the bracket form.
