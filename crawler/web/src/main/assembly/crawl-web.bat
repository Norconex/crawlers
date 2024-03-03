@echo off
REM Copyright 2024 Norconex Inc.
REM 
REM Licensed under the Apache License, Version 2.0 (the "License");
REM you may not use this file except in compliance with the License.
REM You may obtain a copy of the License at
REM 
REM    http://www.apache.org/licenses/LICENSE-2.0
REM 
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.
cd %~dp0
set ROOT_DIR=%~dp0

REM Third-party libraries sometimes have to create and write to temporary files.
REM By default those are created in your system "temp" folder 
REM (usually defined under %TEMP% variable in Windows).
REM To change the temporary location those libraries will use, add the
REM following to the java command below (replacing the path):
REM
REM     -Djava.io.tmpdir="C:\temp"

REM If you are experiencing memory problems or simply wish to increase crawling
REM performance you can specify the amount of memory allocated by increasing
REM the Java heap space. You can do so by adding the following to the Java
REM command below (using 2G as an example):  
REM
REM     -Xmx2G

REM For advanced users, JMX monitoring can be enabled by adding the following 
REM to the java command below: 
REM 
REM     -DenableJMX=true


java -Dlog4j2.configurationFile="file:///%ROOT_DIR%log4j2.xml" -Dfile.encoding=UTF8 -cp "./lib/*" com.norconex.crawler.web.WebCrawlSession %*
