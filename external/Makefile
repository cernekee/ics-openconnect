NDK := /opt/android-ndk-r9
GCCVER := 4.8

LIBS := ../libs
ASSETS := ../assets/raw

ARCH_LIST := arm x86 mips
NDK_ARCH_arm := armeabi
NDK_ARCH_x86 := x86
NDK_ARCH_mips := mips

JAR_LIST := openconnect-wrapper.jar

ARCH_TARGETS := $(addprefix arch-,$(ARCH_LIST))
ARCH_UTILS_TARGETS := $(addprefix arch-utils-,$(ARCH_LIST))
ARCH_CURL_TARGETS := $(addsuffix /curl,$(ARCH_LIST))
ARCH_OC_TARGETS := $(addsuffix /libopenconnect.so,$(ARCH_LIST))

INSTALL_ARCH_TARGETS := $(addprefix install-arch-,$(ARCH_LIST))
INSTALL_JAR_TARGETS := $(addprefix install-jar-,$(JAR_LIST))

.PHONY: install
install: $(INSTALL_ARCH_TARGETS) $(INSTALL_JAR_TARGETS)

.PHONY: clean
clean:
	$(MAKE) -C utils clean
	rm -rf $(ARCH_LIST) $(JAR_LIST)

.PHONY: $(INSTALL_JAR_TARGETS)
$(INSTALL_JAR_TARGETS) : install-jar-% : %
	mkdir -p $(LIBS)
	cp $* $(LIBS)/

.PHONY: $(INSTALL_ARCH_TARGETS)
$(INSTALL_ARCH_TARGETS) : install-arch-% : arch-%
	mkdir -p $(LIBS)/$(NDK_ARCH_$*)
	cp $*/libopenconnect.so $(LIBS)/$(NDK_ARCH_$*)/
	mkdir -p $(ASSETS)/$(NDK_ARCH_$*)
	cp $*/curl $(ASSETS)/$(NDK_ARCH_$*)/

.PHONY: $(ARCH_TARGETS)
$(ARCH_TARGETS) : arch-%: %/libopenconnect.so %/curl

$(ARCH_CURL_TARGETS) : %/curl : arch-utils-%
	mkdir -p $*
	cp utils/$*/curl $*/

$(ARCH_OC_TARGETS) : %/libopenconnect.so : .openconnect-sources
	rm -rf $*/openconnect
	mkdir -p $*/openconnect
	cp -a openconnect $*/
	$(MAKE) -C $*/openconnect/android ARCH=$* NDK=$(NDK) GCCVER=$(GCCVER)
	cp -Lf $*/openconnect/android/*/openconnect/.libs/libopenconnect.so $@

.openconnect-sources:
	$(MAKE) -C openconnect/android sources
	touch $@

.PHONY: $(ARCH_UTILS_TARGETS)
$(ARCH_UTILS_TARGETS) : arch-utils-% : utils-sources
	$(MAKE) -C utils ARCH=$* NDK=$(NDK) GCCVER=$(GCCVER)

.PHONY: utils-sources
utils-sources:
	$(MAKE) -C utils sources

openconnect-wrapper.jar:
	cd openconnect/java && ant
	cp openconnect/java/dist/$@ .

# note: this skips curl as it rarely changes
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
	git checkout -f ../assets
	git commit ../libs -m "libs: Sync with latest openconnect sources"
