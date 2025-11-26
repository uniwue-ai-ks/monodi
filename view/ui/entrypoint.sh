#!/bin/sh
echo "Applying template variables"

export __HTML_HEAD_EXTRA__="-->${HTML_HEAD_EXTRA}<!--"
envsubst < /srv/index.html.template > /srv/index.html

echo "Running $@"
exec "$@"
