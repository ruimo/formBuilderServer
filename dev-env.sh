#!/bin/bash

docker run --rm -it -e "TZ=Asia/Tokyo" -p 9000:9000 -v `pwd`:/var/home -u `id -u` --entrypoint=/bin/bash formbuilder/formbuilderserver $*
