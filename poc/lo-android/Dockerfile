# Build environment for the LibreOffice Android (aarch64) engine, PoC WP6. Throwaway (MASTER_SPEC section 15).
# The source tree, Android SDK and NDK are mounted, not copied (see build.sh).
FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      autoconf automake bison ccache flex gperf libtool make nasm pkg-config patch \
      git zip unzip xz-utils bzip2 curl wget ca-certificates file \
      build-essential python3 python3-dev python3-setuptools \
      libxml2-dev libxslt1-dev libxml2-utils xsltproc libfontconfig-dev libfreetype-dev \
      libkrb5-dev libnss3-dev libcups2-dev libx11-dev libxext-dev libxrender-dev libxrandr-dev libxtst-dev \
      openjdk-17-jdk-headless ant gettext locales meson ninja-build uuid-dev uuid-runtime \
    && rm -rf /var/lib/apt/lists/* \
    && locale-gen en_US.UTF-8
ENV LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8
ARG UID=1000
RUN userdel -r ubuntu 2>/dev/null || true; useradd -m -u $UID builder
USER builder
WORKDIR /src
