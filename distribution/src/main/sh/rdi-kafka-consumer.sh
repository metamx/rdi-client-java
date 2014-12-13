#!/bin/sh -e
exec java -cp "conf:lib/kafka/*" com.metamx.rdiclient.kafka.Main "$@"
