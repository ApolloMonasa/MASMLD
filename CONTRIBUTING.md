# Contributing

Use JDK 25 and the Gradle wrapper. Point `-PlocalIdePath` at an IDE installation root containing `product-info.json`; do not use its launcher executable.

Run relevant module tests, then `./gradlew releaseBundle -PlocalIdePath=/path/to/ide` before a release. An additional local IDE can be checked with `-PadditionalVerificationIdePath=/path/to/second/ide`. See docs/validation.md for tested versions and evidence limits.

Architecture modules depend on Core. Keep CPU-specific parsing, documentation and dependencies out of Core and out of the independent GNU ld module. See docs/extension-contract.md. Changes and contributions are distributed under the project MIT license.

Report a bug with IDE/build number, installed MASMLD module versions, selected architecture, a minimal input and expected/actual behavior. Remove private project data from reports.
