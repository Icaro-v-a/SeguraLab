@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>&1 || (echo Instale o JDK 17 ou superior. & pause & exit /b 1)
if not exist "lib\sqlite-jdbc-3.54.1.0.jar" goto baixar
if not exist "lib\slf4j-api-2.0.17.jar" goto baixar
if not exist "lib\slf4j-nop-2.0.17.jar" goto baixar
goto compilar
:baixar
java "ferramentas\PrepararDependencias.java"
if errorlevel 1 (echo Copie os JARs do ReservaLab para lib. & pause & exit /b 1)
:compilar
if not exist "build\classes" mkdir "build\classes"
javac --release 17 -encoding UTF-8 -d "build\classes" "src\br\edu\seguralab\App.java"
if errorlevel 1 (pause & exit /b 1)
echo Acesse http://127.0.0.1:8788 no navegador.
java -cp "build\classes;lib\*" br.edu.seguralab.App
if errorlevel 1 pause
