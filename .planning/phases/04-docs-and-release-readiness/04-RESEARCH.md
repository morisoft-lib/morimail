# Phase 4: Docs and Release Readiness - Research

**Researched:** 2026-11-22
**Domain:** Technical documentation (Entra app registration, Exchange Online RBAC for Applications), Maven Central release rehearsal, one small code fix (WR-01)
**Confidence:** HIGH (Microsoft pages read this session; the release rehearsal was run locally; the openssl recipe and the README snippet were executed and compiled)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Live-findings write-back**
- **D-01:** Update `docs/spec.md` §6.3 (the spec itself says "correct it in code and here"). Keep the table rows and add a live-status column: 403 and 404 (`ErrorInvalidUser`) are confirmed on 2026-11-22; the mailbox 401, `MailboxNotEnabledForRESTAPI` and the out-of-scope refusal are marked unverified. Replace the forum-answer sentence ("A forum answer reports 401 ...") with the 404 finding. Record the spec edit in `docs/architecture.md`. No change to the error mapping in code, because the live results match it. Every error-table row stays tested.
- **D-02:** Fix review warning WR-01 in this phase. `clean()` in `GraphTransport.java` and `EntraClientCredentials.java` must replace addresses before cutting (or cut at a token boundary), so an address straddling the cut cannot leak its start. Add one test per class. Mark WR-01 fixed in `03-REVIEW-DISPOSITION.md` or record it in the Phase 4 summary.
- **D-03:** The sample keeps Graph's default for `saveToSentItems`: it sends none, so campaign mails land in Sent Items as an audit trail. No code change. Say so in `entra-setup.md` (account.properties step) and where the sample is described.

**Setup guide (`docs/entra-setup.md`)**
- **D-04:** Follow the spec §9 order and recommend B (Exchange RBAC for Applications), with cmdlets copied from the current Microsoft page at writing time. State plainly that A (Entra admin consent) is the path verified live on 2026-11-22 and B is not yet verified live. The union-trap step warns that A's consent must be removed when B is used.
- **D-05:** The certificate step uses openssl: `req -x509` for the key and `.cer` upload, then `pkcs12 -export` for the `.p12`. Mention the `-legacy` flag only as a fallback if an old JDK 17 patch cannot read the file. Mention the client secret as the quick alternative for a first test.
- **D-06:** End the guide with a short "Not yet verified live" section: shared mailbox as sender, `from` display name, out-of-scope RBAC refusal, B itself, `MailboxNotEnabledForRESTAPI` and the mailbox 401. No inline caveats scattered through the steps.
- **D-07:** Write the guide in English. Use only example values (`example.com`, zero GUIDs). Include the propagation note: a 403 right after setup is expected and was seen live. Verification is the sample's dry run, then `--limit 1` to the owner's own address, then the example campaign to test addresses, then a second run that sends nothing.

**README quick start**
- **D-08:** The quick start has one Java block: `EntraClientCredentials`, then a `MailAccount` with transport `graph`, then `Mail.using(...).template(...).attach(...).build()`, then `Mailer.of(account).send(mail)`, with one catch per error category (transient / permanent incl. sender-rejected / auth-required) and what to do for each. After it, 2-3 lines on running `serial-mail send --dry-run` on the example campaign, and a link to `docs/entra-setup.md`. The README also covers the DOCS-03 items: what morimail is not (Exchange limits of 30/min and 10k recipients/day per mailbox, not a bulk mailer), pre-1.0 status, coordinates, at-least-once and accepted-not-delivered, and the keystore sentence (already present, keep it).
- **D-09:** The snippet is checked once by hand against the real API, with no compile-checked mirror test (Rule 0). The planner must verify every method name and signature in the snippet against the source.

**Release steps and rehearsal**
- **D-10:** The owner's release steps go into a new `docs/release.md`, a short checklist: verify the Central namespace, create the GPG key with the maintainer identity and publish it to a keyserver, put the Portal token in `settings.xml` (server id `central`, placeholders only), set the release version, deploy with `-Prelease -pl morimail -am`, review in the Portal (autoPublish false), publish, tag, and bump to the next SNAPSHOT. No secrets. The owner runs every step; Claude never runs them.
- **D-11:** Rehearsal: `-Prelease deploy -DskipUpload=true -pl morimail -am`, then inspect the bundle. It must contain only `morimail-parent` and `morimail` (pom, jar, sources, javadoc, checksums, `.asc`), with no `-tests.jar` and no sample. If the `-tests.jar` shows up, first try the documented fix (`excludeArtifacts` or the attach setting). If neither is clean, stop and ask the owner before any structural change, such as a separate testkit module.
- **D-12:** Sign the rehearsal with a throwaway GPG key in a temporary `GNUPGHOME` in the scratchpad, outside the repository, and check that every bundle file has an `.asc`. Delete the key afterwards and commit nothing key-related. `mvn -Prelease verify -Dgpg.skip -pl morimail -am` must also stay green without Javadoc warnings.

### Claude's Discretion
- How the architecture.md contract summary is laid out, as long as it stays one page and keeps the existing deviations table.
- Exact wording and section order inside the README and `entra-setup.md`, within the decisions above.
- Whether WR-01 is fixed by reordering (replace, then cut) or by a token-boundary cut, whichever is simpler.

### Deferred Ideas (OUT OF SCOPE)
- Live verification of the shared mailbox, display name, out-of-scope RBAC, the mailbox 401 and `MailboxNotEnabledForRESTAPI`: the owner, after 0.1.0 if wanted.
- A sample option for `saveToSentItems=false`: only if a campaign user asks for it.
- From Phase 3: settable timeouts, an `authority`/`graphBaseUrl` key (sovereign clouds), the >3 MB probe and upload sessions.
- Open review items not in scope: the journal has no exclusive lock (IN-06); `attachments/` still picks up `Thumbs.db`/`desktop.ini`.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DOCS-01 | `docs/entra-setup.md`: app registration + certificate, Mail.Send grant (A vs B), traps (union, 30 min-2 h, Enterprise-app IDs, Organization Management), licensed mailbox, shared-mailbox question, `saveToSentItems`, `account.properties` + env vars, dry run then `--limit 1` | "Microsoft setup facts" (cmdlets quoted from the current page), the executed openssl recipe, portal steps from the Entra pages, sample CLI facts, troubleshooting map from live results |
| DOCS-02 | `docs/architecture.md`: one-page contract summary, every deviation incl. differences from the Microsoft docs | "architecture.md delta" list: rows to add, bullets to update (live results replace "only the live test shows"), the §6.3 spec edit |
| DOCS-03 | README: what it is / is not, pre-1.0, coordinates, quick start, at-least-once + accepted-not-delivered, keystore sentence | Compiled quick-start snippet, Exchange sending limits quoted from the current Microsoft page (incl. tenant limits not in the spec) |
| DOCS-04 | `-Prelease verify -Dgpg.skip -pl morimail -am` clean; rehearsal bundle only `morimail-parent` + `morimail`; owner release steps listed | Rehearsal executed: bundle is already correct, `excludeArtifacts` not needed; rehearsal gotchas (SNAPSHOT, offline, install side effect, GNUPGHOME path length) |
</phase_requirements>

## Summary

This phase is writing plus one 10-line code fix; no new library, dependency or feature is needed. Every open technical question was settled by reading the current Microsoft and Sonatype pages and by running the pieces locally. The three results the planner must build on: (1) the existing parent POM release profile with `attach=false` on the test-jar **already produces the correct bundle** (30 entries: `morimail-parent` pom and `morimail` pom, jar, sources, javadoc, each with `.asc` and four checksums; no `-tests.jar`, no sample), so `excludeArtifacts` is not needed and the research flag is closed; (2) the Exchange RBAC page (updated 2026-10-27) confirms the spec §9 cmdlets and traps and adds details the guide must carry (Exchange Administrator role in Entra, the test cmdlet ignores Entra grants and bypasses the cache, `Alias`/`EmailAddresses` rather than `PrimarySmtpAddress` for scope filters); (3) the openssl recipe and the README snippet were executed/compiled against the real code.

Four things behave differently from what the CONTEXT assumes and need a planned answer. First, a rehearsal at `0.1.0-SNAPSHOT` with `-DskipUpload=true` creates **no bundle zip** (it only stages into `target/central-deferred`), and offline mode skips the `publish` goal entirely; the zip only appears at a release version and online. The rehearsal therefore runs in a scratch copy of `git archive HEAD` with the version `sed`-ed to `0.1.0`. Second, `deploy` also runs `install`, writing into `~/.m2`; clean it afterwards. Third, `gpg-agent` cannot start with a `GNUPGHOME` under the long scratchpad path (socket name limit); use a short symlink such as `/tmp/gh-reh -> <scratchpad>/gnupg`. Fourth, the WR-01 reorder "replace, then cut" would bring back the quadratic regex the current code deliberately avoids; the token-boundary cut proposed in `03-REVIEW.md` is the right fix.

No live-test report is outstanding: the findings are in `03-UAT.md` (status complete, 2026-11-22) and CONTEXT.md. Code already matches them (3 MiB / 4 MiB constants, `--rate` 20, 403/404 mapping), so criterion 1 is a doc edit plus the WR-01 fix, not a code correction.

**Primary recommendation:** Plan four small streams in this order: (a) WR-01 fix + two tests + disposition; (b) spec §6.3 / architecture.md write-back; (c) `entra-setup.md`, README, `release.md`; (d) the rehearsal run last (it uses `git archive HEAD`, so it sees only committed docs), recording its result in the phase summary.

## Architectural Responsibility Map

This phase has no runtime capability to place in a tier; it documents capabilities that live in the tiers below.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Mail.Send grant (A: Entra consent, B: Exchange RBAC) | Microsoft Entra / Exchange Online (external admin plane) | — | Configured by the tenant admin outside the library; the guide only documents it |
| Token acquisition (certificate / secret) | Library `entra` package | Entra token endpoint | `EntraClientCredentials`; unchanged this phase |
| Send + error mapping | Library `graph` package | Graph / Exchange | Mapping unchanged; spec §6.3 text catches up |
| Error text hygiene (`clean()`) | Library `graph` and `entra` (duplicated on purpose) | — | WR-01 fix site; the two packages must not import each other |
| Release bundle | Maven build (parent POM `release` profile) | Central Portal (validation, publish) | Plugin stages attached artifacts only |

## Standard Stack

No new libraries. The stack is fixed by Phases 1-3 (`pom.xml`, `.claude/CLAUDE.md`).

### Core (already pinned, verified in `pom.xml`)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| central-publishing-maven-plugin (`org.sonatype.central`) | 0.12.0 | Stage and bundle for Central; `skipUpload` rehearsal | Already in the `release` profile with `autoPublish=false` [VERIFIED: pom.xml:159-161, 224-233] |
| maven-gpg-plugin | 3.2.8 | Signing in `verify` | [VERIFIED: pom.xml:146-149] |
| maven-source-plugin / maven-javadoc-plugin | 3.4.0 / 3.12.0 | `-sources.jar`, `-javadoc.jar` (doclint all, failOnWarnings) | [VERIFIED: pom.xml:138-145, 197-211] |
| Maven wrapper | Maven 3.10.1, only-script | Build | Never Maven 4 (Central plugin documents it as broken) [CITED: central.sonatype.org/publish/publish-portal-maven/] |

### Supporting tools used by docs and rehearsal (local, not dependencies)
| Tool | Version here | Use |
|------|-------------|-----|
| openssl | 3.5.5 | `req -x509` key + `.cer`, `pkcs12 -export` (guide, executed) |
| keytool (JDK) | Temurin 21.0.13 | Optional check of the `.p12` |
| gpg | 2.4.8 | Throwaway signing key for the rehearsal |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Scratch-copy rehearsal (`git archive` + `sed`) | `versions:set` in the repo, then revert | Touches the working tree and needs an unpinned plugin; the scratch copy commits nothing and was verified |
| Token-boundary cut (WR-01) | Replace-then-cut | Replace-then-cut reintroduces the quadratic address regex on a long token without `@` (the reason for the current order, see the code comment in both `clean()`) |
| `excludeArtifacts` | `attach=false` (existing) | `excludeArtifacts` works by artifactId only, so it could never exclude one classifier of `morimail`; not needed anyway |

**Installation:** none. **Version verification:** all versions read from `pom.xml` this session; the plugin 0.12.0 jar is in the local repo and its `plugin.xml` was read.

## Package Legitimacy Audit

This phase installs no external packages. No `npm`/`pip`/`cargo` command is involved, and the Maven plugins above were already in the build and ran today. Nothing to audit; no `[SUS]`/`[SLOP]` items; no `checkpoint:human-verify` for installs is required.

## Architecture Patterns

### Documentation flow (what feeds what)

```
03-UAT.md live results ──► spec §6.3 table (+ live-status column)
        │                  architecture.md: §6.3 edit row, updated "Differences" bullets
        │
Microsoft pages (RBAC, certs, credentials) ──► entra-setup.md (cmdlets quoted, dated)
        │
code (signatures) ──► README quick start (compiled once)  ──► link ──► entra-setup.md
        │
parent POM release profile ──► rehearsal (scratch copy, version 0.1.0, throwaway key)
        │                          └─► bundle inspection ──► docs/release.md (owner steps)
WR-01 review finding ──► clean() x2 + 2 tests ──► 03-REVIEW-DISPOSITION.md
```

### Recommended file changes
```
docs/entra-setup.md      NEW   (DOCS-01)
docs/release.md          NEW   (DOCS-04, D-10)
docs/architecture.md     EDIT  (DOCS-02: summary on top, keep tables, update bullets)
docs/spec.md             EDIT  (§6.3 table/paragraph; optionally §8.3 last paragraph, §12)
README.md                EDIT  (DOCS-03)
morimail/src/main/java/.../graph/GraphTransport.java            EDIT clean()
morimail/src/main/java/.../entra/EntraClientCredentials.java    EDIT clean()
morimail/src/test/java/.../graph/GraphErrorMappingTest.java     +1 test
morimail/src/test/java/.../entra/EntraTokenErrorsTest.java      +1 test
.planning/phases/03-graph-and-entra/03-REVIEW-DISPOSITION.md    WR-01 (and IN-01) fixed
.gitignore               OPTIONAL: *.p12, *.key, *.pem (keep the tracked test keystore)
```

### Pattern 1: The deviations table stays the single record
Add rows as `Spec | morimail | Reason` (existing pattern, `docs/architecture.md:7-24`). New rows for this phase: the §6.3 spec edit (D-01); §8.3 rate paragraph resolved by the owner (`--rate 20` confirmed); the rehearsal facts belong in `release.md`, not in the deviations table.

### Pattern 2: One page of contracts
`docs/architecture.md` currently has a placeholder intro ("The full contract summary follows with the 0.1.0 documentation") [VERIFIED: docs/architecture.md:3]. Replace that paragraph with a short contract summary (about 25-30 lines) placed above the deviations table: the two extension points (`TransportProvider`, `TokenProvider` with the `freshAccessToken` default), the error hierarchy and what each category tells the caller to do, accepted-not-delivered / at-least-once, the registry (explicit built-in list + `ServiceLoader`, duplicate ids fail), options opaque to the core and validated in `validate`, thread-safety, the import rules (core JDK-only; `graph` and `entra` do not import each other), and the logging rule. Keep the existing "Contracts" bullets but fold them in, so the page does not grow a second contracts section.

### Anti-Patterns to Avoid
- **Scattering "not verified" caveats through the guide.** D-06 puts them in one closing section.
- **Copying cmdlets from the spec.** The spec says copy from the Microsoft page at writing time; the page is quoted below with its date.
- **Rehearsing on the repository's working tree at SNAPSHOT.** It yields no zip (see Pitfall 1).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Excluding the test-jar from the bundle | A custom bundle script or `excludeArtifacts` config | The existing `attach=false` in `morimail/pom.xml:79` | Already yields the correct bundle (verified) |
| Rehearsal bundle assembly | Hand-zipped bundle, hand-written checksums | `central-publishing-maven-plugin` with `skipUpload` | The plugin generates md5/sha1/sha256/sha512 and the zip |
| README snippet verification | A compile-checked mirror test | One-off `javac` of the snippet (done here) | D-09 / Rule 0 |
| Certificate creation | Java code or a script | The two openssl commands | D-05 |
| WR-01 | A shared sanitiser class | Same 6-line edit in each `clean()` | `graph` and `entra` must not import each other; the duplication is intentional |

## Microsoft setup facts (for `entra-setup.md`)

All from pages fetched this session. Dates are the pages' own `updated_at`.

### Exchange RBAC for Applications
Source: https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac, `updated_at` 2026-10-27 [CITED]. The cmdlets below are quoted from the page.

```powershell
Connect-ExchangeOnline -UserPrincipalName <admin UPN>        # connect page, updated 2026-06-18
New-ServicePrincipal -AppId <Client Application ID in AAD> -ObjectId <Service principal object ID in AAD> -DisplayName <name>
New-ManagementScope -Name "<scope name>" -RecipientRestrictionFilter "<OPATH filter>"
New-ManagementRoleAssignment -App <ObjectID, AppID, or DisplayName> -Role "Application Mail.Send" -CustomResourceScope "<scope name>"
Test-ServicePrincipalAuthorization -Identity <ObjectID, AppID, or DisplayName> -Resource <target mailbox>
```

Facts the page states and the guide must carry:
- **Enterprise-application IDs:** "Don't use the IDs from the App Registrations page, as it shows different values. The red outlined 'Application ID' is the AppID and the 'Object ID' is the ServiceID." The page also offers `Get-MgServicePrincipal` as another way to find them. [CITED]
- **Service principals cannot be created with Exchange tools:** "Service Principals can't be created directly using Exchange Online tools"; `New-ServicePrincipal` creates a pointer to an existing Entra service principal. [CITED]
- **Roles required (differs from spec step 6):** "You need to be a member of the Organization Management role group to assign these permissions" **and** "In Microsoft Entra ID, you need the Exchange Administrator role to assign these permissions." The spec names only Organization Management; add the Entra role. [CITED]
- **Union trap:** "the assigned permissions are a union operation on the permissions from Microsoft Entra ID and the permissions assigned in Exchange Online RBAC. Each authority can act independently." The page's example is `Mail.Read`; the same holds for `Mail.Send` (`Application Mail.Send` -> `Mail.Send`: "Allows the app to send mail as any user without a signed-in user"). [CITED]
- **Delay:** "Changes to app permissions are subject to cache maintenance that varies between 30 minutes and 2 hours depending on the app's recent usage. When you test configurations, the test command bypasses this cache. The cache of an app with no inbound calls to APIs is reset after 30 minutes. The cache of an active app is kept alive for up to 2 hours." [CITED]
- **The test cmdlet shows RBAC only:** "This method excludes permissions that might be granted separately in Microsoft Entra ID." So `InScope = True` does **not** prove the union trap is cleared; the guide must tell the admin to also check the Entra Admin consent tab. `InScope` is true/false per mailbox; omitting `-Resource` gives "Not Run". [CITED]
- **Other limits worth one line each:** exclusive management scopes do not restrict app access; Autodiscover does not work with RBAC application roles; up to 10,000 applications per org. [CITED]
- **Scope filter properties** [CITED: learn.microsoft.com/en-us/powershell/exchange/recipientfilter-properties]: the page tells you "Don't use the *PrimarySmtpAddress* property; use the *EmailAddresses* property instead"; `Alias` is a single-valued Exchange alias; `CustomAttribute1`..`15` are free strings (the RBAC page's own example uses `CustomAttribute1`); `RecipientTypeDetails` values are documented under `Get-Recipient`. Recommendation: use `Alias -eq '<alias>'` in the example (single value, documented property, and the guide tells the admin to confirm with `Test-ServicePrincipalAuthorization ... -Resource <mailbox>` and check `InScope`). The exact filter on a real mailbox has not been exercised (A3).
- **Removing the Entra consent** (needed for the union trap) [CITED: learn.microsoft.com/en-us/entra/identity/enterprise-apps/manage-application-permissions, updated 2026-02-19]: sign in to the Entra admin center as at least Cloud Application Administrator; "Entra ID > Enterprise apps > All applications", select the app, "Permissions", tab "Admin consent", the "..." control of the permission, "Revoke permission". The same page warns that revoking does not stop re-consent, so also remove `Mail.Send` from the app registration's API permissions list.

### App registration and credentials
- Register: "Browse to **Entra ID** > **App registrations** and select **New registration**", name, "**Single tenant only - <your tenant>**" recommended, **Register**; the Overview page shows the **Application (client) ID**. [CITED: learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app, 2026-05-14] The Directory (tenant) ID is also on the Overview page [ASSUMED] (A1).
- Certificate: "In the Microsoft Entra admin center, in **App registrations**, select your application. Select **Certificates & secrets** > **Certificates** > **Upload certificate**. Select the file you want to upload. It must be one of the following file types: *.cer*, *.pem*, *.crt*. Select **Add**. Record the certificate **Thumbprint**." [CITED: .../how-to-add-credentials, updated 2026-11-17] morimail does not need the thumbprint (it computes `x5t#S256` itself); the portal thumbprint is only for the admin's own check.
- Secret: "Certificates & secrets > Client secrets > New client secret"; lifetime at most 24 months, Microsoft recommends under 12; the **Value** "is *never displayed again* after you leave this page"; "Client secrets are less secure than certificate or federated credentials and therefore should **not be used** in production environments." Self-signed certificates are fine for testing; production should use a CA-signed certificate and Key Vault. [CITED: .../how-to-add-credentials]
- Path A: the register page documents **API permissions** under Manage and **Grant admin consent for <tenant name>**, with **Granted for <tenant name>** as the status after refresh. [CITED: quickstart-register-app] The permission to add is Microsoft Graph, Application permissions, `Mail.Send` (spec §9 step 3A).
- Certificate assertion: unchanged and live-confirmed (PS256, `x5t#S256`). [CITED: .../certificate-credentials, updated 2026-06-15; VERIFIED live per 03-UAT.md test 1]

### Exchange sending limits (README "what it is not", guide mailbox step)
Source: https://learn.microsoft.com/en-us/office365/servicedescriptions/exchange-online-service-description/exchange-online-limits, `updated_at` 2026-11-11 [CITED]:
- "Message rate limit: 30 messages per minute"; "Recipient rate limit: 10,000 recipients per day"; "Recipient limit: Customizable up to 1000 recipients" per message. The 24-hour rate is a sliding window ("won't be able to send messages again until 09:00 AM the next day" example).
- The page words the message rate limit as applying "via SMTP client submission". It does not say it governs Graph `sendMail`. The README should say "documented per-mailbox limits" and not promise how Graph enforces them. The 20/min default sits below 30/min.
- **Not in the spec, worth adding:** the Tenant External Recipient Rate Limit (TERRL, depends on licence count, trial tenants capped at 5,000 external recipients per day) and the `onmicrosoft.com` throttle: "Outbound mail from these domains is limited to 100 external recipients per organization within a 24-hour rolling window" (NDR code 550 5.7.236). The page recommends Azure Communication Services email for bulk external mail. A sender on an `onmicrosoft.com` address will hit 100 external recipients per day; the guide should say to use a verified custom domain, and the README's "not a bulk mailer" paragraph can cite both limits.

## Release rehearsal (settled by execution)

### Result [VERIFIED: ran locally 2026-11-22]
Run in a scratch copy at version `0.1.0`, online, with a throwaway key, `-Prelease clean deploy -DskipUpload=true -DskipTests -pl morimail -am`: BUILD SUCCESS, reactor `morimail-parent` + `morimail`. `central-bundle.zip` has exactly 30 entries:

```
de/morisoft/morimail-parent/0.1.0/morimail-parent-0.1.0.pom   (+ .asc .md5 .sha1 .sha256 .sha512)
de/morisoft/morimail/0.1.0/morimail-0.1.0.pom                 (+ same 5)
de/morisoft/morimail/0.1.0/morimail-0.1.0.jar                 (+ same 5)
de/morisoft/morimail/0.1.0/morimail-0.1.0-sources.jar         (+ same 5)
de/morisoft/morimail/0.1.0/morimail-0.1.0-javadoc.jar         (+ same 5)
```

No `-tests.jar`, no `morimail-sample-serial`. All five signed files verified with `gpg --verify` ("Good signature" x5). So `attach=false` plus `-pl morimail -am` is sufficient; **`excludeArtifacts` is not needed**, and it could not do the job anyway (parameter `java.util.List`, "Assign artifacts that must not be added to the bundle", matched by artifactId, so it cannot single out a classifier of `morimail`) [VERIFIED: plugin.xml of central-publishing-maven-plugin 0.12.0 in the local repo; CITED: Sonatype page "by their artifactId"]. This closes the research flag; D-11's fallback chain is not triggered.

The plugin does not validate POM metadata (Sonatype page [CITED]); the Portal does that on upload, which only the owner can see. Metadata present in the parent POM: name, description, url, MIT license, developer, scm [VERIFIED: pom.xml:12-36]; `morimail/pom.xml` has name and description [VERIFIED: morimail/pom.xml:14-15].

`./mvnw -o -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` was run today: BUILD SUCCESS, no WARN lines in the filtered output (criterion 5, first half, holds before any change; the WR-01 edit must keep it that way).

### Rehearsal recipe (for the plan; the executor runs it, never the owner's real steps)
```bash
SP=<scratchpad>; REL=$SP/rel; GH=/tmp/gh-reh        # GH is a SHORT symlink, see Pitfall 3
mkdir -m 700 $SP/gnupg && ln -sfn $SP/gnupg $GH
cat > $SP/keyparams <<'EOF'
%no-protection
Key-Type: RSA
Key-Length: 2048
Name-Real: Throwaway Rehearsal
Name-Email: rehearsal@example.com
Expire-Date: 1d
%commit
EOF
GNUPGHOME=$GH gpg --batch --gen-key $SP/keyparams
rm -rf $REL && mkdir $REL && git archive HEAD | tar -x -C $REL
cd $REL && sed -i 's/0\.1\.0-SNAPSHOT/0.1.0/' pom.xml morimail/pom.xml morimail-sample-serial/pom.xml
GNUPGHOME=$GH ./mvnw -B -ntp -Prelease clean deploy -DskipUpload=true -pl morimail -am
unzip -Z1 target/central-publishing/central-bundle.zip | sort        # expect 30 lines, no "tests", no "sample"
# verify every non-.asc/non-checksum file has a Good signature (see Code Examples)
# cleanup (mandatory): gpgconf --kill gpg-agent; rm -rf $GH $SP/gnupg $SP/keyparams; rm -rf ~/.m2/repository/de/morisoft
```
`skipUpload` sends nothing and needs no Central credentials [VERIFIED: plugin.xml description: "Nothing is sent to Central when this is set, so no credentials for publishingServerId are needed"]. The `publish` goal still needs online mode, so the build must not use `-o` (Pitfall 2). Because it runs on `git archive HEAD`, run it after the docs are committed.

### What `docs/release.md` should contain (owner checklist, D-10)
Quoted facts to use:
1. **Namespace** (`de.morisoft`, DNS-verified): central.sonatype.com, username menu, "View Namespaces", "Add Namespace", enter the namespace, "Verify Namespace", copy the Verification Key, create a **TXT** record on the exact domain `morisoft.de`, then confirm. "Don't confirm early": a cached NXDOMAIN can delay verification. Takes minutes after the record exists. [CITED: central.sonatype.org/register/namespace/] (spec §13.2 already says the owner must control `morisoft.de`.)
2. **GPG key** with identity `Stephan Wald <stephan@morisoft.de>`: `gpg --gen-key`, `gpg --list-keys`, `gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`. Central supports `keyserver.ubuntu.com`, `keys.openpgp.org`, `pgp.mit.edu`. [CITED: central.sonatype.org/publish/requirements/gpg/] Passphrase through gpg-agent/pinentry (`export GPG_TTY=$(tty)`); never `gpg.passphrase` in settings (deprecated by the plugin) [CITED: project STACK notes].
3. **Portal token:** go to https://central.sonatype.com/usertoken, "Generate User Token", display name and expiry; "Tokens cannot be retrieved once the modal closes". The page has no `settings.xml` snippet; the plugin page says to add a `<server>` whose `<id>` is `central` with the token's username and password. [CITED: generate-portal-token and publish-portal-maven pages] Placeholders only in the doc:
   ```xml
   <server><id>central</id><username>TOKEN_USERNAME</username><password>TOKEN_PASSWORD</password></server>
   ```
4. **Release version:** in all three POMs (`pom.xml:9`, `morimail/pom.xml:10`, `morimail-sample-serial/pom.xml:10`, the last two are the `<parent>` version). The `sed` above is the verified way; a commit "release 0.1.0" follows.
5. **Deploy:** `./mvnw -B -ntp -Prelease clean deploy -pl morimail -am` (tests run; signing prompts via gpg-agent). `autoPublish` is `false` and `waitUntil` defaults to `validated`, so the deployment stops at validated. [VERIFIED: pom.xml:231; CITED: plugin page]
6. **Review in the Portal, then publish.** Release on Central is permanent (spec §13.2).
7. **Tag** `v0.1.0` and push; **bump** to the next SNAPSHOT (three places again); then update the README sentence "0.1.0 ... not published yet" and the "not yet released" line.
8. Maven 4 must not be used (wrapper pins 3.10.1).

## Common Pitfalls

### Pitfall 1: `-DskipUpload=true` at a SNAPSHOT version makes no bundle
**What goes wrong:** the run ends "BUILD SUCCESS" with no zip. The plugin's own message: "Skipping the deployment of the staged SNAPSHOT artifacts at user's request (skipUpload). They are in .../target/central-deferred". [VERIFIED: ran it]
**Why:** SNAPSHOTs are staged, not bundled (plugin.xml: "For snapshots, the artifacts are staged, but not deployed").
**How to avoid:** rehearse in a scratch copy at version `0.1.0` (recipe above). The `central-deferred` listing (staged, SNAPSHOT) also already shows only parent + `morimail` and no tests-jar, so it is a usable first check.
**Warning signs:** no `target/central-publishing/central-bundle.zip`.

### Pitfall 2: offline mode silently skips the publish goal
**What goes wrong:** `./mvnw -o ... deploy` finishes green with "[WARNING] Goal publish requires online mode for execution but Maven is currently offline, skipping" and stages nothing. [VERIFIED: ran it] The REPO-08 offline rule applies to `verify`, not to the rehearsal.
**How to avoid:** run the rehearsal online with `skipUpload`; nothing is sent.

### Pitfall 3: gpg-agent cannot start under the scratchpad path
**What goes wrong:** `gpg --gen-key` fails with "can't connect to the gpg-agent" and `gpg-agent: socket name '.../scratchpad/gnupg/S.gpg-agent.extra' is too long` (the scratchpad path is 91 characters, the socket path limit is about 108 and gpg adds a suffix). [VERIFIED: ran it; no `/run/user/<uid>` exists here]
**How to avoid:** keep the key material physically in the scratchpad (D-12) but point `GNUPGHOME` at a short symlink (`/tmp/gh-reh -> <scratchpad>/gnupg`); verified working. Kill the agent and delete the symlink and key afterwards.

### Pitfall 4: `deploy` also runs `install`
**What goes wrong:** the rehearsal writes `~/.m2/repository/de/morisoft/{morimail,morimail-parent}/{0.1.0-SNAPSHOT,0.1.0}` including artifacts signed with the throwaway key. [VERIFIED: ran it; the directory did not exist before, I removed it afterwards]
**How to avoid:** `rm -rf ~/.m2/repository/de/morisoft` as the last rehearsal step (only if nothing else created it; check the timestamp).

### Pitfall 5: WR-01 "replace, then cut" brings back quadratic regex time
**What goes wrong:** the current order (collapse, cut to 1000, replace addresses) exists because `[^\s'"<>]+@[^\s'"<>]+` is quadratic on a long token without `@` (code comment in both `clean()`). Replacing before cutting runs it on the full server text again. The existing `@Timeout(10)` tests (`aVeryLongCodeIsCutAndCleanedQuickly`, `aVeryLongDescriptionIsCutAndCleanedQuickly`) would catch the regression.
**How to avoid:** use the token-boundary cut from `03-REVIEW.md` (Code Examples). Cutting at the last space also removes a lone trailing high surrogate risk.

### Pitfall 6: a 403 during propagation looks like a permanent rejection
**What goes wrong:** live, the certificate flow got 3 of 10 sends as 403 `ErrorAccessDenied` minutes after consent (03-UAT test 1). The library maps 403 to `SenderRejectedException` (permanent) and the sample stops with exit 3, although the cause is propagation lag, not a wrong mailbox.
**How to avoid (docs only, no code change per D-01):** the guide states a 403 right after setup is expected, to wait (30 min to 2 h) and rerun; a rerun resends only rows not journalled as accepted (the exit-3 row has a failed journal line and is retried) [VERIFIED: 02/03 STATE decisions]. Put the same sentence next to the 403 row in spec §6.3 / architecture.md.

### Pitfall 7: the guide's `--limit 1` needs a campaign copy
The example campaign's recipients are `example.org` addresses. `--limit 1` on it would try to mail `anna@example.org`. The guide must tell the owner to copy `example-campaign/` and put their own address in `recipients.csv` (and the real values in a copy of `account.properties`), and say that the journal (`journal.jsonl` in the campaign directory, git-ignored via `*.jsonl`) then records that row as accepted, so the later full run of that same copy skips it. The campaign id defaults to the directory name, so a fresh copy name gives a fresh journal.

### Pitfall 8: key material in the repository
`.gitignore` ignores `*.pfx`, `*.jsonl`, `out/`, `account.properties` (except the example) but **not** `*.p12`, `*.key`, `*.pem`, `*.cer` [VERIFIED: .gitignore]. The guide tells the owner to create and keep these outside the repository. An optional hygiene edit: add `*.p12`, `*.key`, `*.pem` with `!morimail/src/test/resources/test-keystore-throwaway.p12` (the file is already tracked, so ignore rules do not affect it). Rule 0 allows it as REPO-11 hygiene; the planner decides.

## Code Examples

### Certificate creation (executed here, OpenSSL 3.5.5; loaded by `EntraClientCredentials.withCertificate(tenant, client, Path, char[])` on JDK 21)
```bash
# Keep these files OUTSIDE the repository.
export MAIL_CERT_PASSWORD='<choose a password>'
openssl req -x509 -newkey rsa:2048 -sha256 -nodes -keyout app.key -out app.cer \
    -days 730 -subj "/CN=morimail-example"
openssl pkcs12 -export -inkey app.key -in app.cer -name morimail \
    -out app.p12 -passout env:MAIL_CERT_PASSWORD
# upload app.cer in the portal (Certificates & secrets > Certificates > Upload certificate)
# fallback only if an old JDK 17 patch cannot read app.p12 (the -legacy form also worked here):
openssl pkcs12 -export -legacy -inkey app.key -in app.cer -out app.p12 -passout env:MAIL_CERT_PASSWORD
```
Observed: OpenSSL 3 writes PBES2/PBKDF2/AES-256-CBC with a SHA-256 MAC; the library loaded it and `keytool -list` showed one `PrivateKeyEntry`. JDK 17 itself was not available locally; the last CI run on Temurin 17 loaded the legacy-format test keystore, but not an OpenSSL-3-format file, hence D-05's `-legacy` fallback line [VERIFIED: ran; CI run 45047907977 success on Temurin 17]. `-passout env:` keeps the password off the command line. (The six-character minimum is keytool's rule, not openssl's, but the guide should simply say "choose a strong password".)

### README quick start (compiled with `javac --release 17` against `morimail/target/classes` + Jackson 2.23.0; compiled clean)
```java
import de.morisoft.morimail.*;
import de.morisoft.morimail.entra.EntraClientCredentials;
import java.nio.file.Path;
import java.util.Map;

var auth = EntraClientCredentials.withCertificate(
        "00000000-0000-0000-0000-000000000000",          // tenant id
        "00000000-0000-0000-0000-000000000000",          // client id
        Path.of("/path/app.p12"), System.getenv("MAIL_CERT_PASSWORD").toCharArray());

MailAccount account = MailAccount.builder("entra-main")
        .transport("graph")
        .auth(auth)
        .defaultFrom("newsletter@example.com")
        .build();
Mailer mailer = Mailer.of(account);

MailTemplate template = new MailTemplate(
        "Hello $$firstName$$", "<p>Hello $$firstName$$,</p>", "Hello $$firstName$$,");
Mail mail = Mail.using("entra-main")
        .to("anna@example.org")
        .template(template, Map.of("firstName", "Anna"))
        .attach(Path.of("info.pdf"))
        .build();                                         // throws InvalidMailException

try {
    mailer.send(mail);                                    // returns = accepted, not delivered
} catch (TransientMailException e) {
    // try again later; e.retryAfter() is an Optional<Duration>
} catch (SenderRejectedException | AuthRequiredException e) {
    // stop and fix the setup: the mailbox or the app grant, or the credentials
} catch (PermanentMailException e) {
    // this mail will never be accepted as it is (includes InvalidMailException)
} catch (MailException e) { /* not reached today: the three branches above cover every subclass */ }
```
Signatures checked in source: `Mail.using(String)` `Mail.java:30`; `.to(String...)` `:85`; `.template(MailTemplate, Map<String, ?>)` `:258`; `.attach(Path)` `:279`; `build() throws InvalidMailException` `:327`; `MailAccount.builder(String)` `:32`, `.transport(String)` `:87`, `.auth(TokenProvider)` `:98`, `.defaultFrom(String)` `:122`, `build()` `:139`; `Mailer.of(MailAccount...)` `:30`, `send(Mail) throws MailException` `:60`; `record MailTemplate(String subject, String html, String text)` `MailTemplate.java:30`; `EntraClientCredentials.withCertificate(String, String, Path, char[])` `:174-175`; `TransientMailException.retryAfter()` returns `Optional<Duration>` `:58`; `SenderRejectedException` and `InvalidMailException` extend `PermanentMailException` [VERIFIED: Read/grep of the source files this session]. The multi-catch `SenderRejectedException | AuthRequiredException` before `PermanentMailException` is legal because `SenderRejectedException` is a subclass listed first (it compiled). Drop the final `catch (MailException)` from the README, since the snippet should stay short; or keep it only if the planner wants a safety net (the compiler accepts it).

### Sample commands (executed on the built jar)
```bash
./mvnw -B -ntp -DskipTests package -pl morimail-sample-serial -am
cd morimail-sample-serial
java -jar target/morimail-sample-serial-0.1.0-SNAPSHOT.jar send \
    --account example-campaign/account.properties --campaign example-campaign --dry-run --out out
```
Output observed: `attachments: info.pdf (application/pdf, 602 bytes), logo.png (image/png, 60959 bytes)`, five `[n/5] ... rendered` lines, `dry run: 5 mails written to ...`, exit 0, with no environment variables set and a non-existent `/path/app.p12` [VERIFIED: ran]. Options and defaults [VERIFIED: SendCommand.java:36-76]: `--account`, `--campaign` required; `--campaign-id`, `--dry-run`, `--out` (default `out`), `--limit`, `--rate` (default 20), `--journal` (default `journal.jsonl` in the campaign directory). Exit codes [VERIFIED: SerialMail.java Javadoc]: 0 nothing failed, 1 at least one mail failed or the journal could not be written, 2 pre-flight/argument problem, 3 stopped by a rejected sender or required authentication. The jar's file name changes with the version; at a SNAPSHOT it is `...-0.1.0-SNAPSHOT.jar`, so the guide should say `target/morimail-sample-serial-<version>.jar`.

The sample sends no `saveToSentItems` (it passes only `baseUrl`) [VERIFIED: SerialMail.java:76-80], so Graph's default (save to Sent Items) applies, matching D-03.

### WR-01 fix (from `03-REVIEW.md`, adapted to the two files)
`GraphTransport.clean` (collapse is `replaceAll(" {2,}", " ")` after control characters/whitespace became spaces) and `EntraClientCredentials.clean` (collapse is `replaceAll("\\s+", " ")`). Replace the `String text = (collapsed.length() > MAX_CLEAN_INPUT ? ...)` expression with:
```java
if (collapsed.length() > MAX_CLEAN_INPUT) {
    collapsed = collapsed.substring(0, MAX_CLEAN_INPUT);
    int lastSpace = collapsed.lastIndexOf(' ');
    collapsed = lastSpace > 0 ? collapsed.substring(0, lastSpace) : "";   // never keep a partial token
}
String text = collapsed.replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>").trim();
```
Update the in-code comment (it currently says "collapse first, then cut, then replace addresses ... a cut before collapsing could split an address"), and the `clean()` Javadoc in `GraphTransport` if it mentions the cut (doclint runs under `-Prelease`, so run `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` after the edit). A single token longer than 1000 characters now yields `"-"`, which is acceptable (documented as a safe loss).

Test per class, next to `anAddressAfterALongWhitespaceRunLeavesNoPartOfItInTheMessage` (`GraphErrorMappingTest.java:367-373`, `EntraTokenErrorsTest.java:221-229`): an input where the `@` falls after character 1000 and the local part starts before it, for example `"x".repeat(990) + " " + "alice".repeat(5) + "@example.org"` (the space is at index 990, the local part spans 991-1015). Assert the message does not contain `alice`. Graph: put it in `errorBody(..., "m")` as the code and assert `doesNotContain("alice")`; Entra: put it in `error_description` and assert the same. Verify the new tests fail on the old code first (the reviewer reproduced the leak with a similar input). Then edit `03-REVIEW-DISPOSITION.md`: WR-01 `disposition: fixed` (and IN-01, which asks for exactly this test), frontmatter `open: 0`, and the table rows, or record it in the Phase 4 summary (D-02 allows either).

### Verification loop for the bundle
```bash
cd $SP/bx && rm -rf ./* && unzip -q $REL/target/central-publishing/central-bundle.zip
find . -type f ! -name '*.asc' ! -name '*.md5' ! -name '*.sha1' ! -name '*.sha256' ! -name '*.sha512' | while read f; do
  test -f "$f.asc" && GNUPGHOME=$GH gpg --verify "$f.asc" "$f" 2>&1 | grep -q 'Good signature' && echo "OK  $f" || echo "BAD $f"
done                                                  # expect 5 x OK
unzip -Z1 $REL/target/central-publishing/central-bundle.zip | grep -ciE 'tests|sample'   # expect 0
```

## Spec §6.3 write-back (D-01), exact content to land

Replace the table in `docs/spec.md` (lines 226-234, [VERIFIED: docs/spec.md:222-238]) with a third column and the live results from `03-UAT.md`:

| Response | Exception | Live status (2026-11-22) |
|---|---|---|
| 202 | none | confirmed (certificate flow and secret flow) |
| 429, 503, 504 | `TransientMailException`, with `Retry-After` when present | WireMock only |
| Network or timeout error | `TransientMailException` | WireMock only |
| 401 after a fresh token was obtained | `SenderRejectedException` if the body points at the mailbox, otherwise `AuthRequiredException` | unverified live (WireMock only) |
| 403 | `SenderRejectedException` (mailbox outside the app's scope) | confirmed: `ErrorAccessDenied`. Also seen intermittently for minutes after a permission grant while Exchange propagates it |
| 404 | `SenderRejectedException` (mailbox does not exist) | confirmed: `ErrorInvalidUser` for a sender without an Exchange mailbox |
| 400, 413 | `PermanentMailException` | WireMock only; no 413 seen: attachments of 2,900,000 and 3,100,000 bytes were accepted |

Plus: replace the sentence "A forum answer reports 401 for a sender without an Exchange Online mailbox; treat that as unconfirmed until reproduced." with the 404 `ErrorInvalidUser` finding (state that it is a 404, not a 401). State that `MailboxNotEnabledForRESTAPI` and the out-of-scope RBAC refusal were not provoked. Change the "Starting table. Verify each row ..." lead-in to say the table was checked on 2026-11-22. Also update the architecture.md bullet "The mailbox error code for an unlicensed or missing mailbox is not documented ... the live test is where it gets corrected" (`docs/architecture.md:49`) to the live result, and the Entra bullet "only the live test shows whether the service accepts PS256" (`:40`) to "accepted live on 2026-11-22". The `mailbox` hint in the 401 rule stays an assumption (A4).

Optional spec tidy-ups the owner's confirmations make possible (spec is binding, so keep them minimal; the planner decides): §8.3's last paragraph ("The default of 20 mails per minute is a guess ... look up the current documented limits") can state the limits quoted above and that 20 is confirmed; §12's table rows for LICENSE holder, SECURITY contact, trailer, rate are confirmed by the owner (03-UAT test 5). Record any such edit in architecture.md as D-01 requires for §6.3.

### The "every error-table row is still tested" check
`GraphErrorMappingTest` covers: status table incl. 429/503/504 with `Retry-After` (`everyStatusMapsToItsExceptionAndCostsOneRequest` `:212`, `retryAfterIsReadAsSecondsOnly` `:246`), network/timeout (`aResetConnectionIsTransientWithTheCause` `:464`, `aSlowAnswerIsATransientTimeout` `:475`), the 401 retry and second-401 mailbox rule (`:92`, `:110`, `mailboxHint...` `:147/:154`), 400/413/403/404 via the parameterized status test, and `graphsOwnMessageNeverReachesTheExceptionMessage` `:387` for the 404 `ErrorInvalidUser` scenario [VERIFIED: grep of method names in GraphErrorMappingTest.java; the parameter values were not re-read]. The planner's verification step is simply "full `./mvnw verify` stays green; no test removed".

## architecture.md delta (DOCS-02)

Existing deviation rows already cover: `freshAccessToken`, `Map<String, ?>`, unchecked registry/config exceptions, JUnit 6, reactor-only test-jar and the `-pl` release scope, `authority(URI)`, PKCS12 `char[]`, fixed timeouts, `baseUrl`, 3 MiB/4 MiB, error-class rule, no Graph free text, `Retry-After`, token errors, `changeit` keystore, one live test [VERIFIED: docs/architecture.md:7-24]. So the DOCS-02 list is already complete; what is missing:
1. The one-page contract summary (replace the placeholder sentence, line 3).
2. A row for the §6.3 spec edit (D-01), and one for any §8.3/§12 edit.
3. Updated bullets: PS256 accepted live; the 403/404 codes observed; binary limits confirmed (2,900,000 and 3,100,000 byte attachments accepted: the latter is above 4 MB decimal after base64 and below 4 MiB, so the MiB reading is right; where Graph starts to answer 413 stays unknown); the 4 MiB request limit bullet can say live probes did not contradict it.
4. New "Differences from the Microsoft documentation" bullets from this phase's reading (all dated 2026-11-22): the RBAC page requires the Exchange Administrator Entra role in addition to Organization Management (spec §9 step 6 names only the latter); the RBAC test cmdlet excludes Entra grants, so it cannot confirm the union trap is cleared; the Exchange limits page words the 30/min message rate as SMTP client submission and does not say Graph `sendMail` is bound by it; the tenant external recipient limit and the `onmicrosoft.com` 100-recipient throttle exist and are not in the spec; the PrimarySmtpAddress advice for scope filters.
5. The 403-during-propagation behaviour next to the 403 row (Pitfall 6).
6. WR-01 fix recorded in the "failure message" row or the Contracts text (messages are cleaned: collapse, cut at a token boundary, replace addresses, cut at 300).

## State of the Art

| Old approach | Current approach | When changed | Impact |
|--------------|------------------|--------------|--------|
| Application Access Policies to scope an Entra `Mail.Send` grant | RBAC for Applications (management scope + role assignment) | Page: "it replaces Application Access Policies" | Guide recommends B; policies not mentioned except the union note |
| RS256 / SHA-1 `x5t` assertions | PS256 + `x5t#S256` | Current certificate-credentials page (2026-06-15) | Live-confirmed 2026-11-22 |

**Deprecated/outdated:** `gpg.passphrase` in settings (deprecated by the gpg plugin); the old OSSRH staging flow (the spec already targets the Central Portal plugin).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The app registration Overview page shows the Directory (tenant) ID next to the Application (client) ID, and a portal-created registration needs no redirect URI for client credentials | Microsoft setup facts | Guide wording slightly off; owner finds the IDs anyway |
| A2 | An Enterprise application (service principal) exists automatically for an app registered in the portal, so `New-ServicePrincipal` can point at it; if it does not exist the admin must create it in Entra first (the RBAC page says Exchange cannot create it) | Microsoft setup facts | B setup stalls at `New-ServicePrincipal`; owner needs a one-line fix |
| A3 | `New-ManagementScope -RecipientRestrictionFilter "Alias -eq '<alias>'"` matches the sending mailbox (property documented; the exact filter not run on a real mailbox, and the owner's tenant uses path A) | Microsoft setup facts | `Test-ServicePrincipalAuthorization` shows `InScope False`; the guide already tells the admin to check it and adjust the filter |
| A4 | A real mailbox-related 401 body contains `mailbox` in `error.code` (the rule in the code; never provoked live) | §6.3 write-back | Already marked unverified; no new risk |
| A5 | Shared-mailbox sending, `from` display-name rendering and RBAC out-of-scope refusal behave as the spec expects | Not-yet-verified section | These stay in the "Not yet verified live" section by D-06 |
| A6 | An OpenSSL-3-format `.p12` loads on every JDK 17 patch level (only JDK 21 tested locally; Temurin 17 CI loaded the legacy-format test file) | Code Examples | A `KeyStoreException`-style `IllegalArgumentException` at load; D-05's `-legacy` fallback covers it |
| A7 | No release-affecting Portal validation rule beyond the Sonatype requirements page is hit (the Portal's POM validation cannot be run locally) | Release rehearsal | Owner sees a validation error in the Portal at the real deploy; the deployment is not published until the owner acts, and fixes need a new version only after publishing |

## Open Questions

1. **Will the owner switch the tenant from path A to path B before the final rehearsal?**
   - What we know: the owner's tenant is on A; B is the recommended path and untested live.
   - What's unclear: whether DOCS-01's "follow the guide" check (success criterion 2) is done on A or B.
   - Recommendation: write the guide for B with A described and flagged as the live-verified path (D-04); the plan's last task is an owner checkpoint ("follow the guide, send the example campaign to test addresses, run again, expect nothing sent"), not an executor task.

2. **Whether to add `*.p12`/`*.key`/`*.pem` to `.gitignore` (Pitfall 8).**
   - Recommendation: yes, one line each, with the negation for the tracked test keystore; it is hygiene under REPO-11, not a feature. Planner's call.

3. **Whether the first real `deploy` should run tests.** The rehearsal used `-DskipTests` for speed; the earlier offline run executed all 312 tests green. Recommendation: the owner's `release.md` command keeps the tests on.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | build, tests | yes | Temurin 21.0.13 (`--release 17`) | JDK 17 only in CI (GitHub Actions run 45047907977 green on Temurin 17) |
| Maven wrapper | all | yes | Maven 3.10.1 | — |
| Network | rehearsal `publish` goal, WebFetch | yes (Central plugin ran online; gh works) | — | none for the rehearsal: offline skips the goal |
| gpg | rehearsal signing | yes | 2.4.8 | none; needs the short `GNUPGHOME` path |
| openssl | guide recipe | yes | 3.5.5 | `keytool -genkeypair` (legacy PKCS12) exists but the guide uses openssl by D-05 |
| unzip / zipinfo | bundle inspection | yes | — | `jar tf` |
| central-publishing-maven-plugin 0.12.0 | rehearsal | yes (local repo) | 0.12.0 | — |
| PowerShell + ExchangeOnlineManagement module, Entra admin portal, Central Portal | owner steps only | not applicable here | — | The Exchange cmdlets cannot be run by anyone but the owner; the guide stays "copied from Microsoft's page" |

**Missing dependencies with no fallback:** none for the executor. **Blocking owner-only items:** namespace verification, GPG key on a keyserver, Portal token, the real release.

## Validation Architecture

Skipped: `workflow.nyquist_validation` is `false` in `.planning/config.json` [VERIFIED: config.json]. For the plan's own checks: `./mvnw -B -ntp verify` (all tests incl. the two new WR-01 tests), `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` (no warnings), and the rehearsal's bundle listing and signature loop.

## Security Domain

`security_enforcement` is on (absent/true), ASVS level 1. This phase ships documentation plus a small error-text hygiene fix; no new input surface.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | docs only | The guide prescribes certificate credentials (secret only for a first test), password via environment variable, `-passout env:` |
| V3 Session Management | no | — |
| V4 Access Control | yes (docs) | Least privilege: RBAC scope per mailbox (B), remove the org-wide Entra consent (union trap), Entra check since the test cmdlet ignores it |
| V5 Input Validation | yes (small) | WR-01: server-supplied text is collapsed, cut at a token boundary, address-replaced, cut at 300 |
| V6 Cryptography | no change | RSASSA-PSS/PS256 is unchanged; the guide uses openssl and does not hand-roll anything |
| V8 Data Protection / logging | yes | No tokens/secrets in docs or output; only example values |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Private key or `.p12` committed to the public repo | Information disclosure | Guide: keep key files outside the repo; optional `.gitignore` lines (Pitfall 8) |
| Client secret in shell history or `account.properties` | Information disclosure | Env var only (`secretEnv`); the guide must not show a secret on a command line; the sample's own checks name keys, never values |
| Over-broad `Mail.Send` (any mailbox in the org) left in place after RBAC | Elevation of privilege | Union-trap step: revoke the Entra consent, verify in Admin consent tab |
| Mail address leaking through a cut error text | Information disclosure | WR-01 fix + tests |
| Throwaway GPG key or bundle left behind / committed | Information disclosure | Delete key, symlink, scratch copy, `~/.m2/repository/de/morisoft` after the rehearsal; commit nothing key-related (D-12) |
| Accidental real release | Tampering / repudiation | Rehearsal uses `skipUpload`; `autoPublish=false`; never run a real `deploy`, tag or publish (CLAUDE.md) |
| Secrets/tenant IDs/real addresses in docs | Information disclosure | Zero GUIDs, `example.com`/`example.org`; before committing, grep the new docs for GUID patterns other than all-zero and for real domains |

## Project Constraints (from CLAUDE.md)

From `./CLAUDE.md` and `./.claude/CLAUDE.md` (same authority as locked decisions):
- Rule 0: simplest code; two extension points only; no new dependency without asking; no feature/flag not in the spec; stop and ask if something elaborate seems needed (a separate testkit module is such a case, not needed).
- Public repo: no secret, tenant ID, real address, internal host or customer name in any file, fixture, commit message or issue text; `example.com`, `example.org`, zero GUIDs; exceptions only the test keystore and the maintainer identity.
- Commit as `Stephan Wald <stephan@morisoft.de>` (workspace `GIT_*` env vars override local git config; export the identity before every commit, per the user's memory note). Commit messages end with the `Co-Authored-By` trailer (owner confirmed keeping it).
- Never publish, deploy or tag; never send real mail; live tests are the owner's.
- Before coding against token/Graph endpoints read the current Microsoft page; follow the page and note differences in `docs/architecture.md` (done for this phase's reading, see the delta list).
- Public types get Javadoc stating thread safety; Javadoc must build cleanly (`-Prelease`, doclint all, failOnWarnings). Do not log tokens, secrets, assertions or full bodies; recipients at DEBUG only.
- `mvn verify` passes offline; finish and commit each unit of work with `mvn verify` green. GSD workflow enforcement: edits go through a GSD command (this phase's execution).
- Keep the MVP scope; the only library change in this phase is WR-01.

## Sources

### Primary (HIGH confidence)
- Local execution this session: release rehearsal (`-Prelease clean deploy -DskipUpload=true`, offline, online, SNAPSHOT and `0.1.0`), bundle listing and `gpg --verify` x5, `-Prelease clean verify -Dgpg.skip -pl morimail -am`, sample build and dry-run, openssl recipe plus `EntraClientCredentials.withCertificate` load, `javac` of the README snippet, `gh run list`.
- `central-publishing-maven-plugin-0.12.0.jar` `META-INF/maven/plugin.xml` (goals `help`/`publish`, phase `deploy`, parameters `skipUpload`, `excludeArtifacts`, `skipPublishing`, `autoPublish`, `waitUntil`).
- Repository files read: `pom.xml`, `morimail/pom.xml`, `morimail-sample-serial/pom.xml`, `docs/spec.md` §6.3, §8, §9, §10.2, §11-13, `docs/architecture.md`, `README.md`, `.github/workflows/ci.yml`, `.gitignore`, `03-UAT.md`, `03-REVIEW.md`, `03-REVIEW-DISPOSITION.md`, `COVERAGE.md`, `GraphTransport.java`/`EntraClientCredentials.java` `clean()`, `SerialMail.java`, `SendCommand.java`, `AccountFile.java`, the example campaign.
- https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac (page `updated_at` 2026-10-27)
- https://learn.microsoft.com/en-us/entra/identity-platform/certificate-credentials (2026-06-15), .../quickstart-register-app (2026-05-14), .../how-to-add-credentials (2026-11-17)
- https://learn.microsoft.com/en-us/entra/identity/enterprise-apps/manage-application-permissions (2026-02-19)
- https://learn.microsoft.com/en-us/office365/servicedescriptions/exchange-online-service-description/exchange-online-limits (2026-11-11)
- https://central.sonatype.org/publish/publish-portal-maven/, .../publish/requirements/gpg/, .../register/namespace/, .../publish/generate-portal-token/

### Secondary (MEDIUM confidence)
- https://learn.microsoft.com/en-us/powershell/exchange/connect-to-exchange-online-powershell (2026-06-18), .../new-managementscope (2026-05-16), .../recipientfilter-properties: used for syntax only; the filter itself is untested (A3).

### Tertiary (LOW confidence)
- None used for recommendations.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, nothing new; versions read from the POMs.
- Architecture / release rehearsal: HIGH, executed end to end with a signed bundle checked.
- Microsoft setup facts: HIGH for the quoted cmdlets and traps (current page); MEDIUM for the scope filter and the Entra-side service-principal creation (A2, A3), which only the owner can exercise.
- Pitfalls: HIGH for the five reproduced ones (1-5), MEDIUM for 6-8 (derived from live notes and file inspection).

**Research date:** 2026-11-22
**Valid until:** 2026-12-22 for the Microsoft pages (the RBAC page changes with the preview feedback address still on it, so re-read it when writing the guide); the rehearsal facts hold as long as the plugin stays at 0.12.0.
