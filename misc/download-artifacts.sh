#!/bin/bash

set -euo pipefail

BASE=https://storage.googleapis.com/cernekee-travis/ics-openconnect

pushd app/libs
wget -O openconnect-wrapper.jar ${BASE}/openconnect-wrapper.jar
wget -O stoken-wrapper.jar ${BASE}/stoken-wrapper.jar
popd

mkdir -p app/src/main/jniLibs/{armeabi,x86}
pushd app/src/main/jniLibs
wget -O armeabi/libopenconnect.so ${BASE}/arm/libopenconnect.so
wget -O x86/libopenconnect.so ${BASE}/x86/libopenconnect.so
wget -O armeabi/libstoken.so ${BASE}/arm/libstoken.so
wget -O x86/libstoken.so ${BASE}/x86/libstoken.so
popd

mkdir -p app/src/main/assets/raw/{armeabi,x86}
pushd app/src/main/assets/raw
wget -O armeabi/curl-bin ${BASE}/arm/curl
wget -O x86/curl-bin ${BASE}/x86/curl
wget -O armeabi/run_pie ${BASE}/arm/run_pie
wget -O x86/run_pie ${BASE}/x86/run_pie
popd

exit 0
