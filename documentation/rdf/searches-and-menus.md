# Configured searches and navigation menus

By default every [class](monodi-rdf.md#class-definitions) automatically gets a top-level search
link in the navigation bar and on the start page, opening the plain class search form. The two
vocabularies here are **additive** and do not change that:

- **`:Search`** — defines a *configured view* over a class (intro text, an optional landing
  page, pre-applied filters). It adds **nothing** to the navigation on its own; it is only a
  search *target* that you surface by pointing a `:MenuEntry` (or any link) at it. A Monodi
  subcorpus, for example, is a `:Search`.
- **`:MenuEntry`** — the only way to add navigation entries beyond the automatic class links:
  extra links and nested drop-down menus, each pointing at a search (a `:Search` node *or* a
  class), an arbitrary URL, or acting as a pure grouping header.

Both are authored as plain Turtle.

## `:Search`: a configured view over a class

A `:Search` node is opened at `/search/<its-uri>`, exactly like a class — the URL segment is
resolved by type, so a class and a `:Search` are interchangeable as link targets. Defining a
`:Search` does **not** place it anywhere in the navigation; surface it with a [`:MenuEntry`](#menuentry-navigation-entries-and-drop-downs)
(below) or link to its URL. When the viewer opens a `:Search` it loads the searchable attributes
and *all* view configuration from the search's `:baseClass`, and overlays only the predicates
below. A `:Search` therefore never overrides a class field; it only adds intro text, a landing
page, and pre-applied filters (fixed or prefilled).

- **`:baseClass`** (required) — the class to search over, in Monodi, often `data:document`. The
  search uses that class's attributes, columns, synopsis settings, button texts, etc.
- **`:introText`** (language-tagged HTML) — rendered between the page header and the search bar.
- **`:landingHtml`** (language-tagged HTML) together with **`:landingButtonText`**
  (language-tagged) — an optional landing page rendered *instead of* the search. A button
  labelled with `:landingButtonText` dismisses it and reveals the search.
- **`:fixedFilter`** (zero or more) — a node with an `:attribute` (an attribute IRI of the base
  class) and a `:value`. Each is applied as a **hidden, non-overridable** filter: the attribute
  is removed from the search form and the results table, and every result must match the value.
- **`:prefilledFilter`** (zero or more) — the same shape (`:attribute` + `:value`), but an
  **editable default** rather than a lock. The filter is pre-populated in the search form when
  the page is first opened, and the user can change or remove it from there. An empty `:value`
  just pre-opens the filter row with no default value. The field stays visible and is not
  enforced — the opposite of `:fixedFilter`.

```turtle
data:searchEdition  a :Search ;
   :baseClass  data:document ;
   :introText  "Die Edition umfasst …"@de, "The edition comprises …"@en ;
   :fixedFilter     [ :attribute data:documentSubcorpus ; :value "edition" ] ;
   :prefilledFilter [ :attribute data:documentFesttag ; :value "" ] .  # pre-opens an (empty) feast-day filter

# Gated behind a landing page, no intro text:
data:searchGraduale a :Search ;
   :baseClass  data:document ;
   :fixedFilter [ :attribute data:documentSubcorpus ; :value "graduale-synopticum" ] ;
   :landingHtml "<h2>Graduale Synopticum</h2><p>…</p>"@de ;
   :landingButtonText "Zur Suche"@de, "Go to search"@en .

# No fixed filter at all → the full, unfiltered corpus:
data:searchCumulative a :Search ;
   :baseClass data:document .
```

## `:MenuEntry`: navigation entries and drop-downs

A `:MenuEntry` is one item in the navigation. It has an `rdfs:label` (language-tagged) and at
most one **target**:

- **`:search <uri>`** — open a search. The target may be a `:Search` node *or* a plain class.
- **`:href "<url>"`** — go to an arbitrary URL: an external address (`https://…`) or an
  internal path (`/katalog-landing`).
- **no target** — a pure grouping node that only opens a drop-down of its children.

Two predicates control placement:

- **`:topLevelOrder n`** — places the entry directly in the header at position `n`. There may be
  several top-level entries, each its own header item or menu. An entry **without**
  `:topLevelOrder` appears only where it is referenced as a `:child`.
- **`:child [ :entry <other> ; :order n ]`** — lists an entry's children as ordered
  *placements*. Children appear in a drop-down sorted by `:order`. Because a child is referenced
  by IRI, the same entry can be placed under several parents at different positions.

```turtle
# A header menu "Editionen" with two children:
data:menuEditionen  a :MenuEntry ;
   rdfs:label "Editionen"@de, "Editions"@en ;
   :topLevelOrder 0 ;
   :child [ :entry data:menuEdition       ; :order 0 ] ,
          [ :entry data:menuTranskription ; :order 1 ] .

# Leaves, reused as children above; each opens a configured search:
data:menuEdition       a :MenuEntry ;
   rdfs:label "Edition (ED)"@de ; :search data:searchEdition .

data:menuTranskription a :MenuEntry ;
   rdfs:label "Transkriptionen (TR)"@de ; :search data:searchTranskription .

# A plain header link, no drop-down:
data:menuKatalog       a :MenuEntry ;
   rdfs:label "Katalog"@de ; :topLevelOrder 1 ; :href "/katalog-landing" .
```

A top-level entry with children renders similar to a drop-down.

> Do not create cycles — an entry must not end up a descendant of itself. The viewer guards
> against infinite loops, but a cyclic menu is almost certainly a mistake.
