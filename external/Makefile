NDK := /opt/android-ndk-r9
GCCVER := 4.8

LIBS := ../libs
ARCH_LIST := arm x86 mips
NDK_ARCH_LIST := armeabi x86 mips

.PHONY: all clean
all: $(addsuffix /libopenconnect.so,$(ARCH_LIST)) openconnect-wrapper.jar
	mkdir -p $(addprefix $(LIBS)/,$(NDK_ARCH_LIST))
	cp arm/libopenconnect.so $(LIBS)/armeabi/
	cp mips/libopenconnect.so $(LIBS)/mips/
	cp x86/libopenconnect.so $(LIBS)/x86/
	cp openconnect-wrapper.jar $(LIBS)/

clean:
	rm -rf $(ARCH_LIST) openconnect-wrapper.jar

%/libopenconnect.so: .openconnect-sources
	rm -rf $*
	mkdir $*
	cp -a openconnect $*/
	$(MAKE) -C $*/openconnect/android ARCH=$* NDK=$(NDK) GCCVER=$(GCCVER)
	cp -Lf $*/openconnect/android/*/openconnect/.libs/libopenconnect.so $@

.openconnect-sources:
	$(MAKE) -C openconnect/android sources
	touch $@

openconnect-wrapper.jar:
	cd openconnect/java && ant
	cp openconnect/java/dist/$@ .
