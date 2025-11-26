FROM ubuntu:25.10
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
RUN apt update \
  && apt install -y npm curl zip unzip \
  && npm install -g @angular/cli \
  && curl -s "https://get.sdkman.io" | bash \
  && bash -c 'source "/root/.sdkman/bin/sdkman-init.sh" && sdk install java 23.0.2-tem && sdk install sbt'
