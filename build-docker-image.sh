#!/bin/bash

what=$1
shift

IMAGE_NAME=ghcr.io/janeliascicomp/colormipsearch-tools:3.1.1

case $what in
  --build-image-with-docker)
  # Run this using `./build-docker-image.sh --load|--push`
  docker buildx build --platform linux/arm64,linux/amd64 --tag ${IMAGE_NAME} . $*
  ;;
  --build-local-podman-image)
  echo "Create images"
  podman build  \
        --platform linux/amd64,linux/arm64 \
        --manifest ${IMAGE_NAME} . \
        $*
  ;;
  --build-and-push-podman-image)
  echo "Create images"
  podman manifest rm ${IMAGE_NAME} -i
  podman image rm -f ${IMAGE_NAME}
  podman build  \
        --platform linux/amd64,linux/arm64 \
        --manifest ${IMAGE_NAME} . \
        $*
  echo "Push ${IMAGE_NAME} images"
  podman manifest push ${IMAGE_NAME}
  ;;

esac
