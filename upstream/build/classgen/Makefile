JAVAC = javac -source 1.3 -target 1.3
sources = $(shell find src -name '*.java')
classes = $(sources:src/%.java=build/%.class)
jar_classes = $(classes)

all: $(classes)

$(classes): $(sources) 
	@mkdir -p build
	$(JAVAC) -d build $(sources)

test: $(classes)
	javac Poop.java && java -cp build:. org.ibex.classgen.JSSA Poop

clean: 
	rm -rf build/*

.PHONY: doc
doc: doc/index.html

doc/index.html: $(sources) src/org/ibex/classgen/package.html
	mkdir -p doc
	javadoc -d doc $(sources)

sizecheck:
	@for c in $(jar_classes); do \
		for f in `echo $$c|sed 's,\.class$$,,;'`*.class; do gzip -c $$f; done | wc -c | tr -d '\n'; \
		echo -e "\t`echo $$c | sed 's,build/org/ibex,,;s,\.class$$,,;s,/,.,g;'`"; \
	done | sort -rn | awk '{ sum += $$1; print }  END { print sum,"Total"; }'
