FROM ubuntu:22.04

COPY crawler/web/target/nx-crawler-web-4.0.0-SNAPSHOT.zip /nxer/
COPY committer/elasticsearch/target/nx-committer-elasticsearch-4.0.0.zip /nxer/

RUN apt-get -y update && \
    apt-get install -y nano && \
    apt-get install -y curl && \
    apt-get install -y iputils-ping && \
    apt-get install -y unzip && \
    curl -LO https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.deb && \
    dpkg -i jdk-17_linux-x64_bin.deb && \    
    unzip /nxer/nx-crawler-web-4.0.0-SNAPSHOT.zip -d /nxer/ && \
    rm *.deb && \
    rm /nxer/*.zip