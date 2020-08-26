# Teamscale Upload

## Developing

Open this folder in IntelliJ.
It will automatically be imported as a Maven project.
You can then test and develop on the JVM.

**However, not everything that works in the JVM will also work after compiling to a native excutable.
Thus, please always test your changes after compiling to a native executable!**

To create a native executable locally, you must install the [GraalVM JDK](https://www.graalvm.org/) and make it your default JDK by setting `JAVA_HOME` and putting `$JAVA_HOME/bin` on your `PATH`.
All of this is accomplished easily with [SDKMAN!](https://sdkman.io/):

```bash
sdk install java 20.2.0.r11-grl  # install graal and make it your default JDK
gu install native-image          # install native-image
native-image --version           # check that everything worked
```

To create a native executable, run

```bash
./mvnw clean package
```

The executable is created under `./target/`.

## Creating an Release

Simply create a release in GitHub.
Travis will automatically create and attach the binaries.

