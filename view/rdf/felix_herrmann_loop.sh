#!/bin/bash

# while /tmp/stop-loop doesn't exists
while [ ! -f /tmp/stop-loop ]; do
  ./01_create_tdb2_from_rdf.sh data.ttl
  ./02_start_fuseki.sh
  cp /tmp/out/fuseki/data.ttl ./data.ttl
  rm -rf /var/www/html/monodicum/svgs
  cp /tmp/out/svgs -r /var/www/html/monodicum/
  rm -rf /tmp/out
done
