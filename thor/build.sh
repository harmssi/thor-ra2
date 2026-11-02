#!/bin/bash
# Build Thor RA2: sync the tree to the build host, build it in the Docker image "thor-build"
# (see thor/Dockerfile), fetch the APK to dist/ThorRA2.apk. Optional: --install (adb install -r).
set -euo pipefail
HOST=${THOR_BUILD_HOST:?set THOR_BUILD_HOST=user@linux-host-with-docker}
REMOTE=${THOR_RA2_REMOTE:-/mnt/cache/thor-ra2}
REPO="$(cd "$(dirname "$0")/.." && pwd)"

ssh "$HOST" "mkdir -p $REMOTE/src"
rsync -a --delete --exclude .git --exclude /build --exclude /dist \
    --exclude /android/.gradle --exclude /android/app/build --exclude /android/app/.cxx \
    --exclude /android/local.properties --exclude /android/keystore.properties \
    "$REPO/" "$HOST:$REMOTE/src/"

ssh "$HOST" "docker run --rm -v $REMOTE:/work thor-build bash /work/src/thor/docker-build.sh"

mkdir -p "$REPO/dist"
scp -q "$HOST:$REMOTE/out/ThorRA2.apk" "$REPO/dist/ThorRA2.apk"
echo "APK: $REPO/dist/ThorRA2.apk"

if [ "${1:-}" = "--install" ]; then
    adb install -r "$REPO/dist/ThorRA2.apk"   # adb uses ANDROID_SERIAL if set
fi
