#!/bin/sh

script=$(readlink -f "$0")
scriptdir=`dirname "$script"`
cd "$scriptdir"

classpath=
for f in `ls ./lib/*.jar`
do
	classpath=${classpath}:${f}
done

./jre/bin/java \
	-enableassertions \
	-Xmx4g \
	-Xss2m \
	-cp ".:lang:${classpath}" \
	-Djava.library.path="lib" \
	${main_class} "$@"
