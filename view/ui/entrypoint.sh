#!/bin/sh
echo "Applying template variables"

export __HTML_HEAD_EXTRA__="-->${HTML_HEAD_EXTRA}<!--"
envsubst < /srv/index.html.template > /srv/index.html

# FUSEKI_HOST: hostname or IP of the fuseki service (default: fuseki)
FUSEKI_HOST="${FUSEKI_HOST:-fuseki}"

# The Caddyfile is generated at runtime so FUSEKI_HOST can be overridden.
# BASE_PATH only affects React Router (window.__BASE_PATH__),
# not Caddy's routing. A reverse proxy in front is responsible for stripping the prefix.
cat > /etc/caddy/Caddyfile <<CADDYEOF
:80 {
	handle_path /fuseki/* {
		reverse_proxy ${FUSEKI_HOST}:3030
	}

	handle {
		file_server {
			browse
			root /srv
		}
		@to-index {
			not path /fuseki/* /static/* /resources/* /manifest.json /favicon.ico /assets/*
		}
		rewrite @to-index /index.html
	}
}
CADDYEOF

echo "Running $@"
exec "$@"
