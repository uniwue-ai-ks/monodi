# Website htmlContent is trusted and executes scripts

The view UI inserts HTML content via `Range.createContextualFragment` instead of `innerHTML`, so embedded `<script>` tags execute (needed for interactive Notationsdokumentationen). When this was introduced (2026-08), it was verified safe: every string reaching `StaticContent` was team-authored, and the one place where editor-typed text becomes HTML — the critical apparatus — escapes it at export time via scalatags.

## Consequences

Every string rendered through `StaticContent` is a script gate, not just markup. Never feed it — e.g. via a new `htmlContent`-ranged attribute — anything built from unescaped user or editor input; text typed by anyone must be escaped at export time.
