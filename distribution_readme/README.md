**Command-line tool to upload external analysis results (coverage, findings, ...) to Teamscale.**

This distribution contains the teamscale-upload tool and a corresponding Java execution environment (JVM) packed into a zip archive.
To use the tool unpack the archive and call the executable:

- on Windows: `teamscale-upload\bin\teamscale-upload.bat`
- on Linux and macOS: `teamscale-upload/bin/teamscale-upload`

An invocation starts with a command that says what to upload:

- `report` uploads external analysis reports such as coverage, findings or metrics.
- `vulnerability-report` uploads a vulnerability report, e.g. a Software Bill of Materials (SBOM).

For example (unix):
```
teamscale-upload/bin/teamscale-upload \
    report \
    --server https://teamscale.example.com \
    --project my-project \
    --user build-user \
    --accesskey ACCESS_KEY \
    --partition "Unit Tests" \
    --format JACOCO \
    my-coverage-report.xml
```

Run the executable with `--help` to see all commands and with `COMMAND --help` to see the options of a single command.
