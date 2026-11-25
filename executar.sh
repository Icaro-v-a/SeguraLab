#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ ! -f lib/sqlite-jdbc-3.54.1.0.jar ] || [ ! -f lib/slf4j-api-2.0.17.jar ] || [ ! -f lib/slf4j-nop-2.0.17.jar ]; then java ferramentas/PrepararDependencias.java; fi
mkdir -p build/classes
javac --release 17 -encoding UTF-8 -d build/classes src/br/edu/seguralab/App.java
exec java -cp 'build/classes:lib/*' br.edu.seguralab.App
