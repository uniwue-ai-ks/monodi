# DockerFiles for MonoDi

# Export
Alter Export Docker container dieser wird nicht mehr benutzt da libsvg-convert in einem eigenen Docker Container aufgerufen wird während des exports.

# librsvg
Docker Image für librsvg-convert dies wird im Export aufgerufen um die Pdfs zu erstellen. Und wird aktuell benutzt. Diese wird nicht immer gebaut sondern nur einmal

# build
Ein Docker-Image um das Projekt auf dem Server zu bauen.
Der Server hat nicht immer die die richtigen Versionen zum Bauen der Software.
Das Image enthält alles, was man braucht um folgende Module zu bauen:
- Editor/Server
- Editor/UI
- Viewer/UI
