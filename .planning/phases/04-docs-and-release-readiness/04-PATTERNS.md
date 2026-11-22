# Phase 4: Docs and Release Readiness - Pattern Map

**Mapped:** 2026-11-22
**Files analyzed:** 11 (2 new docs, 4 edited docs, 2 main-code edits, 2 test edits, 1 planning edit; optional `.gitignore`)
**Analogs found:** 11 / 11 (all tracked in git; none are mirror paths)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java` (`clean()`, ~l.309) | utility | transform | itself, plus the twin in `entra/EntraClientCredentials.java` | exact |
| `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java` (`clean()`, ~l.375) | utility | transform | `GraphTransport.clean` | exact |
| `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java` (+1 test) | test | request-response (WireMock) | `anAddressAfterALongWhitespaceRunLeavesNoPartOfItInTheMessage` (l.367-373) | exact |
| `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java` (+1 test) | test | request-response (WireMock) | same-named test (l.221-229) | exact |
| `.planning/phases/03-graph-and-entra/03-REVIEW-DISPOSITION.md` | doc (planning) | n/a | itself (frontmatter `findings:` list, `disposition: open` -> `fixed`) | exact |
| `docs/spec.md` (§6.3 table and paragraph) | doc (spec) | n/a | existing §6.3 table, lines ~222-238 | exact |
| `docs/architecture.md` | doc | n/a | itself (deviations table, "Contracts" bullets) | exact |
| `README.md` | doc | n/a | itself (current stub with section headings) | exact |
| `docs/entra-setup.md` (NEW) | doc (guide) | n/a | `docs/architecture.md` tone; `example-campaign/account.properties` for keys | role-match |
| `docs/release.md` (NEW) | doc (checklist) | n/a | `docs/architecture.md` tone; parent `pom.xml` release profile | role-match |
| `.gitignore` (optional: `*.p12`, `*.key`, `*.pem`, negate the tracked test keystore) | config | n/a | existing `*.pfx`, `*.jsonl` lines | exact |

## Pattern Assignments

### Both `clean()` methods (WR-01, utility, transform)

**Analog:** the two existing methods. They are duplicated on purpose (`graph` and `entra` must not import each other, Import rules test), so edit both identically. Do not extract a shared class.

Current Graph code (`GraphTransport.java` ~l.309-335). It collapses spaces, cuts at `MAX_CLEAN_INPUT`, then replaces addresses:
```java
String collapsed = sb.toString().replaceAll(" {2,}", " ");
String text = (collapsed.length() > MAX_CLEAN_INPUT ? collapsed.substring(0, MAX_CLEAN_INPUT) : collapsed)
        .replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>").trim();
```
Current Entra code (`EntraClientCredentials.java` ~l.375-400). It differs only in the collapse regex, which is `replaceAll("\\s+", " ")`, and in the variable name `spaced`:
```java
String collapsed = spaced.toString().replaceAll("\\s+", " ");
String text = (collapsed.length() > MAX_CLEAN_INPUT ? collapsed.substring(0, MAX_CLEAN_INPUT) : collapsed)
        .replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>").trim();
```
**Fix to apply (token-boundary cut, per RESEARCH Pitfall 5).** Do NOT use replace-then-cut, because the address regex is quadratic on a long token with no `@`. `collapsed` must be non-final.
```java
if (collapsed.length() > MAX_CLEAN_INPUT) {
    collapsed = collapsed.substring(0, MAX_CLEAN_INPUT);
    int lastSpace = collapsed.lastIndexOf(' ');
    collapsed = lastSpace > 0 ? collapsed.substring(0, lastSpace) : "";   // never keep a partial token
}
String text = collapsed.replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>").trim();
```
Also update the in-code comment ("Collapse first, then cut, then replace addresses...") and the Graph `clean()` Javadoc if it mentions the cut. Javadoc doclint runs under `-Prelease`.

The rest of each method is unchanged: the empty check returns `"-"`, then the `MAX_ERROR_TEXT` cut that avoids a lone high surrogate, then `"..."`.

### Test per class (test, request-response)

**Graph analog** (`GraphErrorMappingTest.java` l.367-373):
```java
@Test
void anAddressAfterALongWhitespaceRunLeavesNoPartOfItInTheMessage(WireMockRuntimeInfo wm) {
    stubError(400, null, errorBody(" ".repeat(995) + "alice@example.org", "m"));
    assertThat(messageOfFailedSend(wm))
            .isEqualTo("Graph sendMail failed: HTTP 400, code <address>, request-id -");
}
```
New test: copy it with a name such as `anAddressStraddlingTheCutLeavesNoPartOfItInTheMessage`. Use input `"x".repeat(990) + " " + "alice".repeat(5) + "@example.org"` as the code, and assert `doesNotContain("alice")`. The `@Timeout(10)` quadratic guards sit nearby (`aVeryLongCodeIsCutAndCleanedQuickly`, l.~360).

**Entra analog** (`EntraTokenErrorsTest.java` l.221-229):
```java
stubStatus(400, "{\"error\":\"invalid_request\",\"error_description\":\"" + " ".repeat(995)
        + "alice@example.org\"}");
assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
        .isExactlyInstanceOf(AuthRequiredException.class)
        .satisfies(e -> assertThat(e.getMessage()).endsWith("description: <address>"));
```
New test: same shape with the straddling input in `error_description`, asserting `assertThat(e.getMessage()).doesNotContain("alice")`. Run both new tests against the old code first to confirm they fail.

### `.planning/phases/03-graph-and-entra/03-REVIEW-DISPOSITION.md`

Analog: its own frontmatter entries (`- id: WR-01 ... disposition: open`). Set WR-01 and IN-01 to `disposition: fixed`, set frontmatter `open: 0`, and update the table rows. Alternatively record the fix in the Phase 4 summary (D-02 allows either).

### `docs/spec.md` §6.3 (D-01)

Analog: the existing table at spec lines ~222-238. Keep the rows and add a third column "Live status (2026-11-22)". The exact row content is in RESEARCH "Spec §6.3 write-back":
- 202: confirmed.
- 403: confirmed (`ErrorAccessDenied`), plus the propagation note.
- 404: confirmed (`ErrorInvalidUser`).
- 401, 429/503/504, network errors, and 400/413: WireMock only.

Replace the sentence "A forum answer reports 401 ..." with the 404 finding. Say that `MailboxNotEnabledForRESTAPI` and the out-of-scope refusal were not provoked. Change the "Starting table. Verify each row" lead-in. Do not touch the error mapping in code.

### `docs/architecture.md` (DOCS-02)

**Analog:** itself. The structure is:
- l.1 title.
- l.3 placeholder intro ("The full contract summary follows with the 0.1.0 documentation").
- `## Deviations from the spec`, a table with columns `| Spec | morimail | Reason |` and one row per deviation.
- `## Contracts`, a bullet list.

Edits to make:
1. Replace the l.3 placeholder with a roughly 25-30 line contract summary. Cover:
   - the two extension points, with the `freshAccessToken` default;
   - the error hierarchy and what each category tells the caller to do;
   - accepted-not-delivered and at-least-once;
   - the registry (explicit built-in list plus `ServiceLoader`);
   - opaque options validated in `validate`;
   - thread-safety;
   - the import rules;
   - the logging rule.

   Fold the existing Contracts bullets into it so there is only one contracts section.
2. Add deviation rows in the same `| Spec | morimail | Reason |` style:
   - the §6.3 spec edit (D-01);
   - any §8.3/§12 edit;
   - the 403-during-propagation note beside the existing `§6.3 (error table)` row.
3. Update the bullets that say "only the live test shows" (~l.40, PS256) and the mailbox-error-code bullet (~l.49) to the live results.
4. Add "Differences from the Microsoft documentation" bullets, dated 2026-11-22. They are listed in RESEARCH "architecture.md delta" item 4:
   - Exchange Administrator Entra role;
   - the test cmdlet ignores Entra grants;
   - the limits page words 30/min as SMTP;
   - TERRL and the `onmicrosoft.com` 100-recipient throttle;
   - `PrimarySmtpAddress` advice.
5. Update the "failure message" row and the cleaning bullet to the new `clean()` order: collapse, cut at a token boundary, replace addresses, cut at 300.

### `README.md` (DOCS-03)

**Analog:** the current file. Keep its headings and wording: `## Status`, `## Coordinates` (the `0.1.0` dependency block), `## Delivery semantics`, `## Tests` (the keystore sentence, keep verbatim), `## License`, `## Security`. Replace the intro "(in development)" wording and the `## Quick start` stub ("A quick start comes with the first release."). Add a "What morimail is not" section covering the Exchange limits (30/min, 10,000 recipients per day, TERRL, the `onmicrosoft.com` 100-recipient throttle), and say it is not a bulk mailer. Add accepted-not-delivered next to the at-least-once text.

**Quick-start Java block** (compiled clean with `javac --release 17` per RESEARCH; D-09 asks the planner to re-verify names against source):
```java
var auth = EntraClientCredentials.withCertificate(
        "00000000-0000-0000-0000-000000000000", "00000000-0000-0000-0000-000000000000",
        Path.of("/path/app.p12"), System.getenv("MAIL_CERT_PASSWORD").toCharArray());
MailAccount account = MailAccount.builder("entra-main").transport("graph").auth(auth)
        .defaultFrom("newsletter@example.com").build();
Mailer mailer = Mailer.of(account);
MailTemplate template = new MailTemplate("Hello $$firstName$$", "<p>Hello $$firstName$$,</p>", "Hello $$firstName$$,");
Mail mail = Mail.using("entra-main").to("anna@example.org")
        .template(template, Map.of("firstName", "Anna")).attach(Path.of("info.pdf")).build();
try { mailer.send(mail); }
catch (TransientMailException e) { /* try again later; e.retryAfter() is Optional<Duration> */ }
catch (SenderRejectedException | AuthRequiredException e) { /* stop and fix the setup */ }
catch (PermanentMailException e) { /* this mail will never be accepted as it is */ }
```
Source signatures to spot-check (line numbers from RESEARCH):
- `Mail.java:30,85,258,279,327`.
- `MailAccount.java:32,87,98,122,139`.
- `Mailer.java:30,60`.
- `MailTemplate.java:30` (a record of `subject`, `html`, `text`).
- `EntraClientCredentials.java:174`.
- `TransientMailException.java:58`.

After the block, add 2-3 lines on the sample dry run:
```bash
./mvnw -B -ntp -DskipTests package -pl morimail-sample-serial -am
java -jar morimail-sample-serial/target/morimail-sample-serial-<version>.jar send \
    --account example-campaign/account.properties --campaign example-campaign --dry-run --out out
```
This must run from `morimail-sample-serial`, as the verified run did. Link to `docs/entra-setup.md`. Mention that the sample sends no `saveToSentItems`, so mails land in Sent Items (D-03).

### `docs/entra-setup.md` (NEW, DOCS-01)

**Analogs:**
- Tone and format: plain English, short sentences and example values, as in `docs/architecture.md` and the `account.properties` comments.
- Key names to document (`morimail-sample-serial/example-campaign/account.properties`):
```properties
account.id=entra-main
tenantId=00000000-0000-0000-0000-000000000000
clientId=00000000-0000-0000-0000-000000000000
from=newsletter@example.com
certificate.path=/path/app.p12
certificate.passwordEnv=MAIL_CERT_PASSWORD
# secretEnv=MAIL_CLIENT_SECRET
```
- Content skeleton: spec §9 order. Quoted Microsoft cmdlets, certificate steps, union trap and the closing "Not yet verified live" section are all specified in RESEARCH "Microsoft setup facts", "Certificate creation" and Pitfalls 6-8.
  - Recommend B, and state that A is the live-verified path.
  - Certificate creation uses the openssl `req -x509` then `pkcs12 -export -passout env:` recipe, with `-legacy` as a fallback only.
  - Close with a "Not yet verified live" section (D-06), with no inline caveats earlier.
  - Verification order: dry run, then `--limit 1` on a copy of `example-campaign/` containing the owner's own address (Pitfall 7), then the campaign, then a second run that sends nothing.
  - Key files stay outside the repository.
  - Re-read the Microsoft RBAC page when writing and copy its cmdlets.

### `docs/release.md` (NEW, D-10/D-11/D-12)

**Analog:** the parent `pom.xml` release profile (lines ~138-233: source, javadoc, gpg 3.2.8, central-publishing 0.12.0, `autoPublish` false) and `morimail/pom.xml:79` (`attach` false). Content is in RESEARCH "What docs/release.md should contain", items 1-8:
- Namespace verification.
- GPG key with the identity `Stephan Wald <stephan@morisoft.de>`, sent to `keyserver.ubuntu.com`.
- `settings.xml` server id `central`, with placeholders only:
```xml
<server><id>central</id><username>TOKEN_USERNAME</username><password>TOKEN_PASSWORD</password></server>
```
- The version `sed` across the three POMs.
- Deploy command `./mvnw -B -ntp -Prelease clean deploy -pl morimail -am`.
- Portal review, then publish, tag `v0.1.0`, bump to the next SNAPSHOT.
- Never Maven 4.

The doc must be a checklist the owner runs. Claude never runs it.

### Rehearsal (executor task, no file output)

Analog: the recipe in RESEARCH "Rehearsal recipe". Run it after the docs are committed, because it uses `git archive HEAD`.
- Short `GNUPGHOME` symlink `/tmp/gh-reh` pointing into the scratchpad.
- Online run, no `-o`, at version `0.1.0`.
- Expect 30 bundle entries, with no `tests` or `sample`.
- Then run the signature loop.
- Cleanup (mandatory): kill gpg-agent, remove the key, the symlink and `~/.m2/repository/de/morisoft`.
- Also run `./mvnw -o -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` with no warnings.

## Shared Patterns

### Example-values-only rule (all docs)
**Apply to:** README, entra-setup, release, architecture, spec edits.
Use `example.com`, `example.org`, all-zero GUIDs, `/path/app.p12`, and `TOKEN_USERNAME`/`TOKEN_PASSWORD` placeholders. The only allowed identities are the maintainer's. Before commit, grep the new docs for non-zero GUIDs and real domains.

### Deviations recorded in architecture.md
**Apply to:** every spec edit (D-01), every Microsoft-doc difference.
Add the row as `| Spec section | morimail | Reason |`, following the table at `docs/architecture.md:7-24`.

### Duplicated-on-purpose rule
**Apply to:** both `clean()` edits.
Apply the same change twice and never share code. `ImportRulesTest` enforces that `graph` and `entra` do not import each other.

### Build gates after any change
**Apply to:** every plan.
- `./mvnw -B -ntp verify` (offline-capable, all tests, including the two new ones).
- `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` (doclint, no warnings).
- CI already runs both.

### Commit rules
**Apply to:** every plan.
- Export the git identity `Stephan Wald <stephan@morisoft.de>` before each commit, because workspace `GIT_*` env vars override local config.
- End the commit message with the Co-Authored-By trailer.
- Never tag, deploy or publish.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `docs/entra-setup.md` | guide | n/a | No existing step-by-step guide. Use the architecture.md tone and the RESEARCH-quoted Microsoft facts. |
| `docs/release.md` | checklist | n/a | No existing release doc. Use RESEARCH release steps and the POM release profile. |

## Metadata

**Analog search scope:** `README.md`, `docs/`, `morimail/src/{main,test}`, `morimail-sample-serial/example-campaign`, `.planning/phases/03-graph-and-entra`, `pom.xml`.
**Files scanned:** about 12 read directly, plus the full tracked-file listing (`git ls-files`; every path named above is tracked).
**Pattern extraction date:** 2026-11-22
