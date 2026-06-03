# `cantus_id` is the sole inclusion gate for the Cantus export

Every other export in this codebase filters by a `publish` whitelist via `ExportFilter` (see `editor/server/src/main/scala/de/olyro/monodi/exprt/filter/`). The Cantus export deliberately bypasses this whitelist.

A `Document` is included in the Cantus JSON output if and only if its `additionalData` contains a non-empty `Cantus_ID`. The presence of `Cantus_ID` is the opt-in signal: only a curated subset of the corpus is catalogued in Cantus Index, and the inclusion/exclusion decision is made by the editor when they assign (or don't assign) the Cantus identifier — independently of monodi's own publish-status workflow.

A consequence: a future engineer should not "fix" the Cantus pipeline to respect the `publish` whitelist. The two concepts are intentionally decoupled.
