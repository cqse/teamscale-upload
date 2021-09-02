We use [semantic versioning](http://semver.org/):

- MAJOR version when you make incompatible API changes,
- MINOR version when you add functionality in a backwards-compatible manner, and
- PATCH version when you make backwards compatible bug fixes.

# Next Release

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

