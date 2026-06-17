#!/bin/sh

# Exit after any error
set -o errexit

RELEASE_VER=$1

if [ "$RELEASE_VER" == "" ]; then
    echo "Usage: setVersion.sh <version>"
    exit 1
fi

echo "Changing version numbers to ${RELEASE_VER}"
./mvnw versions:set -DnewVersion=${RELEASE_VER} -DgenerateBackupPoms=false
