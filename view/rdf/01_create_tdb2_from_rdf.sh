#!/bin/sh
if ! [ -e bin/apache-jena -a -e bin/apache-jena-fuseki ]; then
	./00_install_fuseki.sh
fi

rm -rf db/ run/ \
 && bin/apache-jena/bin/tdb2.tdbloader --loc db ${@:-data.ttl} \
 && mkdir run/luceneIndexing -p \
 && java -Xmx16G -cp bin/apache-jena-fuseki/fuseki-server.jar jena.textindexer --desc=fuseki.ttl
