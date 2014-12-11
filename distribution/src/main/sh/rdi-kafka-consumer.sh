#!/bin/sh -e
exec java -cp "lib/kafka/*" com.metamx.rdiclient.kafka.Main "$@"
