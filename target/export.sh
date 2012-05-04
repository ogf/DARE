#!/bin/bash

DEST=$1
BASE_PORT=$2
CONCURRENCY=$3
MONGOHOST=$4
DIR="$( cd "$( dirname "$0" )" && pwd )"

if [[ -s "$HOME/.rvm/scripts/rvm" ]] ; then
    source "$HOME/.rvm/scripts/rvm"
fi
cat > env.txt <<EOF
MONGO_HOST=$MONGOHOST
EOF

foreman export upstart $DEST -f Procfile.production -a dare -u dare -c $CONCURRENCY -p $BASE_PORT -e env.txt
rm env.txt
sed -i -r -e 's/cd [^ ]+;/cd \/opt\/dare;/' $DEST/*
tar zcf $DEST.tar.gz -C $DEST .
rm -R $DEST
