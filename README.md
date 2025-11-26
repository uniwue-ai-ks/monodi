# Monodi Document Index and Viewer

Monodi is a toolkit for importing, indexing and viewing documents, originally developed for the Corpus Monodicum project ([see the live instance from that project](https://corpus-monodicum.de/)). The repository contains the web-based viewer and database setup, a editor UI (specific to Corpus Monodicum).

Other projects using Monodi:

- [DIW Wochenberichte](https://diw-berichte.informatik.uni-wuerzburg.de/)
- [Camerarius Digital](https://segmentation-experiments2.informatik.uni-wuerzburg.de/)

## Repository layout

- documentation/ — project documentation, data format notes and deployment instructions
- view/ — frontend and backend code for the web viewer
- editor/ — code for the Corpus Monodicum editor application used to curate imported documents

## Quick start

1. [Read the docs](documentation/rdf/monodi-rdf.md) for details on the Monodi
   RDF format
2. Create an rdf file with your document definitions
3. Run either from source or via docker (see below)

### Running via docker:

```yaml
services:
  fuseki:
    image: harbor-ls6.informatik.uni-wuerzburg.de/monodi-diw/monodi-fuseki:latest
    restart: always
    volumes:
      # mount the folder with your TTL files here
      - ./rdf/:/data/:z
    networks:
      - backend

  frontend:
    image: harbor-ls6.informatik.uni-wuerzburg.de/monodi-diw/monodi:latest
    restart: always
    ports:
      # set your desired external port here
      - "3000:80"
    volumes:
      # mount static files (images, pdfs, etc) here
      - ./static:/srv/resources:z
      - caddy_data:/data:z
      - caddy_config:/config:z
    environment:
      # Set the HTML title
      HTML_TITLE: "Your Project Title"
      # add additional tags to the head, e.g. metadata, analytics scripts, etc.
      HTML_HEAD_EXTRA: "<meta name=\"description\" content=\"Monodi is a document viewer\"/>"
    networks:
      - backend
      - frontend

volumes:
  caddy_data:
  caddy_config:

networks:
  backend:
  frontend:
```

If you want to build the containers yourself, you can find the build files at
- `view/ui/Containerfile`
- `view/rdf/Dockerfile`

### Running from source

1. Run the fuseki database in `view/rdf`:
   ```sh
   ./01_create_tdb2_from_rdf.sh TTL_FILE...
   ./02_start_fuseki.sh
   ```
2. setup and the frontend in `view/ui`:
   ```sh
   npm install
   npm start
   ```
