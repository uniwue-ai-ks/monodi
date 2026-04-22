#!/bin/sh
echo "Applying template variables"

export __HTML_HEAD_EXTRA__="-->${HTML_HEAD_EXTRA}<!--"
envsubst < /srv/index.html.template > /srv/index.html

# Generate Caddyfile from template
# BASE_PATH should be empty or a path without trailing slash, e.g. /viewer
if [ -z "$BASE_PATH" ]; then
  # Serve from root: use plain `handle` blocks
  cat > /etc/caddy/Caddyfile <<'CADDYEOF'
:80 {
	handle_path /fuseki/* {
		reverse_proxy fuseki:3030
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
else
  # Serve from subfolder: use handle_path to strip the prefix
  cat > /etc/caddy/Caddyfile <<CADDYEOF
:80 {
	handle_path ${BASE_PATH}/fuseki/* {
		reverse_proxy fuseki:3030
	}

	handle_path ${BASE_PATH}/* {
		file_server {
			browse
			root /srv
		}
		@to-index {
			not path /static/* /resources/* /manifest.json /favicon.ico /assets/*
		}
		rewrite @to-index /index.html
	}
}
CADDYEOF
fi

echo "Running $@"
exec "$@"
