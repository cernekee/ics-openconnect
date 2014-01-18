OpenConnect for Android
=======================

This is an experimental VPN client for Android, based on the Linux build of
[OpenConnect](http://www.infradead.org/openconnect/).

Most of the Java code was derived from [OpenVPN for Android](https://play.google.com/store/apps/details?id=de.blinkt.openvpn&hl=en) by Arne Schwabe.

OpenConnect for Android is released under the GPLv2 license.  For more information see the COPYING and doc/LICENSE.txt files.

Changelog: see [doc/CHANGES.txt](doc/CHANGES.txt)

## Downloads

Binary APK files are available at [F-Droid](https://f-droid.org/repository/browse/?fdid=app.openconnect).
No registration is required.

## Screenshots

![screenshot-0](screenshots/screenshot-0.png)&nbsp;
![screenshot-1](screenshots/screenshot-1.png)

![screenshot-2](screenshots/screenshot-2.png)&nbsp;
![screenshot-3](screenshots/screenshot-3.png)

## Compiling the app

Prerequisites:

* Android SDK in your $PATH (both platform-tools/ and tools/ directories)
* javac 1.6 and a recent version of Apache ant in your $PATH
* Use the Android SDK Manager to install API 18

Quick start:

    git clone git://github.com/cernekee/ics-openconnect
    cd ics-openconnect
    android update project -p .
    ant debug

Logs of successful (and not-so-successful) builds can be found on this project's
[Travis CI page](https://travis-ci.org/cernekee/ics-openconnect).

## Recompiling the external dependencies

On the host side you'll need to install:

* NDK r9, nominally under /opt/android-ndk-r9
* Host-side gcc, make, etc. (Red Hat "Development Tools" group or Debian build-essential)
* autoconf, automake, and libtool
* javac 1.6 and git in your $PATH

These commands will build the binary components and copy them into libs/

    git submodule init
    git submodule update
    make -C external NDK=/opt/android-ndk-r9
