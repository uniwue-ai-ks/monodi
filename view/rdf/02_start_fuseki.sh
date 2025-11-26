#!/bin/sh
if ! [ -e bin/apache-jena -a -e bin/apache-jena-fuseki ]; then
	./00_install_fuseki.sh
fi
bin/apache-jena-fuseki/fuseki-server --config=fuseki.ttl
