JAVAC = javac -source 1.3 -target 1.3
sources = $(shell find src -name '*.java')
classes = $(sources:src/%.java=build/%.class)

bcel_jar = upstream/bcel-5.2/bcel-5.2.jar

TAR = $(shell test `uname` = SunOS && echo gtar || echo tar)

all: $(classes)

$(classes): $(sources) $(bcel_jar)
	@mkdir -p build
	$(JAVAC) -classpath $(bcel_jar) -d build $(sources)

Test.class: Test.java
	javac $<
	
test: all Test.class
	java -cp build:$(bcel_jar) com.brian_web.gcclass.GCClass . stripped Test.main
	java -cp stripped Test

$(bcel_jar):
	mkdir -p upstream
	curl http://apache.forbigweb.com/jakarta/bcel/binaries/bcel-5.2.tar.gz | gzip -dc | $(TAR) -xf - -C upstream

clean: 
	rm -rf build/*
