#!/bin/bash
DIR="$( cd "$( dirname "$0" )" && pwd )"
find $DIR -mindepth 2 -type f -iname '*.sh' -type f -executable -exec {} \;
