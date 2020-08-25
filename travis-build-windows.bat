call "C:\Program Files (x86)\Microsoft Visual Studio\2017\BuildTools\VC\Auxiliary\Build\vcvars64.bat"

echo -----------------
set
echo -----------------
where.exe cl.exe
echo -----------------
./mvnw.cmd clean package

