# Print and website texts are separate renditions by design

Per-source scholarly texts (Quellenbeschreibung, Notationsdokumentation) exist twice: as a Markdown string in the `quelle` DB column (filled from the sources Excel import) used for print, and as a hand-authored HTML file in the content repo used for the website. This duplication is deliberate, not an accident to be unified: the print pipeline needs a Markdown AST it lays out itself (page breaking, page numbers), while the website rendition wants medium-specific richness — its own styling, links to other pages, and (for the Notationsdokumentation) embedded JavaScript — none of which survives a Markdown pipeline.

## Considered Options

- Single source of truth in Markdown, converted to HTML for the website — rejected: caps the website rendition at what Markdown can express.
- Single source of truth in HTML — rejected: the print layout engine cannot consume HTML, and teaching it to would be a project of its own.
