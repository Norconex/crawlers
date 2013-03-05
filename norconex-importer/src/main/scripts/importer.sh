#!/bin/sh

cd $(dirname $0)

java -cp "./lib/*:./classes" com.norconex.importer.Importer "$@"
