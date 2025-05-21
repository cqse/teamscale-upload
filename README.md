# Teamscale Upload ![Build](https://github.com/cqse/teamscale-upload/workflows/Build/badge.svg)

[Download](https://github.com/cqse/teamscale-upload/releases/latest)
• [Documentation](https://docs.teamscale.com/howto/uploading-external-results/#upload-via-command-line)
• [Changelog](https://github.com/cqse/teamscale-upload/blob/master/CHANGELOG.md)
• [GitHub Action](https://github.com/marketplace/actions/teamscale-upload)

**Command-line tool to upload external analysis results (coverage, findings, ...) to Teamscale.**

Please see [Teamscale Docs - Upload via Command Line](https://docs.teamscale.com/howto/uploading-external-results/#upload-via-command-line) for more details.

## Tool Setup

If you want to use the tool as part of a GitHub build pipeline, consider using the [GitHub Action](https://github.com/marketplace/actions/teamscale-upload).

For a local setup (on a build machine), the [Download](https://github.com/cqse/teamscale-upload/releases/latest) page offers native executables for Windows and Linux (no other tools required on the machine).

Our distributions contain the `teamscale-upload` tool bundled with OS-specific java execution environment. 
On the [Download](https://github.com/cqse/teamscale-upload/releases/latest) page, we offer distributions based on jlink and graalvm.

## Tool Usage
### jlink Distribution for Windows
[Documentation](distribution_readme/README_WINDOWS.md)
### jlink Distributions for Unix-based Systems
[Documentation](distribution_readme/README_UNIX.md)
### GraalVM Distributions
The GraalVM distributions contain a single executable packed into a zip file.
To use the distribution, unpack the archive.
Then run `teamscale-upload --help` (`teamscale-upload.exe --help` in the Windows distribution) to see all available options.

## Tool Development

[Developer Documentation](README_DEV_SETUP.md)

