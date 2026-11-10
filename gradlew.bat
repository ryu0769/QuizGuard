@rem Gradle start up script (minimal) for local wrapper use.
@if "%JAVA_HOME%"=="" set JAVA_HOME=%ProgramFiles%\Java\jdk
@set APP_HOME=%~dp0
@set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
@"%JAVA_HOME%\bin\java.exe" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
