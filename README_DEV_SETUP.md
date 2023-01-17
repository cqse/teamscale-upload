## Development of the teamscale-upload tool

The purpose of this tool is to
1. make it as simple as possible for Teamscale users to upload external reports
2. provide helpful error messages for commonly occurring problems, to help users resolve those themselves

[Tool Design Principles](README_TOOL_DESIGN_PRINCIPLES.md)

## Developing

Open this folder in IntelliJ.
It will automatically be imported as a Maven project.
You can then test and develop on the JVM.

**However, not everything that works in the JVM will also work after compiling to a native excutable (e.g: reflection).
Thus, please always test your changes after compiling to a native executable!**

### Setup (Windows, in git-bash)

#### Setup SDKMAN to get GraalVm and make it the standard VM in the current git bash

In [Chocolatey](https://chocolatey.org/):

* Install git bash
* Install MingGW-w64
* Install 7zip

Problem: `git bash` and `MinGW` do not contain a `zip` program.
We need to create a symlink such that they use `7zip`.
Open a git bash in administrator mode and run this (need admin mode to create symlink)

```bash
ln -s /c/Program\ Files/7-Zip/7z.exe /c/Program\ Files/Git/mingw64/bin/zip.exe
```

In a normal-mode git-bash again:

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

This will install the graal VM and make it the default JVM for the current git bash session

```bash
sdk install java 21.2.0.r11-grl
# For subsequent usages when the JDK has already been installed
# sdk use java 21.2.0.r11-grl 
```

Install `native-image` (a GraalVM extension). Native Image can be added to GraalVM with the GraalVM Updater tool (`gu`).
Run this command to install Native Image (in the normal-mode git bash):

```bash
"$HOME\.sdkman\candidates\java\current\bin\gu.cmd" install native-image
```

Verify the correct installation of native-image

```bash
native-image.cmd --version         
```

#### Install visual studio C++ build tools

```bash
choco install visualstudio2019-workload-vctools
```

This should install `C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvarsall.bat`
That file is required and called in `./build-windows.bat`.

#### Building a Native Executable (Windows)

To create a native executable, run

```batch
./build-windows.bat
```

### Setup (Linux)

To create a native executable locally, you must install the [GraalVM JDK](https://www.graalvm.org/) and make it your default JDK by setting `JAVA_HOME` and putting `$JAVA_HOME/bin` on your `PATH`.
All of this is accomplished with [SDKMAN!](https://sdkman.io/):

```bash
sdk install java 21.2.0.r11-grl  # Install graal and make it your default JDK.
# For subsequent usages when the JDK has already been installed
# sdk use java 21.2.0.r11-grl 
gu install native-image          # install native-image (gu is the graalvm updater)
native-image --version           # check that everything worked
```

The executable is always created under `./target/`.

#### Building a Native Executable (Linux)

```bash
./mvnw clean package
```

### Dealing with Reflection

The native image is built by statically determining all necessary classes and removing the unnecessary ones (to minimize binary size).
Thus, if you or one of your dependencies uses reflection, classes may be missing at runtime and the native executable will crash.

- Minimize dependencies.
  Many Java libraries pull in transitive dependencies that require reflection and mess with the native image build.
  Use alternatives instead that do not require dependencies or copy classes into the code base if you only need small utility functions like `StringUtils`.
- Check if reflection can be replaced with another mechanism, e.g. annotation processing at compile time.
- If reflection is needed, limit it to the absolute minimum necessary.
- If reflection is needed, explicitly specify the necessary reflective accesses in `./src/main/resources/META-INF/native-image/reflect-config.json`.
  Do not use the GraalVM agent to create these files.
  It's not a fool-proof way (i.e. may fail at runtime for customers) and maintaining the generated files is impossible.
- If reflection is needed, provide automated test cases that cover all manually added reflective accesses to prevent
  accidental regressions.

### Running Tests in IntelliJ

Running the integration tests from IntelliJ (classes ending in `IT`) requires that you first build a native image via `./mvnw package`.
To run them locally, you'll furthermore need to define environment variable `TEAMSCALE_ACCESS_KEY` that contains the access key to https://cqse.teamscale.io/ project `teamscale-upload` for the `teamscale-upload-build-test-user` user.
The access key is stored in 1password.
This key is used during the integration tests (stored as Github Secret) to simulate uploads.

IntelliJ will not automatically recompile the native image after you made code changes.
You must manually invoke `./mvnw package`. (Or `package-windows.bat` in windows.)

## Creating a Release

Please update the CHANGELOG and consider semantic versioning when choosing the version number for your release.

Then simply create a release in GitHub and paste the release notes from the CHANGELOG into the release description.
GitHub Actions will automatically create and attach the binaries.

Finally, also create a new release of the [GitHub Action](https://github.com/cqse/teamscale-upload-action) with the same version number.