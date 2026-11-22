# Phase 4: Docs and Release Readiness - Context

**Gathered:** 2026-11-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Apply the owner's live-test findings (spec §6.3, architecture.md, one small code fix), then write `docs/entra-setup.md`, finish `docs/architecture.md` (one-page contract summary plus all deviations), finish `README.md` (what it is / is not, quick start), and run a release rehearsal with the owner's release steps in `docs/release.md`. Requirements DOCS-01..04 plus roadmap success criterion 1 (checkpoint feedback loop). No new library features. Never publish, deploy or tag; never send real mail.

**Live-test findings already established (2026-11-22, `.planning/phases/03-graph-and-entra/03-UAT.md`):**
- Entra accepted the PS256 assertion with `x5t#S256` (certificate flow); the secret flow works too.
- 403 `ErrorAccessDenied` → `SenderRejectedException` confirmed. 404 `ErrorInvalidUser` (sender without an Exchange mailbox) → `SenderRejectedException` confirmed. This is a 404, not the 401 from the forum answer.
- 2,900,000 and 3,100,000 byte attachments accepted, so the limits are binary (MiB) and the constants are right.
- `--rate 20` default confirmed by the owner. LICENSE (Stephan Wald, MIT), SECURITY.md contact and the `Co-Authored-By` trailer confirmed.
- Not verified live: mailbox 401 after a fresh token, `MailboxNotEnabledForRESTAPI`, out-of-scope RBAC refusal, shared mailbox as sender, `from` display name rendering.
- Exchange permission propagation was observed: intermittent 403s for minutes after the consent.
- The owner's tenant was granted through Entra admin consent (path A), not RBAC (path B).

</domain>

<decisions>
## Implementation Decisions

### Live-findings write-back
- **D-01:** Update `docs/spec.md` §6.3 (the spec itself says "correct it in code and here"). Keep the table rows and add a live-status column: 403 and 404 (`ErrorInvalidUser`) are confirmed on 2026-11-22; the mailbox 401, `MailboxNotEnabledForRESTAPI` and the out-of-scope refusal are marked unverified. Replace the forum-answer sentence ("A forum answer reports 401 ...") with the 404 finding. Record the spec edit in `docs/architecture.md`. No change to the error mapping in code, because the live results match it. Every error-table row stays tested.
- **D-02:** Fix review warning WR-01 in this phase. `clean()` in `GraphTransport.java` and `EntraClientCredentials.java` must replace addresses before cutting (or cut at a token boundary), so an address straddling the cut cannot leak its start. Add one test per class. Mark WR-01 fixed in `03-REVIEW-DISPOSITION.md` or record it in the Phase 4 summary.
- **D-03:** The sample keeps Graph's default for `saveToSentItems`: it sends none, so campaign mails land in Sent Items as an audit trail. No code change. Say so in `entra-setup.md` (account.properties step) and where the sample is described.

### Setup guide (`docs/entra-setup.md`)
- **D-04:** Follow the spec §9 order and recommend B (Exchange RBAC for Applications), with cmdlets copied from the current Microsoft page at writing time. State plainly that A (Entra admin consent) is the path verified live on 2026-11-22 and B is not yet verified live. The union-trap step warns that A's consent must be removed when B is used.
- **D-05:** The certificate step uses openssl: `req -x509` for the key and `.cer` upload, then `pkcs12 -export` for the `.p12`. Mention the `-legacy` flag only as a fallback if an old JDK 17 patch cannot read the file. Mention the client secret as the quick alternative for a first test.
- **D-06:** End the guide with a short "Not yet verified live" section: shared mailbox as sender, `from` display name, out-of-scope RBAC refusal, B itself, `MailboxNotEnabledForRESTAPI` and the mailbox 401. No inline caveats scattered through the steps.
- **D-07:** Write the guide in English. Use only example values (`example.com`, zero GUIDs). Include the propagation note: a 403 right after setup is expected and was seen live. Verification is the sample's dry run, then `--limit 1` to the owner's own address, then the example campaign to test addresses, then a second run that sends nothing.

### README quick start
- **D-08:** The quick start has one Java block: `EntraClientCredentials`, then a `MailAccount` with transport `graph`, then `Mail.using(...).template(...).attach(...).build()`, then `Mailer.of(account).send(mail)`, with one catch per error category (transient / permanent incl. sender-rejected / auth-required) and what to do for each. After it, 2-3 lines on running `serial-mail send --dry-run` on the example campaign, and a link to `docs/entra-setup.md`. The README also covers the DOCS-03 items: what morimail is not (Exchange limits of 30/min and 10k recipients/day per mailbox, not a bulk mailer), pre-1.0 status, coordinates, at-least-once and accepted-not-delivered, and the keystore sentence (already present, keep it).
- **D-09:** The snippet is checked once by hand against the real API, with no compile-checked mirror test (Rule 0). The planner must verify every method name and signature in the snippet against the source.

### Release steps and rehearsal
- **D-10:** The owner's release steps go into a new `docs/release.md`, a short checklist: verify the Central namespace, create the GPG key with the maintainer identity and publish it to a keyserver, put the Portal token in `settings.xml` (server id `central`, placeholders only), set the release version, deploy with `-Prelease -pl morimail -am`, review in the Portal (autoPublish false), publish, tag, and bump to the next SNAPSHOT. No secrets. The owner runs every step; Claude never runs them.
- **D-11:** Rehearsal: `-Prelease deploy -DskipUpload=true -pl morimail -am`, then inspect the bundle. It must contain only `morimail-parent` and `morimail` (pom, jar, sources, javadoc, checksums, `.asc`), with no `-tests.jar` and no sample. If the `-tests.jar` shows up, first try the documented fix (`excludeArtifacts` or the attach setting). If neither is clean, stop and ask the owner before any structural change, such as a separate testkit module.
- **D-12:** Sign the rehearsal with a throwaway GPG key in a temporary `GNUPGHOME` in the scratchpad, outside the repository, and check that every bundle file has an `.asc`. Delete the key afterwards and commit nothing key-related. `mvn -Prelease verify -Dgpg.skip -pl morimail -am` must also stay green without Javadoc warnings.

### Claude's Discretion
- How the architecture.md contract summary is laid out, as long as it stays one page and keeps the existing deviations table.
- Exact wording and section order inside the README and `entra-setup.md`, within the decisions above.
- Whether WR-01 is fixed by reordering (replace, then cut) or by a token-boundary cut, whichever is simpler.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Binding spec
- `docs/spec.md` §6.3: the error table to update (D-01)
- `docs/spec.md` §9: the setup-guide steps (DOCS-01)
- `docs/spec.md` §13 (13.1-13.5): README contents, coordinates, what is not published, release profile, identity, who does what
- `docs/spec.md` §10.2: milestone 5 definition
- `docs/spec.md` §11 / `CLAUDE.md`: Rule 0, the no-secrets rule, never publish/tag

### Requirements and roadmap
- `.planning/REQUIREMENTS.md`: DOCS-01..04
- `.planning/ROADMAP.md` Phase 4: success criteria 1-5, the research flag on `excludeArtifacts`

### Live-test findings and open items
- `.planning/phases/03-graph-and-entra/03-UAT.md`: all live results and owner decisions (the source for D-01, D-04, D-06)
- `.planning/phases/03-graph-and-entra/03-REVIEW.md` and `03-REVIEW-DISPOSITION.md`: WR-01 (D-02)
- `.planning/phases/03-graph-and-entra/03-CONTEXT.md`: Phase 3 decisions (D-01..D-16) referenced from architecture.md
- `.planning/phases/01-library-core/01-CONTEXT.md`: D-18 and D-20 (offline gate, release build scoped with `-pl morimail -am`)

### Existing docs to extend
- `docs/architecture.md`: deviations table and Microsoft-doc differences (keep them, add the summary and the §6.3 edit)
- `README.md`: the current stub
- `SECURITY.md`, `LICENSE`: confirmed as is

### External (read at writing time)
- Microsoft "Role Based Access Control for Applications in Exchange Online" (https://learn.microsoft.com/Exchange/permissions-exo/application-rbac): copy the cmdlets from here
- Microsoft "Certificate credentials" and "Client credentials flow" pages (see the spec bibliography)
- Sonatype "Publishing By Using the Maven Plugin" (https://central.sonatype.org/publish/publish-portal-maven/) and "Requirements": for `docs/release.md` and `excludeArtifacts`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `morimail-sample-serial/example-campaign/account.properties`: example account (zero GUIDs, `example.com`), the key names to document in entra-setup.md (`tenantId`, `clientId`, `from`, `certificate.path`, `certificate.passwordEnv`, `secretEnv`)
- `LiveGraphTest` (`morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java`): its `MORIMAIL_LIVE_*` variables can be mentioned for the owner
- `clean()` at `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:309` and `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:375`: the WR-01 fix sites (the code is written twice on purpose, since graph and entra must not import each other)

### Established Patterns
- The release profile lives in the parent `pom.xml` (sources, javadoc, gpg, central-publishing). The test-jar has `attach false` in `morimail/pom.xml:79`. Release builds use `-pl morimail -am` (D-20).
- Deviations go in the architecture.md table as Spec | morimail | Reason rows.
- Docs use plain English, short sentences and only example values.

### Integration Points
- CI (`.github/workflows/`) already runs `verify` and `-Prelease verify -Dgpg.skip -pl morimail -am`. Docs changes must keep both green.

</code_context>

<specifics>
## Specific Ideas

- The guide should present the live-observed propagation (intermittent 403 for minutes after consent) as normal, not as an error.
- The 404 `ErrorInvalidUser` finding replaces the forum-answer 401 claim everywhere it appears.

</specifics>

<deferred>
## Deferred Ideas

- Live verification of the shared mailbox, display name, out-of-scope RBAC, the mailbox 401 and `MailboxNotEnabledForRESTAPI`: the owner, after 0.1.0 if wanted.
- A sample option for `saveToSentItems=false`: only if a campaign user asks for it.
- From Phase 3: settable timeouts, an `authority`/`graphBaseUrl` key (sovereign clouds), the >3 MB probe and upload sessions.
- Open review items not in scope: the journal has no exclusive lock (IN-06); `attachments/` still picks up `Thumbs.db`/`desktop.ini`.

</deferred>

---

*Phase: 04-docs-and-release-readiness*
*Context gathered: 2026-11-22*
