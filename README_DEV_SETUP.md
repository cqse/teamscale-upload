# teamscale-upload development README

The purpose of this tool is to
1. make it as simple as possible for Teamscale users to upload external reports
2. provide helpful error messages for commonly occurring problems, to help users resolve those themselves

[Tool Design Principles](README_TOOL_DESIGN_PRINCIPLES.md)

The tool is built with Gradle.
The distribution is a JLink image: the application jars plus a stripped-down Java runtime, so users don't need to install Java.

# Developing

Open this folder in IntelliJ.
Ensure the Gradle project is recognized by the IDE.
You can then test and develop on the JVM.

**However, not everything that works in the JVM also works in the JLink image, because the image contains only the Java modules listed in `build.gradle`.
Thus, please always test your changes with the JLink image!**

## Setup

Gradle (`./gradlew`) runs on any Java 17 or newer.
The build compiles with a Java 25 (Temurin) toolchain, which Gradle downloads automatically if it is not installed.
No other configuration is required, neither in IntelliJ nor on the command line.

## Building the JLink Image

Run `customRuntimeZip-<platform>` to build the image for one platform.
The build downloads the Java modules (jmods) of each target platform from Adoptium.
Non-Windows images can't be built on Windows, because the zip would lose the executable file permissions.

The images are created in the `build/runtime` directory.
The zipped ready-to-distribute files are found in the `build/distributions` directory.

```bash
./gradlew clean customRuntimeZip-linux-x86_64 customRuntimeZip-windows-x86_64 customRuntimeZip-macos-x86_64 customRuntimeZip-macos-aarch64
```

## Running the Integration Tests

`JLinkIntegrationTest` runs the JLink image of the current platform, so build it first.
The tests run only if the environment variable `TEAMSCALE_ACCESS_KEY` is set.
For details, see the Javadoc of `JLinkIntegrationTest`.

# Creating a Release

### Update Changelog ###
Please update the CHANGELOG and consider semantic versioning when choosing the version number for your release. Wait until the build jobs are finished (they create distributables for windows/unix/mac). They take about 10 minutes.

### Create a new Release ###
Then simply create a release in GitHub (`Draft a new release` on https://github.com/cqse/teamscale-upload/releases) and paste the release notes from the CHANGELOG into the release description.
> **Example Release**
> * Tag: v2.8.2
> * Release Title: v2.8.2
> * Description: [fix] Use TLSv1.2

GitHub Actions will automatically create and attach the binaries.

### Upload the Binaries to https://downloads.teamscale.com/tools/teamscale-upload/ ###
1. Go to [Run new pipeline](https://gitlab.com/cqse/teamscale/teamscale/-/pipelines/new).
2. Check that the `master` branch is selected
3. Set `ts-upload-cli` for variable `CUSTOM_PIPELINE`.
4. Press `New pipeline`.
5. Start the `ts-upload-cli:dist` job on the new pipeline.

This step copies the latest version of the `teamscale-upload` binaries to our website.
Check https://downloads.teamscale.com/tools/teamscale-upload/ and https://docs.teamscale.com/howto/uploading-external-data/#upload-via-command-line

### Create a new Release in Teamscale Upload Action ###
Finally, also create a new release of the [GitHub Action](https://github.com/cqse/teamscale-upload-action) with the same version number.
You need to adjust the version number (`VERSION`) in [action.yml](https://github.com/cqse/teamscale-upload-action/blob/master/action.yml).
