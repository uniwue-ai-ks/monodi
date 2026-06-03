# Cantus siglum and century live on Source, not Document

The Cantus export needs a `siglum` (RISM-style source identifier) and a `century` per record. These could have been stored per-Document — matching the per-record shape of the Cantus JSON — or per-Source, matching the domain: a siglum belongs to a manuscript, not to an individual chant inside it.

We placed `Cantus_Siglum` and `Cantus_Century` as native columns on `Source`. Both are properties of the manuscript itself, shared by every chant in it. Storing them per-Document would allow two rows of the same source to disagree, and would duplicate the value across every row of the Excel sheet.

The other three Cantus fields (`Cantus_ID`, `Cantus_Melody_ID`, `Cantus_Genre`) are per-Document, since they identify a specific chant within a source.
