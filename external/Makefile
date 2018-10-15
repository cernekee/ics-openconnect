ANDROID_NDK ?= /opt/android-sdk-linux_x86/android-ndk-r16b

JAR_DIR := ../app/libs
JNI_DIR := ../app/src/main/jniLibs
BIN_DIR := ../app/src/main/assets/raw

ARCH_LIST := arm arm64 x86 x86_64

# The __ANDROID_API__ version needs to stay in sync with
# openconnect/android/Makefile
NDK_ARCH_arm := armeabi
TRIPLET_arm := arm-linux-androideabi
CFLAGS_arm := -mthumb -Os -D__ANDROID_API__=14

NDK_ARCH_arm64 := arm64-v8a
TRIPLET_arm64 := aarch64-linux-android
CFLAGS_arm64 := -Os -D__ANDROID_API__=26

NDK_ARCH_x86 := x86
TRIPLET_x86 := i686-linux-android
CFLAGS_x86 := -Os -D__ANDROID_API__=14

NDK_ARCH_x86_64 := x86_64
TRIPLET_x86_64 := x86_64-linux-android
CFLAGS_x86_64 := -Os -D__ANDROID_API__=21

JAR_LIST := openconnect-wrapper.jar stoken-wrapper.jar
PKG_LIST := curl

ARCH_TARGETS := $(addprefix arch-,$(ARCH_LIST))

ARCH_CURL_TARGETS := $(addsuffix /curl,$(ARCH_LIST))
ARCH_STOKEN_TARGETS := $(addsuffix /libstoken.so,$(ARCH_LIST))
ARCH_OC_TARGETS := $(addsuffix /libopenconnect.so,$(ARCH_LIST))
ARCH_TOOLCHAIN_TARGETS := $(addsuffix /toolchain/.installed,$(ARCH_LIST))

INSTALL_ARCH_TARGETS := $(addprefix install-arch-,$(ARCH_LIST))
INSTALL_JAR_TARGETS := $(addprefix install-jar-,$(JAR_LIST))

CURL_VER := 7.58.0

TOPDIR := $(shell pwd)

get_sysroot = $(TOPDIR)/$(1)/toolchain/sysroot/usr
set_build_env = PATH=$(TOPDIR)/$(1)/toolchain/bin:$$PATH \
	PKG_CONFIG_LIBDIR=$(call get_sysroot,$(1))/lib/pkgconfig

.PHONY: install
install: $(INSTALL_ARCH_TARGETS) $(INSTALL_JAR_TARGETS)

.PHONY: clean
clean:
	rm -rf $(ARCH_LIST) $(JAR_LIST) $(PKG_LIST)

.PHONY: $(INSTALL_JAR_TARGETS)
$(INSTALL_JAR_TARGETS) : install-jar-% : %
	mkdir -p $(JAR_DIR)
	cp $* $(JAR_DIR)/

.PHONY: $(INSTALL_ARCH_TARGETS)
$(INSTALL_ARCH_TARGETS) : install-arch-% : arch-%
	mkdir -p $(JNI_DIR)/$(NDK_ARCH_$*)
	cp $*/libopenconnect.so $(JNI_DIR)/$(NDK_ARCH_$*)/
	cp $*/libstoken.so $(JNI_DIR)/$(NDK_ARCH_$*)/
	mkdir -p $(BIN_DIR)/$(NDK_ARCH_$*)
	cp $*/curl $(BIN_DIR)/$(NDK_ARCH_$*)/curl-bin
	cp $*/run_pie $(BIN_DIR)/$(NDK_ARCH_$*)/

.PHONY: $(ARCH_TARGETS)
$(ARCH_TARGETS) : arch-% : %/curl %/libstoken.so %/libopenconnect.so

curl/configure:
	mkdir -p curl
	tar -C curl --strip 1 -Jxf dist/curl-$(CURL_VER).tar.xz
	touch $@

$(ARCH_CURL_TARGETS) : %/curl : %/toolchain/.installed curl/configure
	mkdir -p $*/build-curl $*/out
	cd $*/build-curl && \
		$(call set_build_env,$*) $(TOPDIR)/curl/configure \
			--prefix=/ --host=$(TRIPLET_$*) --disable-shared \
			--enable-static --with-ipv6 --with-gnutls \
			CFLAGS="$(CFLAGS_$*) -fvisibility=default -fPIE" \
			LDFLAGS="-rdynamic -fPIE -pie" \
			ac_cv_lib_gnutls_gnutls_check_version=yes
	$(call set_build_env,$*) $(MAKE) -C $*/build-curl
	$(call set_build_env,$*) $(MAKE) -C $*/build-curl install-strip \
		DESTDIR=$(TOPDIR)/$*/out
	cp -L $*/out/bin/curl $*/

stoken/configure:
	cd stoken && bash autogen.sh
	touch $@

$(ARCH_STOKEN_TARGETS) : %/libstoken.so : %/toolchain/.installed stoken/configure
	mkdir -p $*/build-stoken $*/out
	cd $*/build-stoken && \
		$(call set_build_env,$*) $(TOPDIR)/stoken/configure \
			--prefix=/ --host=$(TRIPLET_$*) --disable-static \
			--enable-shared --without-gtk \
			--with-java=$(call get_sysroot,$*)/include \
			--enable-jni-standalone \
			CFLAGS="$(CFLAGS_$*)" LDFLAGS="-Wl,--strip-debug"
	$(call set_build_env,$*) $(MAKE) -C $*/build-stoken
	$(call set_build_env,$*) $(MAKE) -C $*/build-stoken install-strip \
		DESTDIR=$(TOPDIR)/$*/out
	cp -L $*/out/lib/libstoken.so $*/

$(ARCH_OC_TARGETS) : %/libopenconnect.so : .openconnect-sources
	rm -rf $*/openconnect
	mkdir -p $*/openconnect
	cp -a openconnect $*/
	$(MAKE) -C $*/openconnect/android ARCH=$* NDK=$(ANDROID_NDK)
	cp -Lf $*/openconnect/android/$(TRIPLET_$*)/out/lib/libopenconnect.so $@
	cp -f $*/openconnect/android/$(TRIPLET_$*)/out/sbin/run_pie $*/

.openconnect-sources:
	$(MAKE) -C openconnect/android sources
	touch $@

# use openconnect to run make-standalone-toolchain.sh and install
# GnuTLS/nettle into the sysroot for us
$(ARCH_TOOLCHAIN_TARGETS) : %/toolchain/.installed : %/libopenconnect.so
	ln -sf openconnect/android/$(TRIPLET_$*)/toolchain $*/
	touch $@

openconnect-wrapper.jar:
	cd openconnect/java && ant
	cp openconnect/java/dist/$@ .

stoken-wrapper.jar:
	cd stoken/java && ant
	cp stoken/java/dist/$@ .

# note: this skips curl and libstoken as they rarely change
.PHONY: refresh
refresh:
	cd openconnect && \
		git remote update -p && \
		git checkout -f origin/master
	rev=`cd openconnect && git rev-parse --short HEAD`; \
		git commit openconnect -sm \
			"external: Sync with openconnect rev $$rev"
	$(MAKE) clean
	$(MAKE) install
