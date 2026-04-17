@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License. You may obtain a copy of the License at
@REM
@REM   https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied. See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ---------------------------------------------------------------------------
@REM Apache Maven Wrapper startup script, version 3.3.2 (Windows)
@REM ---------------------------------------------------------------------------

@IF "%JAVA_HOME%" == "" (
    SET JAVACMD=java
) ELSE (
    SET JAVACMD=%JAVA_HOME%\bin\java
)

SET MAVEN_WRAPPER_PROPERTIES=.mvn\wrapper\maven-wrapper.properties
FOR /F "tokens=2 delims==" %%G IN ('findstr /I "^wrapperUrl" "%MAVEN_WRAPPER_PROPERTIES%"') DO SET DOWNLOAD_URL=%%G
FOR /F "tokens=2 delims==" %%G IN ('findstr /I "^distributionUrl" "%MAVEN_WRAPPER_PROPERTIES%"') DO SET DISTRIBUTION_URL=%%G

SET MAVEN_USER_HOME=%USERPROFILE%\.m2
SET WRAPPER_JAR=%MAVEN_USER_HOME%\wrapper\maven-wrapper.jar

IF NOT EXIST "%WRAPPER_JAR%" (
    IF NOT EXIST "%MAVEN_USER_HOME%\wrapper" MKDIR "%MAVEN_USER_HOME%\wrapper"
    powershell -Command "Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%WRAPPER_JAR%'"
    IF ERRORLEVEL 1 (
        ECHO ERROR: Could not download Maven wrapper.
        EXIT /B 1
    )
)

%JAVACMD% -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%~dp0" org.apache.maven.wrapper.MavenWrapperMain %*
