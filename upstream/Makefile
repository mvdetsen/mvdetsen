# Inspired by XWT's Makefile.upstream

version_gcc = 3.3.6

url_gcc-core = http://mirrors.kernel.org/gnu/gcc/gcc-$(version_gcc)/gcc-core-$(version_gcc).tar.gz
url_gcc-c++ = http://mirrors.kernel.org/gnu/gcc/gcc-$(version_gcc)/gcc-g++-$(version_gcc).tar.gz
#url_gcc-g77 = http://mirrors.kernel.org/gnu/gcc/gcc-$(version_gcc)/gcc-g77-$(version_gcc).tar.gz

patches_gcc = gcc-fixes.patch gcc-fdata-sections-bss.patch gcc-64bitclean.patch gcc-cooked-in-cmdline-opts.patch # gcc-3.3.6-fortran.patch

configure_gcc = --with-arch=mips1 --target=mips-unknown-elf --disable-threads --with-gnu-ld --with-gnu-as --with-newlib=yes --enable-sjlj-exceptions --enable-languages="c"
configure_gcc_step2 = $(configure_gcc) --enable-languages="c,c++" #,f77"
version_gpc = 20030830
#url_gpc = http://www.gnu-pascal.de/alpha/gpc-$(version_gpc).tar.gz
url_gpc = http://www.megacz.com/upstream/gpc-$(version_gpc).tar.gz
patches_gpc = $(patches_gcc) gpc-with-3.3.3.patch gpc-noutils.patch
configure_gpc = $(configure_gcc) --enable-languages="pascal"

version_binutils = 2.14
url_binutils = ftp://ftp.gnu.org/gnu/binutils/binutils-$(version_binutils).tar.gz
patches_binutils = binutils-no64.patch binutils-nocompilein.patch binutils-ld-jelf.patch binutils-cooked-in-cmdline-opts.patch
configure_binutils = --with-arch=mips1 --target=mips-unknown-elf

version_newlib = 1.11.0
url_newlib = ftp://sources.redhat.com/pub/newlib/newlib-$(version_newlib).tar.gz
patches_newlib = newlib-mips.patch newlib-tzset.patch newlib-malloc.patch newlib-nomemcpy.patch newlib-unix.patch newlib-unistd.patch
configure_newlib = --enable-multilib --target=mips-unknown-elf

url_openbsdglob = http://www.brianweb.net/xwt/openbsdglob.tar.gz
url_regex = http://arglist.com/regex/regex3.8a.tar.gz

url_linpack = http://www.math.iastate.edu/burkardt/f_src/linpack_bench/linpack_bench.f
version_zlib = 1.2.3
url_zlib = http://www.zlib.net/zlib-$(version_zlib).tar.gz

version_expat = 2.0.0
url_expat = http://umn.dl.sourceforge.net/sourceforge/expat/expat-$(version_expat).tar.gz
configure_expat = --host= --target=mips-unknown-elf

version_gmp = 4.1.4
url_gmp = ftp://ftp.gnu.org/gnu/gmp/gmp-$(version_gmp).tar.gz
configure_gmp = --host=mips-unknown-elf

darcs_gcclass = http://darcs.brianweb.net/gcclass
darcs_classgen = http://darcs.brianweb.net/classgen

.SECONDARY: # This is broken in gmake < 3.79.1

upstream = $(shell pwd)
root = $(shell dirname "`pwd`")
usr = $(upstream)/install

PATH := $(usr)/bin:$(PATH)
export PATH

# Solaris' patch doesn't work, use gnu patch on solaris
PATCH = $(shell test `uname` = SunOS && echo gpatch || echo patch)
TAR = $(shell test `uname` = SunOS && echo gtar || echo tar)

tasks/build_gcc: tasks/build_linker
tasks/build_newlib: tasks/build_gcc

cross_root := $(usr)/mips-unknown-elf

download := $(if $(DOWNLOAD_DIR),$(DOWNLOAD_DIR),$(upstream)/download)

tasks/download_%:
	if [ -z "$(url_$*)" ]; then echo "No url for $*" >&2; false; fi
	mkdir -p $(download)
	cd $(download) ; \
	if [ ! -e $(notdir $(url_$*)) ] ; then \
	  if ! curl -O $(url_$*) ; then \
	    rm -f $(notdir $(url_$*)) ; \
	    exit 1; \
	  fi \
	fi
	touch $@

tasks/extract_%: tasks/download_%
	if [ -e $@ ]; then echo "Error: $@ exists. something isn't right"; false; else true; fi
	mkdir -p build
	cd build && \
		gzip -dc  $(download)/$*-$(version_$*).tar.gz | $(TAR) -xf - && \
		rm -f $* && \
		ln -s $*-$(version_$*) $*
	touch $@

tasks/extract_darcs_%:
	if [ -e $@ ]; then echo "Error: $@ exists. something isn't right"; false; else true; fi
	mkdir -p build
	if [ -z "$(darcs_$*)" ]; then echo "No darcs url for $*" >&2; false; fi
	if [ -d "$(root)/_darcs" ]; then \
		cd build && darcs get --repo-name="$*" --verbose "$(darcs_$*)"; \
	else \
		mkdir -p "build/$*" && cd "build/$*"; \
		wget -nH -rl 16 -N -X _darcs $(darcs_$*); \
	fi
	touch $@

tasks/extract_darcs_classgen:
	mkdir -p build
	rm -rf build/classgen
	cp -a classgen build/
	touch $@

tasks/extract_darcs_gcclass:
	mkdir -p build
	rm -rf build/gcclass
	cp -a gcclass build/
	touch $@

update_darcs_%: tasks/extract_darcs_%
	if [ -d "build/$*/_darcs" ]; then \
		cd "build/$*" && darcs pull; \
	else \
		cd "build/$*" && wget -np -nH --cut-dirs=1 -rl 16 -N -X _darcs $(darcs_$*); \
	fi
	rm -f "tasks/build_darcs_$*" # to force a rebuild

tasks/build_darcs_%: tasks/extract_darcs_%
	cd "build/$*" && $(MAKE)
	touch $@

# Add extra nestedvm specific headers to include
tasks/build_extraheaders: $(upstream)/misc/extraheaders.sh tasks/build_newlib

	cd $(cross_root)/include && sh $<
	touch $@

top_lev_stuff := $(patsubst %,../build/org/ibex/nestedvm/%.o, crt0 support support_aux)

# HACK: Get the top level makefile to build the support stuff
$(top_lev_stuff):
	$(MAKE) -C .. $(top_lev_stuff:../%=%)

# Add our support stuff to libc
tasks/build_libc: tasks/build_newlib tasks/build_extraheaders tasks/build_regex tasks/build_openbsdglob misc/extraheaders.sh $(top_lev_stuff)
	mips-unknown-elf-ar sr $(cross_root)/lib/libc.a $(patsubst %,../build/org/ibex/nestedvm/%.o, support support_aux)
	mips-unknown-elf-ar sr $(cross_root)/lib/single-float/libc.a $(patsubst %,../build/org/ibex/nestedvm/%.o, support support_aux)
	rm -f $(cross_root)/lib/crt0.o
	cp $(root)/build/org/ibex/nestedvm/crt0.o $(cross_root)/lib/crt0.o

	touch $@

tasks/build_linker: tasks/build_binutils $(root)/src/org/ibex/nestedvm/linker.ld
	cp $(root)/src/org/ibex/nestedvm/linker.ld $(cross_root)/lib/ldscripts/nestedvm.ld
	for f in $(cross_root)/lib/ldscripts/elf32ebmip.*; do \
		rm -f "$$f"; \
		ln -s nestedvm.ld "$$f"; \
	done
	touch $@

clean_%:
	rm -rf "build/$(*)"*
	rm -f "tasks/build_$*" "tasks/patch_$*" "tasks/extract_$*"


tasks/download_gcc: tasks/download_gcc-core tasks/download_gcc-c++ #tasks/download_gcc-g77
	touch $@

tasks/extract_gcc: tasks/download_gcc
	if [ -e $@ ]; then echo "Error: $@ exists. something isn't right"; false; else true; fi
	mkdir -p build
	cd build && gzip -dc $(download)/gcc-core-$(version_gcc).tar.gz | $(TAR) -xf -
	cd build && gzip -dc $(download)/gcc-g++-$(version_gcc).tar.gz | $(TAR) -xf -
	#cd build && gzip -dc $(download)/gcc-g77-$(version_gcc).tar.gz | $(TAR) -xf -
	touch $@

tasks/patch_%: tasks/extract_%
	if [ -e $@ ]; then echo "Error: $@ exists. something isn't right"; false; else true; fi
	cd build/$*-$(version_$*) &&  \
	for p in $(patches_$*) end; do \
		[ "$$p" = "end" ] || $(PATCH) -p0 -l < ../../patches/$$p || exit 1; \
	done
	touch $@

tasks/build_%: tasks/patch_%
	mkdir -p $(usr)
	mkdir -p build/$*-obj && cd build/$*-obj && \
		../$*-$(version_$*)/configure --prefix=$(usr) $(configure_$*) && \
		$(MAKE) TARGET_CFLAGS="$(MIPS_CFLAGS)" && \
		$(MAKE) install
	touch $@

tasks/build_zlib: tasks/patch_zlib
	mkdir -p $(usr)
	cd build/zlib-$(version_zlib) && \
		AR="mips-unknown-elf-ar rc" CC=mips-unknown-elf-gcc ./configure --prefix=$(usr) $(configure_$*) && \
		$(MAKE) TARGET_CFLAGS="$(MIPS_CFLAGS)" && \
		$(MAKE) install
	touch $@

tasks/build_gcc_step2: tasks/patch_gcc tasks/build_libc
	mkdir -p $(usr)
	mkdir -p build/gcc-obj && cd build/gcc-obj && \
		echo | ../gcc-$(version_gcc)/configure --prefix=$(usr) $(configure_gcc_step2) && \
		$(MAKE) TARGET_CFLAGS="$(MIPS_CFLAGS)" && \
		$(MAKE) install
	touch $@

tasks/extract_gpc: tasks/download_gcc tasks/download_gpc
	if [ -e $@ ]; then echo "Error: $@ exists. something isn't right"; false; else true; fi
	mkdir -p build/gpc.extract
	cd build/gpc.extract && \
		gzip -dc $(download)/gcc-core-$(version_gcc).tar.gz | $(TAR) -xf - && \
		gzip -dc $(download)/gpc-$(version_gpc).tar.gz      | $(TAR) -xf - && \
		test ! -e gcc-$(version_gcc)/gcc/p && \
		mv gpc-$(version_gpc)/p gcc-$(version_gcc)/gcc/p
	mv build/gpc.extract/gcc-$(version_gcc) build/gpc-$(version_gpc)
	rm -rf build/gpc.extract
	touch $@

tasks/build_gpc: tasks/patch_gpc tasks/build_libc
	mkdir -p $(usr)
	 # We need to preapply this patch because gpc's configury doesn't like 3.3.3
	cd build/gpc-$(version_gpc) && cat gcc/version.c | grep -q GPC || $(PATCH) -p0 < gcc/p/diffs/gcc-3.3.diff

	 # echo | is to dismiss the gcc version warning
	mkdir -p build/gpc-obj && cd build/gpc-obj && \
		echo | ../gpc-$(version_gpc)/configure --prefix=$(usr) $(configure_gpc) && \
		$(MAKE) TARGET_CFLAGS="$(MIPS_CFLAGS)" && \
		cd gcc && $(MAKE) pascal.install

	touch $@

tasks/build_openbsdglob: tasks/download_openbsdglob tasks/build_newlib
	@mkdir -p $(usr)/mips-unknown-elf/{include,lib}
	cd build && gzip -dc $(download)/openbsdglob.tar.gz | $(TAR) -xf -
	cd build/openbsdglob && \
		mips-unknown-elf-gcc '-Dissetugid()=0' -DARG_MAX=65536 $(MIPS_CFLAGS) -I. -c -o glob.o glob.c && \
		mips-unknown-elf-gcc -O2 -Wall -Werror -I. -c -o fnmatch.o fnmatch.c && \
		mips-unknown-elf-ar cr libglob.a *.o && \
		mips-unknown-elf-ranlib libglob.a && \
		cp *.h $(usr)/mips-unknown-elf/include && \
		cp *.a $(usr)/mips-unknown-elf/lib
	touch $@

tasks/build_regex: tasks/download_regex tasks/build_newlib
	@mkdir -p $(usr)/mips-unknown-elf/{include,lib}
	mkdir -p build/regex build/regex/fake
	cd build && \
		tar xvzf $(download)/regex3.8a.tar.gz && cd regex && \
		make CC=mips-unknown-elf-gcc CFLAGS="-I. $(MIPS_CFLAGS)" regcomp.o regexec.o regerror.o regfree.o && \
		mips-unknown-elf-ar cr libregex.a regcomp.o regexec.o regerror.o regfree.o && \
		mips-unknown-elf-ranlib libregex.a && \
		cp regex.h $(usr)/mips-unknown-elf/include && \
		cp libregex.a $(usr)/mips-unknown-elf/lib 
	touch $@

tasks/build_gmp: tasks/patch_gmp tasks/build_libc
	cd build/gmp && \
		CFLAGS="$(MIPS_CFLAGS)" LDFLAGS="$(MIPS_LDFLAGS)" \
		./configure --prefix=$(usr) $(configure_gmp) && \
		make && \
		make install exec_prefix=$(usr)/mips-unknown-elf includedir=$(usr)/mips-unknown-elf/include
	touch $@
	
#
# Tests
# These are simply here for convenience. They aren't required 
# to build or run mips2java
#

version_libjpeg = 6b
url_libjpeg = http://www.ijg.org/files/jpegsrc.v$(version_libjpeg).tar.gz

version_libmspack = 20030726
url_libmspack = http://www.kyz.uklinux.net/downloads/libmspack-$(version_libmspack).tar.gz
patches_libmspack = libmspack.patch

version_freetype = 2.1.4
url_freetype = http://easynews.dl.sourceforge.net/sourceforge/freetype/freetype-$(version_freetype).tar.gz
patches_freetype = freetype.patch ft-nostdio.patch

version_boehmgc = 6.3alpha2
#url_boehmgc = http://www.hpl.hp.com/personal/Hans_Boehm/gc/gc_source/gc$(version_boehmgc).tar.gz
url_boehmgc = http://www.brianweb.net/misc/gc$(version_boehmgc).tar.gz
patches_boehmgc = boehm-gc.patch

version_busybox = 1.00-pre9
url_busybox = http://busybox.net/downloads/busybox-$(version_busybox).tar.gz
patches_busybox = busybox.patch

version_texinputs = 1.0
url_texinputs = http://www.megacz.com/tmp/texinputs-$(version_texinputs).tgz

version_samba = 3.0.4
url_samba = http://us4.samba.org/samba/ftp/samba-$(version_samba).tar.gz
patches_samba = samba.patch

tasks/extract_libjpeg: tasks/download_libjpeg
	mkdir -p build
	cd build && \
		gzip -dc $(download)/jpegsrc.v$(version_libjpeg).tar.gz | $(TAR) -xf - && \
		mv jpeg-$(version_libjpeg) libjpeg-$(version_libjpeg) && \
		rm -f libjpeg && \
		ln -s libjpeg-$(version_libjpeg)  libjpeg
	touch $@    

tasks/build_libjpeg: tasks/patch_libjpeg tasks/build_libc
	cd build/libjpeg-$(version_libjpeg) && \
		./configure && \
		make CC="mips-unknown-elf-gcc" \
			AR="mips-unknown-elf-ar rc" \
			AR2="mips-unknown-elf-ranlib" \
			CFLAGS="$(MIPS_CFLAGS)" \
			LDFLAGS="$(MIPS_LDFLAGS)"
	touch $@    

tasks/extract_libmspack: tasks/download_libmspack tasks/build_libc
	mkdir -p build
	cd build && \
		gzip -dc $(download)/libmspack-$(version_libmspack).tar.gz | $(TAR) -xf - && \
		mv libmspack libmspack-$(version_libmspack) && \
		rm -f libmspack && \
		ln -s libmspack-$(version_libmspack)  libmspack
	touch $@    

tasks/build_libmspack: tasks/patch_libmspack tasks/build_libc
	cd build/libmspack-$(version_libmspack)/mspack && \
		make CC="mips-unknown-elf-gcc" \
			AR="mips-unknown-elf-ar" \
			RANLIB="mips-unknown-elf-ranlib" \
			OPTIM="$(MIPS_CFLAGS)"
	touch $@    

tasks/build_freetype: tasks/patch_freetype tasks/build_libc
	cd build/freetype-$(version_freetype) && \
		$(MAKE) setup ansi && \
		$(MAKE) CC="mips-unknown-elf-gcc" \
		    CFLAGS="-c $(MIPS_CFLAGS)" \
			AR="mips-unknown-elf-ar" \
			RANLIB="mips-unknown-elf-ranlib" && \
		mips-unknown-elf-ranlib objs/libfreetype.a
	touch $@    

tasks/extract_boehmgc: tasks/download_boehmgc
	mkdir -p build
	cd build && \
		rm -rf gc$(version_boehmgc) && \
		gzip -dc $(download)/gc$(version_boehmgc).tar.gz | $(TAR) -xf - && \
		rm -f boehmgc boehmgc-$(version_boehmgc) && \
		ln -s gc$(version_boehmgc)  boehmgc && \
		ln -s gc$(version_boehmgc)  boehmgc-$(version_boehmgc)
	touch $@

tasks/build_boehmgc: tasks/patch_boehmgc tasks/build_libc
	cd build/gc$(version_boehmgc) && \
		CC="mips-unknown-elf-gcc" CFLAGS="$(MIPS_CFLAGS)" LDFLAGS="$(MIPS_LDFLAGS)" \
		./configure --host=mips-unknown-elf --disable-shared --disable-threads && \
		make && \
		make gctest 
	touch $@

tasks/build_busybox: tasks/patch_busybox tasks/build_libc
	cd build && \
		rm -f busybox && \
		ln -s busybox-$(version_busybox) busybox
	cd build/busybox && $(MAKE) LDFLAGS="$(MIPS_LDFLAGS)" CFLAGS_EXTRA="$(MIPS_CFLAGS) -DNESTEDVM -g"
	touch $@

# TeX Stuff

tasks/extract_tex_tangle:
	@mkdir -p build/tex
	cd build/tex && curl -o tex.web http://www.math.uni.wroc.pl/~hebisch/tex_p/tex.web
	touch $@

tasks/build_tex_tangle: tasks/extract_tex_tangle tasks/build_gpc
	@mkdir -p build/tex
	mips-unknown-elf-gpc $(MIPS_PCFLAGS) build/tex/tangle.p $(MIPS_LDFLAGS) -o build/tex/tangle.mips
	touch $@

tasks/build_tex: tasks/build_tex_tangle tasks/build_gpc tasks/download_texinputs
	@mkdir -p build/tex

	 # HACK: Build Tangel.class
	make -C $(root) build/tests/Tangle.class

	rm -f build/tex/tex.p build/tex/tex.pool
	cp misc/tex.ch build/tex/tex.ch
	cd build/tex && \
		$(TAR) xfz $(download)/texinputs-$(version_texinputs).tgz && \
		java -cp $(root)/build:$(upstream)/build/classgen/build tests.Tangle \
			--gpc-rts=-nPascalfile:tex.p    \
			--gpc-rts=-npool:tex.pool       \
			--gpc-rts=-nwebfile:tex.web     \
			--gpc-rts=-nchangefile:tex.ch   \
			&& \
		mips-unknown-elf-gpc $(MIPS_PCFLAGS) tex.p $(MIPS_LDFLAGS) -o tex.mips
	touch $@

tasks/build_samba: tasks/patch_samba
	cd build/samba-$(version_samba)/source && \
		test -e config.status || ./configure --host=mips-unknown-elf && \
		fgrep -q '#define HAVE_GETTIMEOFDAY_TZ' include/config.h || \
			echo '#define HAVE_GETTIMEOFDAY_TZ 1' >> include/config.h && \
		make LIBS="-lglob" CFLAGS="$(MIPS_CFLAGS)" LDFLAGS="$(MIPS_LDFLAGS)" \
		     CONFIGFILE="smb.conf" proto_exists bin/ntlm_auth
	touch $@
