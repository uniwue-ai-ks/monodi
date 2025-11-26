# Monodi - Frontend

## Available Scripts

In the project directory, you can run:

### `npm start`

Runs the app in the development mode.<br />
Open [http://localhost:5174](http://localhost:5174) to view it in the browser.

### `npm run build`

Builds the app for production to the `dist` folder.<br />

## OCI container

The included `Containerfile` builds an image based on the Caddy HTTP server,
which also functions as a reverse proxy for Fuseki. See the [compose
file](../docker/compose.yml) for information on usage.
