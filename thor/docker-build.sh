#!/bin/bash
# Runs INSIDE the container "thor-build" (see build.sh). /work = the build directory on the host:
#   /work/src       the source tree (synced by build.sh)
#   /work/keystore  the signing key (created once, KEEP it: updates must be signed with it)
#   /work/out       ThorRA2.apk
# Gradle builds the engine (libmain.so, CMake + NDK, SDL2 2.30.9 fetched by the upstream
# CMake) and the Java shell in one go.
set -euo pipefail
W=/work
APP=$W/src/android
mkdir -p $W/keystore $W/out $W/gradle-home

if [ ! -f $W/keystore/thorra2.jks ]; then
    keytool -genkeypair -v -keystore $W/keystore/thorra2.jks -storepass thorra2 -keypass thorra2 \
        -alias thorra2 -keyalg RSA -keysize 2048 -validity 20000 \
        -dname "CN=Thor RA2, O=harmssi, C=DE"
fi
cat > $APP/keystore.properties <<PROPS
storeFile=$W/keystore/thorra2.jks
storePassword=${THOR_KEYSTORE_PASS:-thorra2}
keyAlias=thorra2
keyPassword=${THOR_KEYSTORE_PASS:-thorra2}
PROPS

CMAKE_BIN=$(command -v cmake)
CMAKE_VER=$(cmake --version | head -1 | awk '{print $3}')
cat > $APP/local.properties <<PROPS
sdk.dir=${ANDROID_HOME:-/opt/android-sdk}
cmake.dir=$(dirname "$(dirname "$CMAKE_BIN")")
PROPS

export GRADLE_USER_HOME=$W/gradle-home
cd $APP
gradle --no-daemon -q assembleRelease -Pthor.cmakeVersion=$CMAKE_VER -Pra2.jobs=$(nproc) ${THOR_GRADLE_ARGS:-}
cp $APP/app/build/outputs/apk/release/app-release.apk $W/out/ThorRA2.apk
ls -la $W/out/ThorRA2.apk
