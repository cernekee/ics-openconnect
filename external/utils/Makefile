#
# Based somewhat on android/Makefile in OpenConnect (David Woodhouse)
#

NDK     := /opt/android-ndk-r9
ARCH    := arm
GCCVER  := 4.8
APIVER  := 14
TOP	:= ..
DIST	:= $(TOP)/dist

# You should be able to just 'make ARCH=x86' and it should DTRT.
ifeq ($(ARCH),arm)
TRIPLET := arm-linux-androideabi
TOOLCHAIN := $(TRIPLET)-$(GCCVER)
OPENSSL_TARGET := android-armv7
endif
ifeq ($(ARCH),x86)
TRIPLET := i686-linux-android
TOOLCHAIN := x86-$(GCCVER)
OPENSSL_TARGET := android-x86
endif
ifeq ($(ARCH),mips)
TRIPLET := mipsel-linux-android
TOOLCHAIN := $(TRIPLET)-$(GCCVER)
OPENSSL_TARGET := android
EXTRA_LDFLAGS := -lz
endif

NDK_SYSROOT := $(NDK)/platforms/android-$(APIVER)/arch-$(ARCH)
OC_SYSROOT  := $(shell pwd)/sysroot-$(TRIPLET)

BINDIR := $(firstword $(wildcard $(NDK)/toolchains/$(TOOLCHAIN)/prebuilt/*/bin))
PATH := $(BINDIR):$(PATH)
PKG_CONFIG_LIBDIR=$(OC_SYSROOT)/lib/pkgconfig
export PATH PKG_CONFIG_LIBDIR


MAKEINSTALL=$(MAKE) INSTALL=$(shell pwd)/install_symlink.sh

CONFIGURE_ARGS := --host=$(TRIPLET) --prefix=$(OC_SYSROOT) \
		  --disable-shared --enable-static \
		  CFLAGS="--sysroot=$(NDK_SYSROOT) -I$(OC_SYSROOT)/include" \
		  LDFLAGS="-L$(OC_SYSROOT)/lib"

.PHONY: all
all: curl stoken

.PHONY: clean
clean:
	rm -rf arm-linux-androideabi/ i686-linux-android/ \
		mipsel-linux-android/ sources sysroot-*/ \
		arm mips x86

#####################################################################
#
# Build OpenSSL for Android
#
OPENSSL_VER := 1.0.1e
OPENSSL_DIR := $(TRIPLET)/openssl-$(OPENSSL_VER)

$(OPENSSL_DIR)/Configure: $(DIST)/openssl-$(OPENSSL_VER).tar.gz
	mkdir -p $(TRIPLET)
	tar xfz $< -C $(TRIPLET)
	touch $(OPENSSL_DIR)/Configure # Make sure it's newer than Makefile and tarball

$(OPENSSL_DIR)/Makefile: $(OPENSSL_DIR)/Configure
	cd $(OPENSSL_DIR) && perl Configure --prefix=$(OC_SYSROOT) \
		--cross-compile-prefix=$(TRIPLET)- no-shared \
		$(OPENSSL_TARGET):"gcc --sysroot=$(NDK_SYSROOT)"

$(OPENSSL_DIR)/libssl.a: $(OPENSSL_DIR)/Makefile
	$(MAKE) -C $(OPENSSL_DIR) build_libs

$(OC_SYSROOT)/lib/libssl.a: $(OPENSSL_DIR)/libssl.a
	# Do this manually instead of using 'make install' since we want symlinks
	mkdir -p $(OC_SYSROOT)/include/openssl
	ln -sf $(shell pwd)/$(OPENSSL_DIR)/include/openssl/*.h $(OC_SYSROOT)/include/openssl
	mkdir -p $(OC_SYSROOT)/lib/pkgconfig
	ln -sf $(shell pwd)/$(OPENSSL_DIR)/*.pc $(OC_SYSROOT)/lib/pkgconfig
	ln -sf $(shell pwd)/$(OPENSSL_DIR)/*.a $(OC_SYSROOT)/lib

OPENSSL_DEPS := $(OC_SYSROOT)/lib/libssl.a

openssl: $(OPENSSL_DEPS)


#####################################################################
#
# Build curl
#
CURL_VER := 7.34.0
CURL_SRC := sources/curl-$(CURL_VER)
CURL_BUILD := $(TRIPLET)/curl

$(CURL_SRC)/configure: $(DIST)/curl-$(CURL_VER).tar.bz2
	mkdir -p sources
	tar xjf $< -C sources
	touch $@

$(CURL_BUILD)/Makefile: $(CURL_SRC)/configure $(OPENSSL_DEPS)
	mkdir -p $(CURL_BUILD)
	cd $(CURL_BUILD) && ../../$(CURL_SRC)/configure $(CONFIGURE_ARGS) \
		--with-ipv6

$(CURL_BUILD)/src/curl: $(CURL_BUILD)/Makefile
	$(MAKE) -C $(CURL_BUILD)

$(ARCH)/curl: $(CURL_BUILD)/src/curl
	mkdir -p $(ARCH)
	cp $< $@

.PHONY: curl
curl: $(ARCH)/curl


#####################################################################
#
# Build libtomcrypt
#
TOMCRYPT_VER := 1.17
TOMCRYPT_DIR := $(TRIPLET)/libtomcrypt-$(TOMCRYPT_VER)

$(TOMCRYPT_DIR)/makefile: $(DIST)/crypt-$(TOMCRYPT_VER).tar.bz2
	mkdir -p $(TRIPLET)
	tar xfj $< -C $(TRIPLET)
	touch $@

$(TOMCRYPT_DIR)/libtomcrypt.a: $(TOMCRYPT_DIR)/makefile
	$(MAKE) -C $(TOMCRYPT_DIR) \
		CC="$(TRIPLET)-gcc --sysroot=$(NDK_SYSROOT)" \
		AR="$(TRIPLET)-ar" \
		RANLIB="$(TRIPLET)-ranlib"

$(OC_SYSROOT)/lib/libtomcrypt.a: $(TOMCRYPT_DIR)/libtomcrypt.a
	$(MAKE) -C $(TOMCRYPT_DIR) \
		DESTDIR=$(OC_SYSROOT) \
		LIBPATH=/lib INCPATH=/include \
		INSTALL_USER=$(shell id -u) \
		INSTALL_GROUP=$(shell id -g) \
		NODOCS=1 install

TOMCRYPT_DEPS := $(OC_SYSROOT)/lib/libtomcrypt.a

tomcrypt: $(TOMCRYPT_DEPS)


#####################################################################
#
# Build libstoken
#
STOKEN_SRC := $(TOP)/stoken
STOKEN_BUILD := $(TRIPLET)/stoken

$(STOKEN_SRC)/configure:
	cd $(STOKEN_SRC) && ./autogen.sh
	touch $@

$(STOKEN_BUILD)/Makefile: $(STOKEN_SRC)/configure $(TOMCRYPT_DEPS)
	mkdir -p $(STOKEN_BUILD)
	cd $(STOKEN_BUILD) && ../../$(STOKEN_SRC)/configure $(CONFIGURE_ARGS) \
		--without-gtk --with-java=$(NDK_SYSROOT)/usr/include \
		--enable-jni-standalone --enable-shared --disable-static

$(STOKEN_BUILD)/libstoken.so: $(STOKEN_BUILD)/Makefile
	$(MAKE) -C $(STOKEN_BUILD)

$(OC_SYSROOT)/lib/libstoken.so: $(STOKEN_BUILD)/libstoken.so
	$(MAKE) -C $(STOKEN_BUILD) install

STOKEN_DEPS := $(OC_SYSROOT)/lib/libstoken.so

$(ARCH)/libstoken.so: $(STOKEN_DEPS)
	mkdir -p $(ARCH)
	cp $< $@

.PHONY: stoken
stoken: $(ARCH)/libstoken.so

.PHONY: sources
sources: $(CURL_SRC)/configure $(STOKEN_SRC)/configure
