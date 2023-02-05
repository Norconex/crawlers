#!/bin/sh

cd $(dirname $0)
export IMPORTER_ROOT_DIR=$(realpath $(dirname $0))

# Third-party libraries sometimes have to create and write to temporary files.
# By default those are created in your system "temp" folder
# (usually /tmp/ under Linux/Unix).
# To change the temporary location those libraries will use, add the
# following to the java command below (replacing the path):
#
#     -Djava.io.tmpdir=/path/to/tmp

# If you are experiencing memory problems or simply wish to increase crawling
# performance you can specify the amount of memory allocated by increasing
# the Java heap space. You can do so by adding the following to the Java
# command below (using 2G as an example):  
#
#     -Xmx2G

java -Dlog4j2.configurationFile="file:${IMPORTER_ROOT_DIR}/log4j.properties" -Dfile.encoding=UTF8 -cp "./lib/*:./classes" com.norconex.importer.Importer "$@"
