**Command-line tool to upload external analysis results (coverage, findings, ...) to Teamscale.**

This distribution contains the teamscale-upload tool and a corresponding Java execution environment (JVM) packed into a zip archive.
To use the tool unpack the archive and call the executable (`teamscale-upload/bin/teamscale-upload`).

Each invocation starts with the command that says what to upload:

- `report` uploads external analysis reports such as coverage, findings or metrics.
- `vulnerability-report` uploads a vulnerability report, e.g. a Software Bill of Materials (SBOM).

Run `teamscale-upload/bin/teamscale-upload --help` to see all commands and
`teamscale-upload/bin/teamscale-upload COMMAND --help` to see the options of a single command.