## Development of the teamscale-upload tool

## Design Principles

The purpose of this tool is to

1. make it as simple as possible for Teamscale users to upload external reports
2. provide helpful error messages for commonly occurring problems, to help users resolve those themselves

This entails the following design decisions:

- We want to keep the command-line interface of the tool as simple as possible.
- We do not want to make this a swiss-army-knife tool that also serves other puposes than uploading external reports.
  This unnecessarily complicates the tool's usage. It also makes it hard to write easily understandable and at the same
  time concise documentation. Instead, other purposes should receive their own tool. Code can be shared between tools
  via the normal Java library mechanisms and Maven.
- We want to optimize for the most common use-case. Command-line options should thus have defaults that just work in
  that case, allowing the average user to only specify the absolute minimum of options.
- Command-line options should be independent of each other, wherever possible. E.g. we should avoid "you can't use
  option X and option Y together" or "if you use option X you must also use option Y". This is confusing for users.
- We prefer long, explanatory error messages that make both the problem and its solution abundantly clear to the user.
- We avoid logging stack traces unless we can reasonably assume that there is a problem in the tool itself. E.g. we do
  not log a stack trace for SSL errors. Stack traces are ignored by the user and usually lead to them skipping over
  important parts of our custom error messages.

## Developing

Open this folder in IntelliJ. It will automatically be imported as a Maven project. You can then test and develop on the
JVM.

**However, not everything that works in the JVM will also work after compiling to a native excutable (e.g: reflection).
Thus, please always test your changes after compiling to a native executable!**

### Setup (Windows, in git-bash)

#### Setup SDKMAN to get GraalVm and make it the standard VM in the current git bash
In [Chocolatey](https://chocolatey.org/):
* Install git bash
* Install MingGW-w64
* Install 7zip

Problem: `git bash` and `MinGW` do not contain a `zip` program. We need to create a symlink such that they use `7zip`.
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
```

Install `native-image` (a GraalVM extension).
Native Image can be added to GraalVM with the GraalVM Updater tool (`gu`).
Run this command to install Native Image (in the admin-mode git bash):
```bash
/C/Program\ Files/GraalVM/graalvm-ce-java11-22.0.0.2/bin/gu.cmd install native-image
```

#### Install visual studio C++ build tools
```bash
choco install visualstudio2019-workload-vctools
```
This should install `C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvarsall.bat`
That file is required and called in `./build-windows.bat`.

### Setup (Linux)

To create a native executable locally, you must install the [GraalVM JDK](https://www.graalvm.org/) and make it your
default JDK by setting `JAVA_HOME` and putting `$JAVA_HOME/bin` on your `PATH`. All of this is accomplished with [SDKMAN!](https://sdkman.io/):

```bash
sdk install java 21.2.0.r11-grl  # Install graal and make it your default JDK. If it is already installed, you need 'use' instead of 'install'.
gu install native-image          # install native-image (gu is the graalvm updater)
native-image --version           # check that everything worked
```

**If you get "command not found" for "gu" on windows, you can try "gu.cmd" and "native-image.cmd".**

The executable is always created under `./target/`.

### Building a Linux Native Executable

```bash
./mvnw clean package
```

### Building a Windows Native Executable

On Windows, you must install the Visual Studio compiler tooling, e.g. via [Chocolatey](https://chocolatey.org/):

```batch
choco install visualstudio2019-workload-vctools
```

To create a native executable, run

```batch
./build-windows.bat
```

### Dealing with Reflection

The native image is built by statically determining all necessary classes and remove the unnecessary ones (to minimize
binary size). Thus, if you or one of your dependencies uses reflection, classes may be missing at runtime and the native
executable will crash.

- Minimize dependencies. Many Java libraries pull in transitive dependencies that require reflection and mess with the
  native image build. Use alternatives instead that do not require dependencies or copy classes into the code base if
  you only need small utility functions like `StringUtils`.
- Check if reflection can be replaced with another mechanism, e.g. annotation processing at compile time.
- If reflection is needed, limit it to the absolute minimum necessary.
- If reflection is needed, explicitly specify the necessary reflective accesses
  in `./src/main/resources/META-INF/native-image/reflect-config.json`. Do not use the GraalVM agent to create these
  files. It's not a fool-proof way (i.e. may fail at runtime for customers) and maintaining the generated files is
  impossible.
- If reflection is needed, provide automated test cases that cover all manually added reflective accesses to prevent
  accidental regressions.

### Running Tests in IntelliJ

Running the integration tests from IntelliJ (classes ending in `IT`) requires that you first build a native image
via `./mvnw package`. To run them locally, you'll furthermore need to define an environment variable `ACCESS_KEY` that
contains the access key to https://demo.teamscale.com for the `build` user. This key is used during the integration
tests to simulate uploads.

IntelliJ will not automatically recompile the native image after you made code changes. You must manually
invoke `./mvnw package`.

## Creating a Release

Please update the CHANGELOG and consider semantic versioning when choosing the version number for your release.

Then simply create a release in GitHub and paste the release notes from the CHANGELOG into the release description.
GitHub Actions will automatically create and attach the binaries.

Finally, also create a new release of the [GitHub Action](https://github.com/cqse/teamscale-upload-action) with the same version number.