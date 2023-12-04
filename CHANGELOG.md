We use [semantic versioning](http://semver.org/):

- MAJOR version when you make incompatible API changes,
- MINOR version when you add functionality in a backwards-compatible manner, and
- PATCH version when you make backwards compatible bug fixes.

# Next Release
# 2.9.1
- [feature] added jlink-based executable distributions
- [fix] added support for more connection-security algorithms
  
# 2.8.2
- [fix] fixed potential vulnerability to a zipslip attack (by malicious tar files in xcode reports)
  
# 2.8.1
- [fix] Use TLSv1.2
- [fix] Use versioned API endpoints to be more robust in case of API changes

# 2.8.0

- [feature] add support for importing trusted certificates from the operating system

# 2.7.1
- [fix] restore compatibility with Ubuntu 20.04 LTS

# 2.7.0
- [fix] response message contained object identifier instead of meaningful information
- [fix] error message on file-access problems contained stack trace

# 2.6.0

- [feature] add `--debug` to enable more detailed logging in case of problems
- [fix] links in error messages were encoded incorrectly and thus not always clickable
- [fix] handle empty stdin gracefully when `--accesskey -` is given instead of crashing

# 2.5.0

- [feature] new `--timeout` command line argument for specifying request timeouts in seconds
- [feature] support for faster coverage extraction from XCResult bundles with [XCode 13.3 command line tools](https://developer.apple.com/documentation/xcode-release-notes/xcode-13_3-release-notes) (see 82004604)
- [feature] release for MacOS

# 2.4.0

- [feature] if a revision exists in multiple repositories the correct repository can be passed using `--repository`
- [feature] upload can be moved to the latest revision using `--move-to-last-commit`

# 2.3.0

- [feature] access keys can now also be passed via stdin or environment variable

# 2.2.0

- [feature] add automatic conversion of XCResult bundles when uploading XCode reports

# 2.1.1

- [fix] change confusing error message when incorrect --commit parameter is given

# 2.1.0

- [feature] suppress unnecessary stack traces in error messages

# 2.0.1

- reduce size of binaries by compressing them. No functional changes

# 2.0.0

- [breaking change] certificates are now validated by default. Use `--insecure` to disable validation
- [breaking change] remove `--detect-commit` option and make that the default behaviour
- [feature] allow users to provide self-signed certificates that should be trusted in a Java keystore
- [feature] allow users to append lines to the default message

# 1.0.0

Initial release

