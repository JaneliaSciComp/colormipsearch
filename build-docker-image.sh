#!/bin/bash

tool=$1
shift

case $tool in
  --with-docker)
  # Run this using `./build-docker-image.sh --load|--push`
  docker buildx build --platform linux/arm64,linux/amd64 --tag ghcr.io/janeliascicomp/colormipsearch-tools:3.1.1 . $*
  ;;
  --with-podman)
  # Run this using `./build-docker-image.sh --load|--push`
  podman manifest create ghcr.io/janeliascicomp/colormipsearch-tools:3.1.1
  podman build --platform linux/arm64,linux/amd64 \
        --manifest ghcr.io/janeliascicomp/colormipsearch-tools:3.1.1 . $*
  ;;

esac
