FROM scratch as tmp

COPY bin/apache-jena-4.3.1 /opt/app/apache-jena-4.3.1
COPY bin/apache-jena-fuseki-4.3.1 /opt/app/apache-jena-fuseki-4.3.1
COPY entrypoint /opt/app/
COPY fuseki.ttl /opt/app/

FROM eclipse-temurin:11-jre-alpine

COPY --from=tmp /opt/app /opt/app
RUN mkdir /data

WORKDIR /opt/app
CMD ["sh", "entrypoint"]
