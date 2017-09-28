#!/bin/sh
export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
./sbt clean universal:packageZipTarball
docker build --no-cache -t formbuilder/formbuilderserver:${TAG_NAME:-latest} .
