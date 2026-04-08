#!/bin/sh
#
# Gradle start up script for UN*X
#

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
GRADLE_OPTS="${GRADLE_OPTS:-"-Dfile.encoding=UTF-8"}"
exec "$APP_HOME/gradle/wrapper/gradle-wrapper" "$@"
