NDK := /opt/android-ndk-r9
GCCVER := 4.8

LIBS := ../libs
ASSETS := ../assets/raw
ARCH_LIST := arm x86 mips
NDK_ARCH_LIST := armeabi x86 mips

.PHONY: all clean
all: $(addsuffix /libopenconnect.so,$(ARCH_LIST)) \
     $(addsuffix /curl,$(ARCH_LIST)) \
     openconnect-wrapper.jar
	mkdir -p $(addprefix $(LIBS)/,$(NDK_ARCH_LIST))
	mkdir -p $(addprefix $(ASSETS)/,$(NDK_ARCH_LIST))
	cp arm/libopenconnect.so $(LIBS)/armeabi/
	cp arm/curl $(ASSETS)/armeabi/
	cp mips/libopenconnect.so $(LIBS)/mips/
	cp mips/curl $(ASSETS)/mips/
	cp x86/libopenconnect.so $(LIBS)/x86/
	cp x86/curl $(ASSETS)/x86/
	cp openconnect-wrapper.jar $(LIBS)/

clean:
	$(MAKE) -C utils clean
	rm -rf $(ARCH_LIST) openconnect-wrapper.jar

%/libopenconnect.so: .openconnect-sources
	rm -rf $*/openconnect
	mkdir -p $*/openconnect
	cp -a openconnect $*/
	$(MAKE) -C $*/openconnect/android ARCH=$* NDK=$(NDK) GCCVER=$(GCCVER)
	cp -Lf $*/openconnect/android/*/openconnect/.libs/libopenconnect.so $@

.openconnect-sources:
	$(MAKE) -C openconnect/android sources
	touch $@

.PHONY: %/utils
%/utils: utils-sources
	$(MAKE) -C utils ARCH=$* NDK=$(NDK) GCCVER=$(GCCVER)

%/curl: %/utils
	mkdir -p $*
	cp utils/$*/curl $*/

.PHONY: utils-sources
utils-sources:
	$(MAKE) -C utils sources

openconnect-wrapper.jar:
	cd openconnect/java && ant
	cp openconnect/java/dist/$@ .
