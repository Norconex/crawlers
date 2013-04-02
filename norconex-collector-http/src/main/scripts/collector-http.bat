@echo off
cd %~dp0

java -cp "./lib/*;./classes" com.norconex.collector.http.HttpCollector %*

