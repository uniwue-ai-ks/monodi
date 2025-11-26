# Docker Files for the viewer

The viewer consists of three containers, the frontend, the fuseki database
and a caddy as a reverse proxy and for static files. The images for the
frontend and fuseki can be built from the Dockerfiles in the respective
directories (`ui` and `rdf`):

```
docker build view/ui/ -t monodi:latest
podman build view/rdf/ -t monodi-fuseki:latest
```

The docker-compose file requires one environment variable `MONODI_BASE_URL`,
which should contain the URL (including protocol and, if necessary, port) of
the server, e.g. `https://monodi.informatik.uni-wuerzburg.de`.

There are several volumes that you may want to change:

- Fuseki: `../rdf/data.ttl:/opt/app/data.ttl:z` should point to the data file
	(turtle format) of the fuseki database.
- Caddy: `/srv` inside the container is the path for static files, with the
	default environment given to the viewer it expects `svg` and `pdf` subfolders
	containing the respective files.

By default the viewer is served on port 3000, with fuseki and static files
being served under subpaths, so only a single port has to be forwarded.

The script `compose.sh` can be used to start the viewer with docker-compose,
creating the required directories and asking for the `MONODI_BASE_URL` if there
is no `.env` file yet.
