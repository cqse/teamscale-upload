# Teamscale Upload ![Build](https://github.com/cqse/teamscale-upload/workflows/Build/badge.svg)

[Download](https://github.com/cqse/teamscale-upload/releases/latest) • [Documentation](https://docs.teamscale.com/howto/uploading-external-results/#upload-via-command-line) • [Changelog](https://github.com/cqse/teamscale-upload/blob/master/CHANGELOG.md)

**Command-line tool to upload external analysis results (coverage, findings, ...) to Teamscale.**

Run `teamscale-upload --help` to see all available options.

## Developing

Open this folder in IntelliJ.
It will automatically be imported as a Maven project.
You can then test and develop on the JVM.

**However, not everything that works in the JVM will also work after compiling to a native excutable (e.g: reflection).
Thus, please always test your changes after compiling to a native executable!**

To create a native executable locally, you must install the [GraalVM JDK](https://www.graalvm.org/) and make it your default JDK by setting `JAVA_HOME` and putting `$JAVA_HOME/bin` on your `PATH`.
All of this is accomplished easily with [SDKMAN!](https://sdkman.io/):

```bash
sdk install java 20.3.0.r11-grl  # install graal and make it your default JDK
gu install native-image          # install native-image
native-image --version           # check that everything worked
```

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

The native image is built by statically determining all necessary classes and remove the unnecessary ones (to minimize binary size).
Thus, if you or one of your dependencies uses reflection, classes may be missing at runtime and the native executable will crash.

- Minimize dependencies.
    Many Java libraries pull in transitive dependencies that require reflection and mess with the native image build.
    Use alternatives instead that do not require dependencies or copy classes into the code base if you only need small utility functions like `StringUtils`.
- If reflection is needed, limit it to the absolute minimum necessary.
- If reflection is needed, explicitly specify the necessary reflective accesses in `./src/main/resources/META-INF/native-image/reflect-config.json`.
    Do not use the GraalVM agent to create these files. It's not a fool-proof way (i.e. may fail at runtime for customers) and maintaining the generated files is impossible.

### Running Tests in IntelliJ

Running the integration tests from IntelliJ (classes ending in `IT`) requires that you first build a native image via `./mvnw package`.
To run them locally, you'll furthermore need to define an environment variable `ACCESS_KEY` that contains the access key to https://demo.teamscale.com for the `build` user.
This key is used during the integration tests to simulate uploads.

IntelliJ will not automatically recompile the native image after you made code changes.
You must manually invoke `./mvnw package`.

## Creating an Release

Please update the CHANGELOG and consider semantic versioning when choosing the version number for your release.

Then simply create a release in GitHub and paste the release notes from the CHANGELOG into the release description.
GitHub Actions will automatically create and attach the binaries.

