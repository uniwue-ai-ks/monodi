#!/bin/sh
export FUSEKI_BASE=bin/apache-jena-fuseki/
if ! [ -e bin/apache-jena -a -e bin/apache-jena-fuseki ]; then
	./00_install_fuseki.sh
fi

# if the GermanCompoundAnalyzer is configured, make sure it is built
if grep -q "^[^#]*de\\.olyro\\.monodi\\.GermanCompoundAnalyzer" fuseki.ttl; then
	ANALYZER_JAR=analyzer/target/german-compound-analyzer-1.0.jar
	if ! [ -e "$ANALYZER_JAR" ]; then
		echo "Analyzer JAR not found. Building..."
		if ! [ -e analyzer/src/main/resources/german-words.txt ]; then
			echo "Generating German wordlist (requires aspell-de)..."
			mkdir -p analyzer/src/main/resources
			aspell --lang=de dump master | sed 's/\/.*//' | tr '[:upper:]' '[:lower:]' | sort -u \
				> analyzer/src/main/resources/german-words.txt || exit 1
		fi
		(cd analyzer && mvn -q package)
		mkdir $FUSEKI_BASE/extra/
		cp analyzer/target/german-compound-analyzer-1.0.jar $FUSEKI_BASE/extra/
	fi
fi

rm -rf db/ run/
bin/apache-jena/bin/tdb2.tdbloader --loc db ${@:-data.ttl} || exit 1
mkdir run/luceneIndexing -p
java -Xmx16G \
	-cp "bin/apache-jena-fuseki/fuseki-server.jar:${FUSEKI_BASE}/extra/*" \
	jena.textindexer --desc=fuseki.ttl
