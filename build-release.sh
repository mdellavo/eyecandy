#!/bin/sh

VERSION=$1

if [ -z "${VERSION}" ]
then
    echo "USAGE: build-release.sh VERSION"
    exit 1
fi

APK_PATH="builds/eyecandy-release-${VERSION}.apk"

git tag -a ${VERSION} -m "release - ${VERSION}"
./gradlew installRelease
cp build/apk/eyecandy-release.apk ${APK_PATH}

echo
echo ${APK_PATH}