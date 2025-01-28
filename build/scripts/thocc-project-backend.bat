@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  thocc-project-backend startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and THOCC_PROJECT_BACKEND_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Dio.ktor.development=false"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\thocc-project-backend-0.0.1.jar;%APP_HOME%\lib\ktor-server-content-negotiation-jvm-3.0.3.jar;%APP_HOME%\lib\koin-ktor-3.5.6.jar;%APP_HOME%\lib\ktor-server-netty-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-server-core-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-serialization-kotlinx-json-jvm-3.0.3.jar;%APP_HOME%\lib\koin-logger-slf4j-3.5.6.jar;%APP_HOME%\lib\ktorm-support-postgresql-4.1.1.jar;%APP_HOME%\lib\ktorm-core-4.1.1.jar;%APP_HOME%\lib\kotlin-reflect-2.0.21.jar;%APP_HOME%\lib\ktor-client-cio-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-client-logging-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-client-content-negotiation-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-jsoup-2.3.0.jar;%APP_HOME%\lib\ktor-client-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-client-core-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-serialization-kotlinx-xml-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-serialization-kotlinx-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-websocket-serialization-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-serialization-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-events-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-http-cio-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-websockets-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-sse-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-network-tls-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-http-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-network-jvm-3.0.3.jar;%APP_HOME%\lib\ktor-utils-jvm-3.0.3.jar;%APP_HOME%\lib\kotlinx-coroutines-slf4j-1.9.0.jar;%APP_HOME%\lib\ktor-io-jvm-3.0.3.jar;%APP_HOME%\lib\kotlinx-coroutines-core-jvm-1.9.0.jar;%APP_HOME%\lib\serialization-jvm-0.90.3.jar;%APP_HOME%\lib\core-jdk-0.90.3.jar;%APP_HOME%\lib\core-jvmcommon-0.90.3.jar;%APP_HOME%\lib\kotlinx-serialization-core-jvm-1.7.3.jar;%APP_HOME%\lib\kotlinx-serialization-json-io-jvm-1.7.3.jar;%APP_HOME%\lib\kotlinx-serialization-json-jvm-1.7.3.jar;%APP_HOME%\lib\koin-core-jvm-3.5.6.jar;%APP_HOME%\lib\stately-concurrent-collections-jvm-2.0.6.jar;%APP_HOME%\lib\stately-concurrency-jvm-2.0.6.jar;%APP_HOME%\lib\stately-strict-jvm-2.0.6.jar;%APP_HOME%\lib\kotlin-stdlib-jdk8-1.9.10.jar;%APP_HOME%\lib\kotlinx-io-core-jvm-0.5.4.jar;%APP_HOME%\lib\kotlin-stdlib-jdk7-1.9.10.jar;%APP_HOME%\lib\kotlinx-io-bytestring-jvm-0.5.4.jar;%APP_HOME%\lib\kotlin-stdlib-2.0.21.jar;%APP_HOME%\lib\selenium-java-4.27.0.jar;%APP_HOME%\lib\postgresql-42.7.4.jar;%APP_HOME%\lib\h2-2.3.232.jar;%APP_HOME%\lib\logback-classic-1.4.14.jar;%APP_HOME%\lib\annotations-23.0.0.jar;%APP_HOME%\lib\slf4j-api-2.0.16.jar;%APP_HOME%\lib\config-1.4.3.jar;%APP_HOME%\lib\jansi-2.4.1.jar;%APP_HOME%\lib\jsoup-1.14.3.jar;%APP_HOME%\lib\selenium-chrome-driver-4.27.0.jar;%APP_HOME%\lib\selenium-devtools-v129-4.27.0.jar;%APP_HOME%\lib\selenium-devtools-v130-4.27.0.jar;%APP_HOME%\lib\selenium-devtools-v131-4.27.0.jar;%APP_HOME%\lib\selenium-firefox-driver-4.27.0.jar;%APP_HOME%\lib\selenium-devtools-v85-4.27.0.jar;%APP_HOME%\lib\selenium-edge-driver-4.27.0.jar;%APP_HOME%\lib\selenium-ie-driver-4.27.0.jar;%APP_HOME%\lib\selenium-safari-driver-4.27.0.jar;%APP_HOME%\lib\selenium-support-4.27.0.jar;%APP_HOME%\lib\selenium-chromium-driver-4.27.0.jar;%APP_HOME%\lib\selenium-remote-driver-4.27.0.jar;%APP_HOME%\lib\selenium-manager-4.27.0.jar;%APP_HOME%\lib\selenium-http-4.27.0.jar;%APP_HOME%\lib\selenium-json-4.27.0.jar;%APP_HOME%\lib\selenium-os-4.27.0.jar;%APP_HOME%\lib\selenium-api-4.27.0.jar;%APP_HOME%\lib\guava-33.3.1-jre.jar;%APP_HOME%\lib\checker-qual-3.43.0.jar;%APP_HOME%\lib\logback-core-1.4.14.jar;%APP_HOME%\lib\netty-codec-http2-4.1.116.Final.jar;%APP_HOME%\lib\alpn-api-1.1.3.v20160715.jar;%APP_HOME%\lib\netty-transport-native-kqueue-4.1.116.Final.jar;%APP_HOME%\lib\netty-transport-native-epoll-4.1.116.Final.jar;%APP_HOME%\lib\jspecify-1.0.0.jar;%APP_HOME%\lib\auto-service-annotations-1.1.1.jar;%APP_HOME%\lib\opentelemetry-semconv-1.25.0-alpha.jar;%APP_HOME%\lib\opentelemetry-exporter-logging-1.44.1.jar;%APP_HOME%\lib\opentelemetry-sdk-extension-autoconfigure-1.44.1.jar;%APP_HOME%\lib\opentelemetry-sdk-extension-autoconfigure-spi-1.44.1.jar;%APP_HOME%\lib\opentelemetry-sdk-1.44.1.jar;%APP_HOME%\lib\opentelemetry-sdk-trace-1.44.1.jar;%APP_HOME%\lib\opentelemetry-sdk-metrics-1.44.1.jar;%APP_HOME%\lib\opentelemetry-sdk-logs-1.44.1.jar;%APP_HOME%\lib\opentelemetry-sdk-common-1.44.1.jar;%APP_HOME%\lib\opentelemetry-api-incubator-1.44.1-alpha.jar;%APP_HOME%\lib\opentelemetry-api-1.44.1.jar;%APP_HOME%\lib\opentelemetry-context-1.44.1.jar;%APP_HOME%\lib\byte-buddy-1.15.10.jar;%APP_HOME%\lib\netty-codec-http-4.1.116.Final.jar;%APP_HOME%\lib\netty-handler-4.1.116.Final.jar;%APP_HOME%\lib\netty-codec-4.1.116.Final.jar;%APP_HOME%\lib\netty-transport-classes-kqueue-4.1.116.Final.jar;%APP_HOME%\lib\netty-transport-classes-epoll-4.1.116.Final.jar;%APP_HOME%\lib\netty-transport-native-unix-common-4.1.116.Final.jar;%APP_HOME%\lib\netty-transport-4.1.116.Final.jar;%APP_HOME%\lib\netty-buffer-4.1.116.Final.jar;%APP_HOME%\lib\netty-resolver-4.1.116.Final.jar;%APP_HOME%\lib\netty-common-4.1.116.Final.jar;%APP_HOME%\lib\failsafe-3.3.2.jar;%APP_HOME%\lib\failureaccess-1.0.2.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\error_prone_annotations-2.28.0.jar;%APP_HOME%\lib\j2objc-annotations-3.0.0.jar;%APP_HOME%\lib\commons-exec-1.4.0.jar


@rem Execute thocc-project-backend
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %THOCC_PROJECT_BACKEND_OPTS%  -classpath "%CLASSPATH%" io.ktor.server.netty.EngineMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable THOCC_PROJECT_BACKEND_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%THOCC_PROJECT_BACKEND_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
