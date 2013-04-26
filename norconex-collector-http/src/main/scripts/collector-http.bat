@echo off
cd %~dp0

java -Dfile.encoding=UTF8 -cp "./lib/*;./classes" com.norconex.collector.http.HttpCollector %*

