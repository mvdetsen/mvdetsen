#!/bin/sh

if [ ! -e jdk11-classes.zip ]; then
	echo "You need to get classes.zip from a jdk 1.1 dist and place it" 1>&2
	echo "in the same directory as this script named jdk11-classes.zip." 1>&2
	echo "You can find old jdk distributions on Sun's website." 1>&2
	exit 1
fi

if [ "$#" -lt 2 ]; then
	echo "Usage: $0 classpath entrypoint 1 [... [entrypoint n]]" 1>&2
	exit 1
fi

mkdir -p stripped

cp="$1"
shift
java -cp build:upstream/bcel-5.1/bcel-5.1.jar \
 	com.brian_web.gcclass.GCClass \
 	"=jdk11-classes.zip:$cp" stripped "$@"

