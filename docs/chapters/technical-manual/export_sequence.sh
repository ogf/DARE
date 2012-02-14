#!/bin/bash
DIR="$( cd "$( dirname "$0" )" && pwd )"
find $DIR/diagrams/sequence/ -type f -not -iname '*.*' -exec sdedit -o \{\}.png -t png \{\} \;
