#!/bin/sh

VERSION=$1

git tag -m "${VERSION}"
./gradlew installRelease
cp build/apk/eyecandy-release.apk builds/eyecandy-release-${VERSION}.apk
