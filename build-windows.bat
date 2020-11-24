rem calling this is required so cl.exe is on the path when native-image tries to compile our exe
rem we can't do this from the travis bash script as the env variables set by this bat file are not
rem propagated to the shell environment
call "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
./mvnw.cmd clean verify -Pfailsafe

