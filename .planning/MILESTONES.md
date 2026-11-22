# Milestones

## v0.1.0 MVP (Shipped: 2026-11-22)

**Phases completed:** 4 phases, 22 plans, 42 tasks

**Key accomplishments:**
- Release-shaped Maven reactor (parent, library, sample) on a script-only Maven 3.10.1 wrapper, green in default, release and offline mode, with a SHA-pinned Java 17 CI workflow.
- Public-facing docs (README, SECURITY.md), the binding spec moved to docs/spec.md, spec section 11 as root CLAUDE.md, five spec deviations recorded, and every whole-tree hygiene gate green.
- Plain-text `Mail.using(..).to(..).subject(..).text(..).build()` with one first-problem `InvalidMailException`, the full checked error tree, and the documented `MailTransport` / `TransportProvider` / `TokenProvider` SPI, all doclint-clean under the release profile.
- A plain-text mail now goes from `Mail.using(..)` through `Mailer.of(account).send(mail)` into the `recording` transport with the account's `defaultFrom` applied, in the library and in the sample module via the reactor test-jar, with the import-rule and thread-safety Javadoc guards enforced by the build.
- `Mail.using(id).to(..).template(t, data).attach(p).build()` now renders subject, html and text from a `Map<String, String>` row with HTML escaping, resolves attachment metadata, and `build()` reports the first unsendable problem as one `InvalidMailException` that never echoes a value.
- RecordingTransport can script transient, permanent, sender-rejected, auth-required and per-recipient failures that surface from Mailer with their type, and TransportRegistry now fails loading on duplicate, blank or broken providers.
- Nine @param tags added to the MailMessage compact constructor Javadoc so JDK 17 doclint (all, failOnWarnings) no longer reports "no @param"; comment-only change, three CI commands green on JDK 21, Temurin 17 confirmation handed to the owner
- picocli `serial-mail send` that reads a CSV campaign, sends one mail per row through an injected Mailer with a forced JSON-lines journal (rerun skips accepted rows), and dry-runs the same campaign into sanitised, zero-padded rendered files.
- Pre-flight now reports placeholders without a CSV column, semicolon headers, repeated columns, duplicate addresses and bad account credentials together in one fixed order, exit 2, nothing sent, no journal, and never prints a value from account.properties.
- The `send` run loop now retries transient failures (four attempts, waits capped at 5 min and never below the `--rate` spacing), stops with exit 3 on sender-rejected or auth-required failures, rejects bad `--rate`, `--limit` and campaign ids in pre-flight, and keeps a journal that warns about damaged lines, repairs a torn last line and stops the run when a write fails.
- Runnable `morimail-sample-serial` jar (manifest classpath over `target/lib`) that dry-runs the shipped example campaign, five example.org rows with three placeholders, a PDF and a PNG, into five rendered mails with no secret set.
- A secret-authenticated Entra app gets a token from a WireMock endpoint and sends one text mail through a registry-built `graph` account, with graph resolved from the built-in list.
- EntraClientCredentials signs a PS256 client assertion from a key and certificate or a PKCS12 file, caches the token with single flight and compare-and-drop, and maps every token failure to transient or auth-required without a secret in any message.
- The Graph transport now sends the full documented sendMail JSON (names, cc, bcc, reply-to, one body, base64 file attachments, saveToSentItems only when false), rejects bad options naming the key, and rejects mails over the 3 MiB or 4 MiB limits before any token request or byte is sent.
- GraphTransport retries a 401 once with `freshAccessToken`, maps every status to the documented exception with Retry-After, and builds failure messages from the status, error code, request-id and cleaned Graph message only.
- `serial-mail send` now builds an Entra token provider and a registry-built graph account from account.properties and the environment, delivers the example campaign once through WireMock, and stops in pre-flight or with exit 3 as the plan requires.
- Graph failure messages now carry only the HTTP status, `error.code` and `request-id` (Graph's free-text `error.message` is dropped), and the root-logger capture in `EntraTokenErrorsTest` uses `CopyOnWriteArrayList`.
- Server text in Graph and Entra failure messages is cut at a word boundary before addresses are replaced, so no part of an address survives, and spec §6.3, §8.3 and docs/architecture.md now match the live test of 2026-11-22.
- docs/entra-setup.md takes the owner from an empty app registration to a campaign whose second run prints `accepted 0, skipped <rows>, failed 0`, with the Exchange cmdlets copied from Microsoft's RBAC page re-read on 2026-11-22 and every open item kept in a closing section.
- README.md now says what morimail is and is not (not a bulk mailer, the Exchange limits, under 3 MiB per file and in total), and its quick start has three catches, one per error category, and compiled clean with `javac --release 17 -Xlint:all -Werror` against the packaged jar.

**Closeout type:** override_closeout. All four phase VERIFICATION.md reports passed, but the verifier marks them stale: files they cover changed afterwards (later phases edited README, docs and POMs; then the 0.2.0-SNAPSHOT bump and fix 378e0bb). The owner chose to close anyway on 2026-11-22.
**Known verification overrides:** 4 newly acknowledged (stale reports for phases 1-4), 0 carried forward. Phase 1 also carries the owner-accepted override D-02b (root commit authored by GitHub).
**Audit:** tech_debt. 69/69 requirements, 14/14 integration connections, offline `./mvnw -o verify` green. 11 tech-debt items, none blocking; see milestones/v0.1.0-MILESTONE-AUDIT.md.
**Git range:** 8c9b082 (initial commit) → 378e0bb; release tag `v0.1.0` set by the owner on the CI-green release commit.

---
