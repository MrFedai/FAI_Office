# Build environment for the LibreOffice WASM (headless) engine, PoC WP7. Throwaway (MASTER_SPEC section 15).
# Emscripten version as required by LibreOffice 26.8 (static/README.wasm.md). The official emsdk image is Ubuntu
# 22.04 (GCC 11), but LibreOffice's build-side tools need GCC >= 13, so emsdk is copied onto Ubuntu 24.04.
FROM emscripten/emsdk:4.0.10 AS emsdk
FROM ubuntu:24.04
COPY --from=emsdk /emsdk /emsdk
ENV EMSDK=/emsdk PATH=/emsdk:/emsdk/upstream/emscripten:/emsdk/node/22.16.0_64bit/bin:$PATH
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      autoconf automake bison ccache flex gperf libtool make nasm pkg-config patch \
      git zip unzip xz-utils bzip2 curl wget ca-certificates file \
      build-essential python3 python3-dev python3-setuptools \
      libxml2-utils xsltproc gettext locales meson ninja-build uuid-dev uuid-runtime \
    && rm -rf /var/lib/apt/lists/* \
    && locale-gen en_US.UTF-8
ENV LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8
ARG UID=1000
RUN (getent passwd $UID | cut -d: -f1 | xargs -r userdel -r 2>/dev/null || true) \
    && useradd -m -u $UID builder && chmod -R a+rwX /emsdk
USER builder
WORKDIR /src
