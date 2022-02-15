## Tool Design Principles

The purpose of this tool is to

1. make it as simple as possible for Teamscale users to upload external reports
2. provide helpful error messages for commonly occurring problems, to help users resolve those themselves

This entails the following design decisions:

- We want to keep the command-line interface of the tool as simple as possible.
- We do not want to make this a swiss-army-knife tool that also serves other puposes than uploading external reports.
  This unnecessarily complicates the tool's usage.
  It also makes it hard to write easily understandable and at the same time concise documentation.
  Instead, other purposes should receive their own tool.
  Code can be shared between tools via the normal Java library mechanisms and Maven.
- We want to optimize for the most common use-case.
  Command-line options should thus have defaults that just work in that case, allowing the average user to only specify the absolute minimum of options.
- Command-line options should be independent of each other, wherever possible.
  E.g. we should avoid "you can't use option X and option Y together" or "if you use option X you must also use option Y".
  This is confusing for users.
- We prefer long, explanatory error messages that make both the problem and its solution abundantly clear to the user.
- We avoid logging stack traces unless we can reasonably assume that there is a problem in the tool itself.
  E.g. we do not log a stack trace for SSL errors.
  Stack traces are ignored by the user and usually lead to them skipping over important parts of our custom error messages.