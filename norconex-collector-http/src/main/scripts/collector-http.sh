#!/bin/sh

cd $(dirname $0)
export ROOT_DIR=$(realpath $(dirname $0))

# Third-party libraries sometimes have to create and write to temporary files.
# By default those are created in your system "temp" folder
# (usually /tmp/ under Linux/Unix).
# To change the temporary location those libraries will use, add the
# following to the java command below (replacing the path):
#
#     -Djava.io.tmpdir=/path/to/tmp

java -Dlog4j.configuration="file:${ROOT_DIR}/log4j.properties" -Dfile.encoding=UTF8 -cp "./lib/*:./classes" com.norconex.collector.http.HttpCollector "$@"
