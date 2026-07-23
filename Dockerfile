FROM ubuntu:26.04@sha256:3131b4cc82a783df6c9df078f86e01819a13594b865c2cad47bd1bca2b7063bb

RUN groupadd --system teamscale \
 && useradd --system --gid teamscale --create-home teamscale

# Self-contained jlink distribution: launcher + bundled (glibc) JVM + all jars.
# Extracted from teamscale-upload-linux-x86_64.zip into the build context.
COPY teamscale-upload/ /opt/teamscale-upload/

USER teamscale
ENTRYPOINT ["/opt/teamscale-upload/bin/teamscale-upload"]
