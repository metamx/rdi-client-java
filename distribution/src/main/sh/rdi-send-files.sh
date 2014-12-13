#!/bin/sh -e
exec java -cp "conf:lib/core/*" com.metamx.rdiclient.example.FileInputMain "$@"
