#!/bin/bash
rm -rf db/ run/
binJena/bin/tdb2.tdbloader --loc db data.ttl
mkdir run/luceneIndexing -p
java -Xmx16G -cp bin/fuseki-server.jar jena.textindexer --desc=fuseki.ttl
bin/fuseki-server --config=fuseki.ttl
