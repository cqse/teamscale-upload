@echo off
rem calling this is required so cl.exe is on the path when native-image tries to compile our exe
rem we can't do this from the travis bash script as the env variables set by this bat file are not
rem propagated to the shell environment

rem There are different potential paths for the vcvarsall.bat file (on the build machine it is apparently the "Enterprise" path)
for %%f in (
        "C:\Program Files (x86)\Microsoft Visual Studio\2019\Enterprise\VC\Auxiliary\Build\vcvarsall.bat"
        "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvarsall.bat"
       ) do (
         IF EXIST %%f (
           call %%f x86_amd64
           goto :vcvarsall_executed
         )
       )
echo "Did not find Visual Studio vcvarsall.bat at one of the expected locations. Aborting now."
exit
:vcvarsall_executed

./mvnw.cmd package
