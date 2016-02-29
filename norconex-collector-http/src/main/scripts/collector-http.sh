#!/bin/sh

cd $(dirname $0)
export ROOT_DIR=$(dirname $0)

java -Dlog4j.configuration="file:${ROOT_DIR}/log4j.properties" -Dfile.encoding=UTF8 -cp "./lib/*:./classes" com.norconex.collector.http.HttpCollector "$@"
