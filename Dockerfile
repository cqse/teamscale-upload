FROM ubuntu:26.04@sha256:3131b4cc82a783df6c9df078f86e01819a13594b865c2cad47bd1bca2b7063bb

RUN groupadd --system teamscale \
 && useradd --system --gid teamscale --create-home teamscale

# Self-contained jlink distribution: launcher + bundled (glibc) JVM + all jars.
# Extracted from teamscale-upload-linux-x86_64.zip (./gradlew customRuntimeZip-linux-x86_64) into
# the build context. Not the `distZip` output (teamscale-upload-<version>.zip): that one ships no
# JVM and a launcher that expects JAVA_HOME to be set or java to be on the PATH.
COPY teamscale-upload/ /opt/teamscale-upload/

# Make the tool callable by name, so consumers don't have to hardcode the path above: users who
# override the entrypoint, derived images and CI systems like GitLab that ignore the entrypoint and
# run their script steps in a shell.
# The launcher resolves symlinks before determining its own install directory, so a symlink works.
# We deliberately don't put /opt/teamscale-upload/bin on the PATH instead: it is the jlink image's
# bin directory, so it also contains the bundled runtime's `java`, `keytool` etc. On the PATH those
# would shadow a real JDK installed in a derived image with our stripped 4-module runtime, which
# then fails in confusing ways for anything but teamscale-upload itself.
RUN ln -s /opt/teamscale-upload/bin/teamscale-upload /usr/local/bin/teamscale-upload

USER teamscale
ENTRYPOINT ["teamscale-upload"]
