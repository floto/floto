#!/bin/bash
set -e

CWD=`dirname "$0"`
cd "$CWD"

CWD=$(pwd)

KILL_ARGUMENT="-XX:OnOutOfMemoryError=kill -9 %p"

if [ -f set-env.sh ]; then
    source set-env.sh
fi

BASE=$CWD/..
(cd $BASE && java -Dlogging.facility=floto-server -cp "lib/*:." "${KILL_ARGUMENT}" ${JAVA_ARGUMENTS} io.github.floto.server.FlotoServer ${ARGUMENTS} "$@")

