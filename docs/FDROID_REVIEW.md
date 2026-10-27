# F-Droid review fixes — 27 October 2026

The existing packaging request is [RFP #4683](https://gitlab.com/fdroid/rfp/-/work_items/4683). It was moved from fdroiddata to fdroid/rfp; no duplicate request is needed. This is an inclusion request, not approval or catalog publication.

## Bot feedback addressed

- Add Fastlane metadata for pt-BR and en-US: title, summary, description, icon, screenshots and version-code changelogs. English translates the listing; the app UI is Portuguese.
- Add the official SHA-256 of the exact Gradle distribution used by this project. The checksum was obtained from services.gradle.org, without copying a value for another archive variant.
- Keep the draft recipe in fdroid.yml synchronized with metadata/com.pixelricardo.game.yml, using a full release-source commit hash and tag-based updates.

Local validation with fdroidserver 2.4.5: rewritemeta and lint passed using the fdroiddata category configuration. Fastlane length, image presence, canonical package filename and version/tag matching were checked. These checks do not mean a full F-Droid build or binary scan has passed; that remains for the packaging pipeline.

## Runtime dependency finding

The bot's `gradlew-fdroid --write-verification-metadata sha256 help` report lists OpenCensus artifacts in the build tooling graph. A fresh `:app:dependencies --configuration releaseRuntimeClasspath` report contains only `org.jetbrains.kotlin:kotlin-stdlib:2.2.10` and `org.jetbrains:annotations:13.0`.

The published 1.2 APK was also checked: no OpenCensus identifiers were found in its DEX and no INTERNET permission is requested. These results distinguish the runtime graph from the build-tool graph; they are not a general privacy/security audit. No tracker runtime dependency or AntiFeature was added based solely on the build-tool report.

The old draft incorrectly used the filename br.com.pixelricardo.game.yml and version 1.1. It now uses com.pixelricardo.game.yml and the released 1.2 / code 3 source. No application code, signing key or APK was changed by this metadata fix.
