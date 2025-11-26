#!/bin/sh
rm -rf db/ run/ \
 && bin/apache-jena-4.3.1/bin/tdb2.tdbloader --loc db ${@:-data.ttl} \
 && mkdir run/luceneIndexing -p \
 && java -Xmx16G -cp bin/apache-jena-fuseki-4.3.1/fuseki-server.jar jena.textindexer --desc=fuseki.ttl
