@echo off

SET task=%1
SET class=%2
SET modification=%3
SET mod_name=ru.ifmo.rain.levashov.implementor
SET mod_dir=ru\ifmo\rain\levashov\implementor

SET wd=D:\workspace\untitled
SET mod_path=%wd%\artifacts;%wd%\lib;%wd%\out\production\%mod_name%
SET src=%wd%\%mod_name%
SET out=%wd%\out\production\%mod_name%

echo Compiling...
javac --module-path %mod_path% %src%\module-info.java %src%\%mod_dir%\*.java -d %out%

@echo on
java --module-path %mod_path% --add-modules %mod_name% -m info.kgeorgiy.java.advanced.%task%^
 %modification% %mod_name%.%class% %salt%

@echo off
cd %wd%