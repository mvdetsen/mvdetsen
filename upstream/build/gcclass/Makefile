JAVAC = javac
sources = $(shell find src -name '*.java')
classes = $(sources:src/%.java=build/%.class)

bcel_jar = upstream/bcel-5.1/bcel-5.1.jar

TAR = $(test `uname` = SunOS && echo gtar || echo tar)

all: $(classes)

$(classes): $(sources) $(bcel_jar)
	@mkdir -p build
	$(JAVAC) -classpath $(bcel_jar) -d build $(sources)

Test.class: Test.java
	javac $<
	
test: all Test.class
	java -cp build:$(bcel_jar) com.brian_web.gcclass.GCClass . stripped Test.main

$(bcel_jar):
	mkdir -p upstream
	curl http://mirrors.mix5.com/apache/jakarta/bcel/binaries/bcel-5.1.tar.gz | gzip -dc | $(TAR) -xf - -C upstream

clean: 
	rm -rf build/*
