#!/bin/bash

what=$1
shift

IMAGE_NAME=ghcr.io/janeliascicomp/colormipsearch-tools:3.3.0

case $what in
  --build-image-with-docker)
  # Run this using `./build-docker-image.sh --load|--push`
  docker buildx build --platform linux/arm64,linux/amd64 --tag ${IMAGE_NAME} . $*
  ;;
  --build-image-with-podman)
  echo "Remove existing images"
  podman manifest rm ${IMAGE_NAME} -i
  podman image rm -f ${IMAGE_NAME}
  echo "Create images"
  podman build  \
        --platform linux/amd64,linux/arm64 \
        --manifest ${IMAGE_NAME} . \
        $*
  ;;
  --build-and-push-podman-image)
  echo "Remove existing images"
  podman manifest rm ${IMAGE_NAME} -i
  podman image rm -f ${IMAGE_NAME}
  echo "Create images"
  podman build  \
        --platform linux/amd64,linux/arm64 \
        --manifest ${IMAGE_NAME} . \
        $*
  echo "Push ${IMAGE_NAME} images"
  podman manifest push ${IMAGE_NAME}
  ;;
  --push-podman-image)
  echo "Push ${IMAGE_NAME} images"
  podman manifest push ${IMAGE_NAME}
  ;;
  *)
  echo "$0 {--build-image-with-docker, --build-image-with-podman, --build-and-push-podman-image, --push-podman-image} [optional-build-args]"

esac
