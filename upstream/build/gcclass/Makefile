JAVAC = javac
sources = $(shell find src -name '*.java')
classes = $(sources:src/%.java=build/%.class)

all: $(classes)

$(classes): $(sources)
	@mkdir -p build
	$(JAVAC) -classpath lib/bcel-5.1.jar -d build $(sources)

Test.class: Test.java
	javac $<
	
test: all Test.class
	java -cp build:lib/bcel-5.1.jar com.brian_web.gcclass.GCClass . stripped Test.main

clean: 
	rm -rf build/*
