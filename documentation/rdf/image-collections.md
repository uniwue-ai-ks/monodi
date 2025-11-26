# Displaying collections of images

Monodi supports two types of image collections: `:imageCollection` and `:htmlImageCollection`.


## `:imageCollection`: IIIF and SVG

An attribute with `rdfs:range :imageCollection` is used to view pages of a document, either from an IIIF image server or
as SVG images. Both may be specified to enable a side-by-side view. An example for an attribute using this type could be:

```turtle
data:documentNotes
    a rdf:Property ;
    :documentPosition [ :main 0 ];
    rdfs:label  "Noten"@de,"Pitches"@en;
    rdfs:domain data:document  ;
    rdfs:range  :imageCollection.
```

The value for an image collection is a node with one or more `:hasPage` predicates. Each represents one page (or image).
The node for a single page requires a `:pageNr` predicate with an integer value.

It also requires a `:hasResolutions` predicate with a node that can have any number of `:resolution` predicates. Each
resolution requires
- a `:url` predicate with the path to an SVG, relative to the path set via REACT_APP_SVG_URL. This defaults to
  "/resources/svg/". For the docker image, this means relative to the folder `/svg` in the volume mounted ad
  `/srv/resources`)
- a `:res` predicate with the intended width of the SVG in pixels. If multiple `:resolution` are present, the viewer
  will select the SVG with largest the `:res` below the viewport size (or the smallest `:res`, if all are larger than
  the viewport).

Note that you need the `:hasResolutions` predicate even if you don't use SVGs. Set it to a blank node without predicates
in that case.

The following predicates on the page node are optional:

- `:label`: a string with a label for the page, shown in the IIIF viewer
- `:image`: URL for a IIIF image information request (ending with "/info.json")
- `:hasPrintView`: URL for a PDF of the page, for download.

An example image collection from Corpus Monodicum data:

```turtle
data:7f725812-8ad3-446d-a71e-bdd4c4acb83c data:documentNotes data:document_images_7f725812-8ad3-446d-a71e-bdd4c4acb83c.
data:document_images_7f725812-8ad3-446d-a71e-bdd4c4acb83c :hasPage [
    :pageNr 0;
    :image """https://gallica.bnf.fr/iiif/ark:/12148/btv1b550057271/f241/info.json""";
    rdfs:label """299""";
    :hasResolutions [
        :resolution [
            :url """27C62052804812886112D5DB68380EA67F86C4089C90635D2AF503540EE4E2A4.svg""";
            :res 515;
        ];
        :resolution [
            :url """ED2DFAEAA216E4A7B912539F318B98A007B1B143ECFE2B1D35234AC06410DA03.svg""";
            :res 755;
        ];
        :resolution [
            :url """2ECF644D85D80108D98D2AA86F524A558EB76F43BC808F75C8B98A090759B344.svg""";
            :res 1115;
        ];
        :resolution [
            :url """45932F11D4F46B2C9FC3ECCFBD383703187F069C99BF3C82A1B8AE6AB3ABA4CB.svg""";
            :res 1655;
        ];
        :resolution [
            :url """DC0E1690131CE47C28406A9C0DBD79DC7E8C173D4C6C199BE2942CC8C5445275.svg""";
            :res 2465;
        ];
    ];
].
```

## `:htmlImageCollection`: Interspersed HTML and images

TODO
