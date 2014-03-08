NDK := /opt/android-ndk-r9
GCCVER := 4.8

LIBS := ../libs
ASSETS := ../assets/raw

ARCH_LIST := arm x86 mips

NDK_ARCH_arm := armeabi
TRIPLET_arm := arm-linux-androideabi
CFLAGS_arm := -mthumb -Os

NDK_ARCH_x86 := x86
TRIPLET_x86 := i686-linux-android
CFLAGS_x86 := -Os

NDK_ARCH_mips := mips
TRIPLET_mips := mipsel-linux-android
CFLAGS_mips := -Os

JAR_LIST := openconnect-wrapper.jar stoken-wrapper.jar
PKG_LIST := curl

ARCH_TARGETS := $(addprefix arch-,$(ARCH_LIST))
ARCH_TOOLCHAIN_SYMLINKS := $(addprefix arch-toolchain-,$(ARCH_LIST))

ARCH_CURL_TARGETS := $(addsuffix /curl,$(ARCH_LIST))
ARCH_STOKEN_TARGETS := $(addsuffix /libstoken.so,$(ARCH_LIST))
ARCH_OC_TARGETS := $(addsuffix /libopenconnect.so,$(ARCH_LIST))
ARCH_TOOLCHAIN_TARGETS := $(addsuffix /toolchain/.installed,$(ARCH_LIST))

INSTALL_ARCH_TARGETS := $(addprefix install-arch-,$(ARCH_LIST))
INSTALL_JAR_TARGETS := $(addprefix install-jar-,$(JAR_LIST))

CURL_VER := 7.34.0

TOPDIR := $(shell pwd)
set_build_env = PATH=$(TOPDIR)/$(1)/toolchain/bin:$$PATH \
	PKG_CONFIG_LIBDIR=$(TOPDIR)/$(1)/toolchain/sysroot/usr/lib/pkgconfig

.PHONY: install
install: $(INSTALL_ARCH_TARGETS) $(INSTALL_JAR_TARGETS)

.PHONY: clean
clean:
	rm -rf $(ARCH_LIST) $(JAR_LIST) $(PKG_LIST)

.PHONY: $(INSTALL_JAR_TARGETS)
$(INSTALL_JAR_TARGETS) : install-jar-% : %
	mkdir -p $(LIBS)
	cp $* $(LIBS)/

.PHONY: $(INSTALL_ARCH_TARGETS)
$(INSTALL_ARCH_TARGETS) : install-arch-% : arch-%
	mkdir -p $(LIBS)/$(NDK_ARCH_$*)
	cp $*/libopenconnect.so $(LIBS)/$(NDK_ARCH_$*)/
	cp $*/libstoken.so $(LIBS)/$(NDK_ARCH_$*)/
	mkdir -p $(ASSETS)/$(NDK_ARCH_$*)
	cp $*/curl $(ASSETS)/$(NDK_ARCH_$*)/

.PHONY: $(ARCH_TARGETS)
$(ARCH_TARGETS) : arch-% : %/curl %/libstoken.so %/libopenconnect.so

curl/configure:
	mkdir -p curl
	tar -C curl --strip 1 -jxf dist/curl-$(CURL_VER).tar.bz2
	touch $@

$(ARCH_CURL_TARGETS) : %/curl : arch-toolchain-% curl/configure
	mkdir -p $*/build-curl $*/out
	cd $*/build-curl && \
		$(call set_build_env,$*) $(TOPDIR)/curl/configure \
			--prefix=/ --host=$(TRIPLET_$*) --disable-shared \
			--enable-static --with-ipv6 --with-gnutls \
			CFLAGS="$(CFLAGS_$*)" \
			ac_cv_lib_gnutls_gnutls_check_version=yes
	$(call set_build_env,$*) $(MAKE) -C $*/build-curl
	$(call set_build_env,$*) $(MAKE) -C $*/build-curl install-strip \
		DESTDIR=$(TOPDIR)/$*/out
	cp -L $*/out/bin/curl $*/

stoken/configure:
	cd stoken && bash autogen.sh
	touch $@

$(ARCH_STOKEN_TARGETS) : %/libstoken.so : arch-toolchain-% stoken/configure
	mkdir -p $*/build-stoken $*/out
	cd $*/build-stoken && \
		$(call set_build_env,$*) $(TOPDIR)/stoken/configure \
			--prefix=/ --host=$(TRIPLET_$*) --disable-static \
			--enable-shared --without-gtk \
			--with-java=$*/toolchain/sysroot/include \
			--enable-jni-standalone
	$(call set_build_env,$*) $(MAKE) -C $*/build-stoken
	$(call set_build_env,$*) $(MAKE) -C $*/build-stoken install-strip \
		DESTDIR=$(TOPDIR)/$*/out
	cp -L $*/out/lib/libstoken.so $*/

$(ARCH_OC_TARGETS) : %/libopenconnect.so : .openconnect-sources
	rm -rf $*/openconnect
	mkdir -p $*/openconnect
	cp -a openconnect $*/
	$(MAKE) -C $*/openconnect/android ARCH=$* NDK=$(NDK) GCCVER=$(GCCVER)
	cp -Lf $*/openconnect/android/$(TRIPLET_$*)/out/lib/libopenconnect.so $@

.openconnect-sources:
	$(MAKE) -C openconnect/android sources
	touch $@

# use openconnect to run make-standalone-toolchain.sh and install
# GnuTLS/libtomcrypt into the sysroot for us
$(ARCH_TOOLCHAIN_TARGETS) : %/toolchain/.installed : %/libopenconnect.so
	ln -s openconnect/android/$(TRIPLET_$*)/toolchain $*/
	touch $@

.PHONY: $(ARCH_TOOLCHAIN_SYMLINKS)
$(ARCH_TOOLCHAIN_SYMLINKS) : arch-toolchain-% : %/toolchain/.installed

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
	git commit -sm "libs: Sync with latest openconnect sources" \
		$(LIBS)/openconnect*.jar $(LIBS)/*/libopenconnect*.so
