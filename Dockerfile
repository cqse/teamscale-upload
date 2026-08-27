FROM ubuntu:26.04@sha256:2260313b31c8c011cd2eebe728008efac1b3982be73eb71348ea2648d2c0e09b

RUN groupadd --system teamscale \
 && useradd --system --gid teamscale --create-home teamscale

# teamscale-upload auto-detects the uploaded revision by running `git rev-parse` in the working
# directory, so the image needs a git binary. --no-install-recommends keeps out git-man and friends.
RUN apt-get update \
 && apt-get install -y --no-install-recommends git \
 && rm -rf /var/lib/apt/lists/*

# A repository bind-mounted from the host belongs to the host user's UID, not to the `teamscale`
# user this image runs as. git would refuse such a repository as "dubious ownership" and
# auto-detection would fail with a misleading "not within a Git repository" message. We accept the
# weakened ownership check: the container has a single purpose and the user mounts their own
# repository on purpose.
RUN git config --system --add safe.directory '*'

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

# Default mount point for report files with plain `docker run -v "$PWD:/workspace:ro"`.
# CI systems (GitLab, Jenkins, Azure DevOps) override this with their own workspace path.
RUN install -d -o teamscale -g teamscale /workspace
WORKDIR /workspace

USER teamscale
ENTRYPOINT ["teamscale-upload"]
