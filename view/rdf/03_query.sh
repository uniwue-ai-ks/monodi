#!/bin/sh
curl -G localhost:3030/tdb2-database/query --data-urlencode "query=$(cat)"
