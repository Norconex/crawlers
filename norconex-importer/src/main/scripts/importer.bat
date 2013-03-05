@echo off
cd %~dp0

start java -cp "./lib/*;./classes" com.norconex.importer.Importer %*

