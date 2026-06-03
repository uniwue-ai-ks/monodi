#!/bin/sh
echo "Applying template variables"

export __HTML_HEAD_EXTRA__="-->${HTML_HEAD_EXTRA}<!--"
envsubst < /srv/index.html.template > /srv/index.html

# Inject <base> tag so relative asset paths (./assets/...) resolve correctly
# when the app is served under a sub-path prefix by a reverse proxy.
if [ -n "$BASE_PATH" ]; then
  sed -i "s|<head>|<head><base href=\"${BASE_PATH}/\">|" /srv/index.html
fi

# FUSEKI_HOST: hostname or IP of the fuseki service (default: fuseki)
FUSEKI_HOST="${FUSEKI_HOST:-fuseki}"
MANAGER_HOST="${MANAGER_HOST:-manager}"
export FUSEKI_HOST
export MANAGER_HOST

echo "Running $@"
exec "$@"
