#!/bin/sh

# Gradle start up script (minimal) for local wrapper use.
APP_HOME=$(cd "$(dirname "$0")" >/dev/null && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVA_HOME/bin/java" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
