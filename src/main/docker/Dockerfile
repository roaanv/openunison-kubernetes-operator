#FROM ubuntu:22.04
#FROM eclipse-temurin:17.0.3_7-jre-focal
FROM ubuntu:20.04

MAINTAINER Tremolo Security, Inc. - Docker <docker@tremolosecurity.com>

RUN apt-get update;apt-get -y install curl apt-transport-https gnupg openjdk-17-jre-headless

RUN groupadd -r ouoperator -g 433 && \
    mkdir /usr/local/ouoperator && \
    useradd -u 431 -r -g ouoperator -d /usr/local/ouoperator -s /sbin/nologin -c "OpenUnison Operator Docker image user" ouoperator && \
    chown -R ouoperator:ouoperator /usr/local/ouoperator 

RUN curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add - 

ADD target/operator-*.jar /usr/local/ouoperator/ouoperator.jar

USER 431

CMD ["/usr/bin/java", "-jar", "/usr/local/ouoperator/ouoperator.jar"]
