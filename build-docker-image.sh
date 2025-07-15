#!/bin/bash

tool=$1
shift

case $tool in
  --with-docker)
  # Run this using `./build-docker-image.sh --load|--push`
  docker buildx build --platform linux/arm64,linux/amd64 --tag ghcr.io/janeliascicomp/colormipsearch-tools:3.1.1 . $*
  ;;
  --local-podman-image)
  echo "Create images"
  podman build  \
        --platform linux/amd64,linux/arm64 \
        --manifest ghcr.io/janeliascicomp/colormipsearch-tools:3.1.1 . \
        $*
  ;;
  --push-podman-image)
  echo "Create images"
  podman build  \
        --platform linux/amd64,linux/arm64 \
        --manifest ghcr.io/janeliascicomp/colormipsearch-tools:3.1.1 . \
        $*
  podman manifest push ghcr.io/janeliascicomp/colormipsearch-tools:3.1.1
  ;;

esac
