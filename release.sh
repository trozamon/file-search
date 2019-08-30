#!/bin/bash

set -e

if [ $# -ne 1 ]
then
    echo "Usage: release.sh <version>"
    exit 1
fi

version=${1}

sed -i '/^version =/d' build.gradle
echo "version = '${version}'" >> build.gradle
./gradlew clean build
git add build.gradle
git commit -m "Release v${version}"
git tag -m "Release v${version}" v${version}

# revert
sed -i '/^version =/d' build.gradle
git add build.gradle
git commit -m 'Post-release reset'
