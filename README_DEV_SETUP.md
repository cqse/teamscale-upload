# teamscale-upload development README

The purpose of this tool is to
1. make it as simple as possible for Teamscale users to upload external reports
2. provide helpful error messages for commonly occurring problems, to help users resolve those themselves

[Tool Design Principles](README_TOOL_DESIGN_PRINCIPLES.md)

At the moment, this tool has two build systems (maven and gradle).
Gradle is used to build the new JLink-executable distribution.
Maven is used to build the old Graalvm-executable distribution.
New dependencies must be inserted into both systems (`pom.xml` and `build.gradle`).

# JLink build
## Developing

Open this folder in IntelliJ.
Ensure the Gradle project is recognized by the IDE.
You can then test and develop on the JVM.

**However, not everything that works in the JVM will also work after compiling to a native executable (e.g: reflection).
Thus, please always test your changes after compiling to a native executable!**

### Setup (Linux)

No additional configuration is required after importing the project into IntelliJ.
Gradle (`./gradlew`) can also be used directly from the command line.

#### Building a JLink Native Executable (Linux)

To create a native executable with JLink locally, run the corresponding `customRuntimeZip-*` Gradle task, or `customRuntimeZip` to
build the executables for all platforms.

The executable is created in the `build/runtime` directory. The zipped ready-to-distribute files are found in the
`build/distributions` directory.

```bash
./gradlew clean customRuntimeZip
```

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

### Upload the Binaries to www.teamscale.com ###
1. Go to [Run new pipeline](https://gitlab.com/cqse/teamscale/teamscale/-/pipelines/new).
2. Check that the `master` branch is selected
3. Set `ts-upload-cli` for variable `CUSTOM_PIPELINE`.
4. Press `New pipeline`.
5. Start the `ts-upload-cli:dist` job on the new pipeline.

This step copies the latest version of the `teamscale-upload` binaries to our website.

### Create a new Release in Teamscale Upload Action ###
Finally, also create a new release of the [GitHub Action](https://github.com/cqse/teamscale-upload-action) with the same version number.
You need to adjust the version number in the [script](https://github.com/cqse/teamscale-upload-action/blob/master/src/run-teamscale-upload.sh). There are two spots where you need to adjust the number!
