FROM fedora:latest
RUN dnf -y update && dnf -y install java-latest-openjdk-devel librsvg2-tools && dnf clean all
ADD ./fonts.tgz /usr/share/fonts/LinLibertine
RUN ["fc-cache", "-f"]
ENV LC_ALL=C.UTF-8
ENTRYPOINT ["rsvg-convert"]
CMD ["--help"]
