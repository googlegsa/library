#!/bin/sh
set -e # fail if any command fails
mkdir -p classes
javac -d classes src/*/*java src/*/tests/*java
cp src/logging.properties classes/
