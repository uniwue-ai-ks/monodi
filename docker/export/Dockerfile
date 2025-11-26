FROM fedora:latest
RUN dnf -y update && dnf -y install java-latest-openjdk-devel librsvg2-tools && dnf clean all
ADD ./fonts.tgz /usr/share/fonts/LinLibertine
RUN ["fc-cache", "-f"]
ADD ./server-0.1.0-SNAPSHOT.tgz /app
COPY ./db.json /var/lib/monodi/editor/db.json
COPY ./config.json /var/lib/monodi/editor/config.json
COPY ./data /
ENV LC_ALL=C.UTF-8
CMD ["/app/server-0.1.0-SNAPSHOT/bin/export"]
