#!/bin/sh
./sbt clean universal:packageZipTarball
docker build --no-cache -t formbuilder/formbuilderserver:${TAG_NAME:-latest} .
