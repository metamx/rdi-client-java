#!/bin/sh -e
exec java -cp "lib/core/*" com.metamx.rdiclient.example.FileInputMain "$@"
