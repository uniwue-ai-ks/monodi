#!/bin/sh
VERSION="6.0.0"

printf "\033[1;32mInstalling Apache Jena + Fuseki $VERSION\033[0m\n"

curl -fLo "apache-jena-${VERSION}.tar.gz" "https://dlcdn.apache.org/jena/binaries/apache-jena-${VERSION}.tar.gz"
curl -fLo "apache-jena-fuseki-${VERSION}.tar.gz" "https://dlcdn.apache.org/jena/binaries/apache-jena-fuseki-${VERSION}.tar.gz"

mkdir -p bin
tar -x -C bin -f "apache-jena-${VERSION}.tar.gz"
tar -x -C bin -f "apache-jena-fuseki-${VERSION}.tar.gz"

ln -fsr bin/apache-jena-${VERSION} bin/apache-jena
ln -fsr bin/apache-jena-fuseki-${VERSION} bin/apache-jena-fuseki
