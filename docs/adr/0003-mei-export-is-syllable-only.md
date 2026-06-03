# MEI export drops everything except syllables and their notes

The MEI export emits only the musical skeleton: each `ZeileContainer` becomes one `<staff>` containing one `<layer>` of `<syllable>` elements with `<neume>`/`<nc>` children. Every other structural element from the monodi data model — `ParatextContainer` (Feier/Festtag headings, retro paratext), `LineChange`, `FolioChange`, `Clef`, and `Box` — is dropped on export. `FormteilContainer` / `MiscContainer` boundaries are flattened: all Zeilen across all top-level children appear as siblings in a single `<section>`.

Consequence: MEI files are structurally lossy. A future requirement to round-trip MEI back into monodi, or to surface folio/clef/paratext to MEI consumers, will require revisiting this decision — not just an additive change. Cantus-style metadata (Cantus_ID, Siglum) is also absent from the file; that lives in the [Cantus export](0002-cantus-id-as-sole-export-gate.md), not here.
