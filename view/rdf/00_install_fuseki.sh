#!/bin/sh
VERSION="${JENA_BASE_URL:-6.1.0}"
JENA_BASE_URL="${JENA_BASE_URL:-https://dlcdn.apache.org}"
# use this base url
#JENA_BASE_URL=https://archive.apache.org/dist

printf "\033[1;32mInstalling Apache Jena + Fuseki $VERSION\033[0m\n"

curl -fLo "apache-jena-${VERSION}.tar.gz" "${JENA_BASE_URL}/jena/binaries/apache-jena-${VERSION}.tar.gz"
curl -fLo "apache-jena-fuseki-${VERSION}.tar.gz" "${JENA_BASE_URL}/jena/binaries/apache-jena-fuseki-${VERSION}.tar.gz"

mkdir -p bin
tar -x -C bin -f "apache-jena-${VERSION}.tar.gz"
tar -x -C bin -f "apache-jena-fuseki-${VERSION}.tar.gz"

ln -fsr bin/apache-jena-${VERSION} bin/apache-jena
ln -fsr bin/apache-jena-fuseki-${VERSION} bin/apache-jena-fuseki
