# Monodi

Monodi manages music sheets of the Latin chant tradition. It edits source material, produces SVG renderings, and exports metadata to internal (RDF/Turtle) and external (Cantus Index) consumers.

## Language

**Source**:
A historical witness (manuscript, fragment, or print) carrying chants. The monodi entity is `Source`, keyed by `Quellensigle`.
_Avoid_: Manuscript, witness, MS, book

**Document**:
One chant within a source — the editable unit and the granularity of the Cantus export. Carries `textinitium`, `foliostart`, and per-chant metadata.
_Avoid_: Chant entry, item, record

**Spiel**:
A curated collection of `Document`s for a specific liturgical occasion. Spiele are rendered as a single combined PDF; they are **not** themselves Cantus records.
_Avoid_: Office, collection, programme

**Quellensigle**:
Monodi's internal identifier for a `Source`. Free-form, set in the Excel import; used as the source's primary key and URL path.
_Avoid_: Source code, source ID, Sigle

**Siglum (Cantus)**:
The source identifier as Cantus Index expects to receive it (often RISM-style, e.g. `A-ABC Fragm. 1`). Stored as `Cantus_Siglum` on the `Source`. May differ in format from `Quellensigle`.
_Avoid_: Sigle (unqualified), Source identifier (unqualified)

**Textinitium**:
The opening text of a chant, as edited in monodi. Stored on `Document.textinitium`.
_Avoid_: Beginning, start text

**Incipit (Cantus)**:
The Cantus-side name for the opening text. Emitted directly from `Document.textinitium` — same content, different name in the JSON output.
_Avoid_: Beginning

**Folio**:
The leaf identifier within a source (e.g. `1r`, `12v`). Held in `Document.foliostart`. Passed through to Cantus verbatim.
_Avoid_: Page, sheet

**Full text**:
The complete prose of a chant, joined from its `Syllable`s and paratext. Computed on demand from the document's notes; not stored as a field.
_Avoid_: Volltext (when speaking in English), body, content

**Cantus Index (CI)**:
External concordance database aggregating chant records across many source databases. Receives daily JSON exports from monodi via the `CantusExport` pipeline.
_Avoid_: Cantus (ambiguous — could also mean the chant repertory itself)

**MEI export**:
A per-`Document` XML export in the Music Encoding Initiative neumes schema. Emits one `<docId>.xml` file per document containing only the musical skeleton (staves of syllables with neumes). Produced by the `MeiExport` pipeline, gated by an optional `meiPath` config field.
_Avoid_: MEI file (ambiguous — could refer to the MEI standard generally), neume export

**db code (Cantus)**:
The fixed string `"CM"` identifying monodi's export within Cantus Index.
_Avoid_: Database name, source code

**Cantus ID**:
The Cantus Index identifier for a chant record (e.g. `008060`). Set per `Document` in the `Cantus_ID` field. Identifies the chant concordance; resolves to `https://cantusindex.org/id/{Cantus_ID}`. Independent of whether the document is currently eligible for export.
_Avoid_: Chant ID (unqualified), CI ID

**Cantus melody ID**:
The Cantus Index identifier for the melody/neume tradition associated with a chant (e.g. `008060a`). Set per `Document` in the `Cantus_Melody_ID` field. Independent of `Cantus ID` — a document may carry either, both, or neither. Resolves to `https://cantusindex.org/melody/{Cantus_Melody_ID}`.
_Avoid_: Melody number (that is the internal `Melodiennummer_Katalog`, a different field)

## Example dialogue

> **Editor**: I added the `cantus_id` on this Document but the chant still isn't showing up in the export.
>
> **Dev**: Did you also set `Cantus_Siglum` on its Source? Without that, the export skips the row and logs a warning.
>
> **Editor**: The Source already has a `Quellensigle` though — can't the export just use that?
>
> **Dev**: No. `Quellensigle` is monodi's internal identifier; the `Siglum` that Cantus expects is a different format (often RISM-style). They're related fields but kept separate on purpose.
>
> **Editor**: Got it. And the Spiel this chant belongs to — does that get exported too?
>
> **Dev**: No, Spiele aren't Cantus records. Only individual Documents go, and only the ones that have a `cantus_id`.
