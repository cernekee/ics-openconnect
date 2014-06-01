OpenConnect for Android
=======================

This is a VPN client for Android, based on the Linux build of
[OpenConnect](http://www.infradead.org/openconnect/).

Much of the Java code was derived from [OpenVPN for Android](https://play.google.com/store/apps/details?id=de.blinkt.openvpn&hl=en) by Arne Schwabe.

OpenConnect for Android is released under the GPLv2 license.  For more
information see the [COPYING](COPYING) and [doc/LICENSE.txt](doc/LICENSE.txt)
files.

Changelog: see [doc/CHANGES.txt](doc/CHANGES.txt)

To help out with translations, please visit
[this project's page on Transifex](https://www.transifex.com/projects/p/ics-openconnect/).

## Downloads and support

Official releases are posted in the [XDA thread](http://forum.xda-developers.com/showthread.php?t=2616121) and on [Google Play](https://play.google.com/store/apps/details?id=app.openconnect).

Binary APK files are also available at [F-Droid](https://f-droid.org/repository/browse/?fdid=app.openconnect).

No registration is required to download from XDA or F-Droid.

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

* NDK r9d, nominally under /opt/android-ndk-r9d
* Host-side gcc, make, etc. (Red Hat "Development Tools" group or Debian build-essential)
* autoconf, automake, and libtool
* javac 1.6 and git in your $PATH

These commands will build the binary components and copy them into libs/
and assets/raw/

    git submodule init
    git submodule update
    make -C external NDK=/opt/android-ndk-r9d
