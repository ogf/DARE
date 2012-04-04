#!/bin/bash
DIR="$( cd "$( dirname "$0" )" && pwd )"
DARE="$DIR/.."

java -jar marginalia-0.8.0-SNAPSHOT-standalone.jar -d $DIR/docs -f backend.tex $DARE/DARE-backend/src/backend/core.clj
