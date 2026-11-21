# Pitfalls Research

**Domain:** Java 17 mail library on Microsoft Graph `sendMail` (Entra app-only, hand-built PS256 client assertion), `$$name$$` templating, serial-mail CLI with journal/resume/rate limit, public repo published to Maven Central
**Researched:** 2026-11-21
**Overall confidence:** MEDIUM-HIGH. Microsoft doc facts were re-read on 2026-11-21 and are HIGH. Real Graph/Entra error behaviour comes mostly from forum reports and is MEDIUM or LOW. Those rows are tagged and need the owner's live test.

Phase key (the roadmapper may rename): **P1** scaffold + model, **P2** core + registry, **P3** sample CLI, **P4** Entra + Graph, **P5** docs + release.

## Verified facts the pitfalls rely on

| Fact | Source | Confidence |
|------|--------|------------|
| Certificate assertion: header `alg` PS256, `typ` JWT, `x5t#S256` = base64url SHA-256 of the cert DER. `aud` = `https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token`. `exp` should be 5-10 min after `nbf` at most. `nbf` "current time is appropriate". "Use PSS padding." | learn.microsoft.com/entra/identity-platform/certificate-credentials (updated 2026-06) | HIGH |
| A Microsoft Q&A answer claims Entra "requires RS256 and does not support PS256" for client assertions, and shows `x5t`. This contradicts the official page. | learn.microsoft.com/en-us/answers/questions/2280615 | LOW (conflicting, unresolved) |
| `sendMail` returns `202 Accepted`, which does not mean processing completed. Delivery is subject to Exchange Online limits. Most steps run after the response. Bad recipients produce NDRs in the sender's mailbox, not HTTP errors. | learn.microsoft.com/en-us/graph/api/user-sendmail and `/graph/outlook-things-to-know-about-send-mail` | HIGH |
| Direct attachment is for files "under 3 MB". Upload session is for 3-150 MB and needs `Mail.ReadWrite`. The page says nothing about a request-size cap. | `/graph/outlook-large-attachments` | HIGH |
| `sendMail` request limit is "generally around 4 MB", with a 413 beyond that. Unclear whether raw or base64. Other threads report >3 MB working. | Microsoft Q&A 1824204 (forum, staff-attributed) | LOW-MEDIUM |
| Outlook Graph limits per app+mailbox: 10,000 requests / 10 min, 4 concurrent requests, 150 MB upload (POST/PUT/PATCH) / 5 min. | `/graph/throttling-limits`, "Outlook service limits" | HIGH |
| Exchange Online per mailbox: 10,000 recipients/day (24 h sliding window), 30 messages/min, recipient limit up to 1000/message. Excess submissions are "throttled and successively carried over". "Exchange Online isn't suited to accommodate bulk-mailing scenarios." Separate tenant external recipient cap (TERRL, trial tenants 5,000/day). | exchange-online-limits (updated 2026-11-11) | HIGH |
| RBAC for Applications: grants are a union with Entra grants. `Test-ServicePrincipalAuthorization` excludes Entra grants and bypasses the cache. Changes take 30 min to 2 h to reach live calls. Use Enterprise-application IDs, not App-registration IDs. Exclusive scopes don't restrict apps. Needs Organization Management in EXO and Exchange Administrator in Entra. | learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac (updated 2026-10) | HIGH |
| `MailboxNotEnabledForRESTAPI` means the mailbox is inactive, soft-deleted, on-prem or unlicensed. Forum sources disagree on 403 vs 404. | Microsoft Q&A / 3CX / Nylas | LOW |
| Jackson 3.x is GA (Java 17 baseline, group `tools.jackson`, packages `tools.jackson.*`). Jackson 2.x (2.21 LTS) continues. | cowtowncoder.medium.com Jackson 3.0 GA post, FasterXML migration guide | MEDIUM-HIGH |
| Maven Wrapper's current default distribution type `only-script` needs no `maven-wrapper.jar`. `bin` and `script` types still do. | maven.apache.org wrapper plugin docs | MEDIUM-HIGH |

---

## Critical Pitfalls

### Pitfall 1: The "retry once after 401 with a fresh token" is a no-op, and the contract that would make it work is frozen too early

**What goes wrong:** The Graph transport sees a 401 and asks `TokenProvider.accessToken(scope)` again. `EntraClientCredentials` returns the same cached token, because it is still inside its 5-minute margin. The "single retry" repeats the identical failing call and the 401 is mapped to an error.

**Why it happens:** Spec section 7.3 says to add an invalidate method to `EntraClientCredentials`. Section 4 says `graph` must not import `entra`. `TokenProvider` is a one-method `@FunctionalInterface`. Graph therefore has no way to reach the invalidate method. The conflict only shows up in P4, after the sample (P3) has coded against the contract.

**How to avoid:**
- Decide the mechanism in P1/P2, when `TokenProvider` is written. The least invasive option is a `default` method on the interface, for example `default void invalidate(String rejectedToken) {}`. It keeps the interface functional, needs no new type, and is a no-op for lambdas and stubs.
- Make invalidation compare-and-clear: drop the cache only if the cached token equals the rejected one. Otherwise N threads that all get 401 each invalidate a token another thread just refreshed, and the token endpoint is hit N times.
- This widens an SPI, which the spec discourages in 7.3 ("if a simpler way exists"). Record the choice in `docs/architecture.md` and raise it with the owner at the first checkpoint.
- Test with a stub provider that returns token A, then B after `invalidate`. Assert the second request carries B. The spec's WireMock "single retry after 401" test does not catch this unless the stub provider changes tokens.

**Warning signs:** The Graph 401 test uses a lambda `TokenProvider` that always returns the same string. Two identical 401s appear in WireMock's request log. `graph` has an `import ...entra`.

**Phase to address:** P1/P2 (contract), verified in P4.

---

### Pitfall 2: PS256 assertion builds and verifies in unit tests, but Entra rejects it (AADSTS700027 / 700024)

**What goes wrong:** The JWT round-trips in JUnit and the real token endpoint answers `invalid_client`. The mistakes are usually one of these:
- Signing the raw JSON instead of the ASCII bytes of `base64url(header) + "." + base64url(payload)`.
- `Base64.getEncoder()`, MIME or padded output instead of `getUrlEncoder().withoutPadding()`.
- `x5t#S256` computed from the hex thumbprint string that the portal displays (that one is SHA-1), from the public key, or as hex or standard base64. It must be `base64url(SHA-256(cert.getEncoded()))` of the leaf cert that was uploaded.
- Wrong PSS parameters. Required: `Signature.getInstance("RSASSA-PSS")`, then `setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))` before `initSign`/`update`. Using `SHA256withRSA` gives RS256, and a default `PSSParameterSpec` gives SHA-1 PSS.
- Claims as non-numbers. `nbf`, `iat` and `exp` must be integer seconds. If `Instant` or `Date` goes through a Jackson module, or millis are used, the token is wrong.
- The `x5t` header (legacy, SHA-1) copied from an older doc or forum answer.
- A reused `jti`, or an assertion cached across requests. The spec says a new one per request.

**Why it happens:** The unit test signs and verifies with the same code and the same parameters, so it proves self-consistency only. The real validator is Microsoft's.

**How to avoid:**
- Verify in tests with an independent path. Decode the compact JWT by splitting on `.` and decoding with the plain JDK decoder, then verify with a freshly built `Signature` using explicit PSS parameters. Also assert the header is exactly `{alg, typ, x5t#S256}`. Assert `x5t#S256` equals `MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())` base64url-encoded, with no `=`, `+` or `/`. Assert the claim types are integral JSON numbers and `aud` equals `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token`.
- Build the header and claims with `ObjectNode`/`Map<String,Object>` and `long` values. Do not use records with `Instant`.
- **Treat PS256 vs RS256 as a live-test question.** The official page says PS256. One forum answer says Entra wants RS256. Follow the official page and do not code a speculative fallback. Make the first live-test failure message explicit (AADSTS code plus "certificate uploaded to this app registration?"). If AADSTS700027 appears, check upload, thumbprint and client ID before blaming the algorithm. Report the outcome at the P4 checkpoint.
- Key handling. A PS256 signature needs an RSA key of 2048 bits or more. For a non-RSA key from a PKCS12, fail fast in the factory with a clear `IllegalArgumentException`, not a `SignatureException` on first send.
- Clock skew. `nbf = iat = now` on a machine whose clock runs ahead gives AADSTS700024 ("not within its valid time range", reported on forums, MEDIUM). The spec says "current time", and the doc agrees. If the live test hits skew, subtract about 60 s from `nbf`/`iat` and note it in `architecture.md`. Do not add a `Clock` abstraction.
- `exp`: keep it at or under 5-10 minutes after `nbf`. The spec's 10 minutes is the edge of the docs' advice, so 5 minutes is safer.

**Warning signs:** Test file contains only a sign-then-verify round trip. The `aud` is hard-coded to the public cloud while the authority host is configurable. The `x5t` header key appears anywhere in the code.

**Phase to address:** P4 (build and test), live confirmation at the P4 checkpoint.

---

### Pitfall 3: The authority host is configurable but `aud` and the token URL drift apart, and `common` slips in as tenant

**What goes wrong:**
- The token URL is built from the test authority (WireMock) while `aud` is built from the default, or the reverse. Tests pass or fail for the wrong reason, and in production a custom authority (sovereign cloud) breaks.
- A `tenant` of `common`, `organizations` or `consumers` is accepted. Client-credentials needs a specific tenant (GUID or verified domain). Entra answers with an error that the consumer reads as a bug in the library.
- `scope` for a client-credentials call must be `https://graph.microsoft.com/.default`. `Mail.Send` or a bare resource gives AADSTS70011 (invalid scope).

**Why it happens:** Two string concatenations for the same URL.

**How to avoid:** Derive the token URL once, `authority + "/" + tenant + "/oauth2/v2.0/token"`, and use that same value for the POST and for `aud`. Reject blank and multi-tenant aliases in the factory. Make the Graph transport always pass the `.default` scope. If a caller passes a scope that does not end with `/.default`, fail with `IllegalArgumentException`, not an HTTP round trip.

**Warning signs:** A test asserting `aud` against a literal string with the production host while the client points at `localhost:<port>`.

**Phase to address:** P4.

---

### Pitfall 4: Token endpoint error mapping goes wrong on order, status and body shape

**What goes wrong:**
- Spec 7.3 says "map 4xx to `AuthRequiredException`; 5xx, 429 and network errors to Transient". If the generic 4xx rule is evaluated first, a 429 becomes `AuthRequired` and the CLI stops the run. 408 and other transient 4xx behave the same.
- The assumption "errors are HTTP 400 JSON". In practice some auth failures arrive as 401 (`invalid_client`, for example AADSTS7000215 invalid secret), and gateways or proxies can return HTML or an empty body. Unguarded `readTree` throws `JsonProcessingException` (a `RuntimeException`-like failure path in the library), leaking out of `send` as an unchecked or wrong-category error.
- `expires_in` handled as a string vs a number. The v2 endpoint returns a number. Use `JsonNode.asLong()`, which tolerates both.
- An absent or zero `expires_in` leads to a token that is always "expired", so every send re-requests a token. A negative `expires_in - 300` does the same silently.
- Messages. Entra's `error_description` is multi-line (`\r\n`), carries trace ID and timestamp, and for AADSTS700016 includes the tenant name. Put `error` + `error_codes`/AADSTS code + first line of the description in the exception message. Do not log the form body, the assertion or the secret.

Common codes to recognise in docs and tests (MEDIUM, observed on forums and in the Entra reference): AADSTS7000215 invalid client secret (often the portal "Secret ID" pasted instead of the secret "Value"), AADSTS7000222 expired secret, AADSTS700016 application not found in the tenant (wrong clientId or tenant), AADSTS90002 tenant not found, AADSTS700027 certificate not registered or signature failure, AADSTS700024 assertion outside valid time range, AADSTS70011 invalid scope.

**How to avoid:** Order the mapping explicitly: network/timeouts, then 429/5xx/408 as Transient (with `Retry-After`), then the remaining 4xx as `AuthRequired`. Parse the body defensively. A non-JSON body gives an `AuthRequired` or Transient message containing the status and the first 200 characters, with no secrets. Test a 429, a 401 with `invalid_client`, a 400 HTML body and a 500.

**Warning signs:** A single `if (status >= 400 && status < 500)` ahead of the 429 branch. No test with a non-JSON body.

**Phase to address:** P4.

---

### Pitfall 5: Token cache and single-flight bugs

**What goes wrong:**
- `synchronized` around the whole `accessToken` method holds the lock during the HTTP call. That satisfies "at most one request at a time", but without an HTTP timeout one hung connection blocks every sender forever.
- Margin arithmetic on wall-clock time breaks when the clock moves. Use `System.nanoTime()`/monotonic deadlines or compute `Instant` once per response.
- The test "a new request after expiry" needs time travel. Do not add a `Clock` to the public API for it. With a 5-minute margin, a WireMock response with `expires_in: 299` is always "expired" (every call re-requests), and `expires_in: 3600` is reused. That covers both branches with no clock.
- Failed token requests cached, or a failure leaving the "in flight" state stuck, so later callers wait forever or never retry.
- Invalidation races: see Pitfall 1.

**How to avoid:** A small lock-protected cache entry (token, monotonic deadline). Run the request while holding the lock but give the `HttpClient` both a connect timeout and a per-request timeout. Clear nothing on failure. A concurrency test with 8 threads and a WireMock delay asserts exactly one token request.

**Phase to address:** P4.

---

### Pitfall 6: `java.net.http.HttpClient` defaults bite (no timeout, client per call, interrupts)

**What goes wrong:**
- No request timeout by default. A stalled Graph or token call blocks the CLI or a consumer thread indefinitely. The retry and rate-limit logic never runs.
- Creating a new `HttpClient` per send. In Java 17 each client owns a selector thread and connection pool that is only reclaimed by GC (`close()` arrives in Java 21). A 5,000-row run leaks threads.
- Catching `InterruptedException` and mapping it to `TransientMailException` without restoring the interrupt flag. Ctrl-C then turns into "retry in 30 s".
- `HttpTimeoutException`, `ConnectException` and other `IOException`s all mapped alike (see Pitfall 8 on duplicates).
- Bodies: `BodyPublishers.ofString` uses UTF-8 by default, fine, but `Content-Type: application/json; charset=utf-8` should be explicit. Non-ASCII display names and subject text (umlauts) are common for this owner.

**How to avoid:** One shared `HttpClient` per transport or token provider instance, with `connectTimeout` (about 10 s) and a per-request `timeout` (30-60 s). Restore the interrupt flag and throw a `TransientMailException` or unchecked cancellation. Never set `followRedirects(ALWAYS)` on calls that carry `Authorization`.

**Phase to address:** P4 (and P3 for the CLI's interrupt handling).

---

### Pitfall 7: Timeouts and 5xx after the request left the building duplicate mail

**What goes wrong:** `sendMail` is not idempotent. A read timeout, a dropped connection after the body was sent, or a 504 may happen after Exchange accepted and queued the message. The spec maps these to Transient and the CLI retries up to 3 times, so the recipient can get 2-4 copies. A crash between the 202 and the journal append has the same effect on resume.

**Why it happens:** Transient is treated as "safe to retry". For 429 that is true (the request was rejected). It is not true for timeouts or 5xx.

**How to avoid:**
- Keep the spec's mapping, but make the cost visible: document at-least-once semantics in README and Javadoc of `MailTransport.send`.
- Use a generous request timeout (60 s or more) so timeouts are rare.
- Raise the choice with the owner at the checkpoint: after a timeout or 5xx on a send, the CLI could write an `unknown` journal outcome and not auto-retry that recipient. This avoids duplicates but loses mail. It needs a way to tell connect failures (safe) from request timeouts (unsafe). Java's `HttpConnectTimeoutException`/`ConnectException` vs `HttpTimeoutException` distinguish them.
- Consider a write-ahead journal line (`started`) before each send so a resume can surface "attempt in doubt" instead of resending silently. This is an extra record type, so it is an owner decision under Rule 0. At minimum, journal the 202 immediately and fail the run if the journal write fails.

**Warning signs:** The CLI retries on `HttpTimeoutException` with no note about duplicates. The README promises "a rerun never sends twice" without the caveat.

**Phase to address:** P3 (journal format, retry policy), P4 (exception classification), P5 (documentation).

---

### Pitfall 8: 401/403/404 meanings are ambiguous for app-only sendMail, and the mapping table encodes guesses

**What goes wrong:** The spec table says 401 splits on whether the body "points at the mailbox", 403 is `SenderRejectedException` and 404 is `SenderRejectedException`. In practice:
- 403 `ErrorAccessDenied` ("Access is denied. Check credentials and try again.") occurs both when the mailbox is outside the RBAC scope and when the app has no `Mail.Send` grant at all, or before RBAC propagation (30 min-2 h). The first is one mailbox, the other is the whole account. The body does not distinguish them (MEDIUM, forum reports).
- 404 can mean the mailbox does not exist, or `MailboxNotEnabledForRESTAPI` (inactive, soft-deleted, on-prem, unlicensed). Some sources report the latter as 403 (LOW).
- If `from` is a proxy address or alias that is not the UPN, `/users/{from}/sendMail` yields 404. The path accepts a UPN or object ID.
- A `from` in the body that differs from the mailbox in the URL needs Send As rights, otherwise an error appears.
- 401 means a bad token (expired, wrong audience or tenant). It does not mean "no permission". Treating 401-with-fresh-token as `AuthRequired` is right. "Body points at the mailbox" is a guess and will rarely be true.

**How to avoid:** Keep the table as a starting point, as the spec says, and always put the Graph `error.code`, `error.message`, HTTP status and `request-id` (from `innerError` or the `request-id` header) in the exception message. Never rely on parsing English message text. Define behaviour for an unrecognised body (still map by status). Write each row of the P4 table test with a fixture sanitised per Pitfall 22. Treat the P4 checkpoint as the point where real responses overwrite this table. Document in `entra-setup.md` that the from-address must be the mailbox's UPN or primary SMTP that resolves in `/users/{from}`.

**Warning signs:** String matching on `"mailbox"` in an error message. A test asserting a 401 maps to `SenderRejected` that was never seen in a real response.

**Phase to address:** P4, with the owner's live test as the gate.

---

### Pitfall 9: Attachment size limit is defined on raw bytes while the real limit is on the request

**What goes wrong:** Spec 6.2 rejects any file >= 3 MB and a total >= 3 MB, measured on raw bytes. Base64 inflates by 4/3 (JSON adds a little more). A 2.9 MB attachment becomes about 3.87 MB of `contentBytes`, plus HTML body and JSON overhead, which sits right at the "generally around 4 MB" request limit (LOW-MEDIUM). Microsoft's page documents "under 3 MB" only for the file size. The request cap is from forum answers, and other threads report >3 MB working. The true boundary is unknown until the live test.

**How to avoid:**
- Do not move the spec's rule on a forum claim. Instead: in `validate`, compute an estimated request size (`sum(ceil(n/3)*4) + html + text + a fixed overhead`) in addition to the raw 3 MB rules, and keep the constant in one place in the `graph` package.
- Ask the owner to include two live checks at the P4 checkpoint: one message with a 2.9 MB attachment, and one near 3.5 MB. Record where 413 or 400 appears. Then fix the rule in code and `docs/architecture.md`.
- Map 413 to `InvalidMailException` (it is the mail's fault).
- Re-check the size at send time with `Files.size` or the actual bytes read. A file can change between `validate` and `send`. Read the bytes once, measure the length read, and base64 that array.
- The 150 MB per 5 minutes upload limit per mailbox applies on top: at 20 mails/min, an average payload above about 1.5 MB (30 MB/min) will trigger 429s. Large attachments plus the default rate means throttling, so Retry-After handling is not optional.

**Phase to address:** P4 (validate), P5 (docs).

---

### Pitfall 10: Retry-After parsing and 429 behaviour

**What goes wrong:**
- `Retry-After` is documented as delta-seconds or an HTTP-date. `Long.parseLong` on a date throws `NumberFormatException` from inside the error handler.
- A missing header on 429 (some Graph resources omit it, per Microsoft's own notes) must fall back to the CLI's 30 s.
- A huge `Retry-After` (minutes or hours) makes the CLI sit idle without telling the user. A negative or zero value spins.
- Concurrency throttle: 4 concurrent requests per app+mailbox. The library promises thread safety, so a consumer with 10 threads sending from one mailbox gets 429s. Document it.
- Header lookup: `HttpHeaders.firstValue` is case-insensitive. Do not index the map by `"Retry-After"`.

**How to avoid:** `Optional<Duration>` parsed leniently (seconds, else HTTP-date via `DateTimeFormatter.RFC_1123_DATE_TIME`, else empty). Clamp to a sane maximum in the CLI, say 10 minutes. Anything longer is a failure to report, not a wait. Log the wait at INFO with the duration only.

**Phase to address:** P4 (parse), P3 (CLI wait policy; the CLI is coded first against a stub `TransientMailException(retryAfter)`).

---

### Pitfall 11: Exchange Online is not a bulk mailer, and its limits show up after the 202

**What goes wrong:** 202 means accepted. Per-mailbox limits (10,000 recipients per 24 h, 30 messages/min) and tenant-level external recipient limits are applied in the transport pipeline. Excess is "throttled and carried over", rejected, or turned into NDRs in the sender's mailbox. None of it reaches the CLI, so the journal says `accepted` for mail that never arrived. A 12,000-row campaign will fail silently in its last 2,000 rows (MEDIUM, from Microsoft's limits page; the exact surface of the failure is unverified).

**How to avoid:**
- The default of 20/min stays under 30/min. Keep it, and document that the 24 h cap is the real ceiling and that the number is per sending mailbox.
- Pre-flight in the CLI: warn (not necessarily fail) when the row count exceeds a documented figure such as 10,000. The warning belongs in the CLI/README, not in the core (no Graph assumptions in the core).
- Say plainly in README and `docs/architecture.md`: "accepted" is not "delivered". The sender mailbox collects NDRs. Suggest checking it after a run.
- Position it as a serial-mail tool for hundreds to low thousands, not a marketing platform. Microsoft states Exchange Online is not suited to bulk mailing. A "newsletter" sample implies bulk. Large public mailbox providers expect unsubscribe headers for bulk senders, and List-Unsubscribe is out of scope, so the README should warn.
- At the P4 checkpoint, replace the guessed 20/min with documented limits. The spec already requires this.

**Phase to address:** P3 (CLI warning), P5 (docs), P4 checkpoint (verify default).

---

### Pitfall 12: Exchange RBAC for Applications set-up errors (the admin guide is where most users fail)

**What goes wrong:**
- Using IDs from App registrations for `New-ServicePrincipal` (`-ObjectId` must be the Enterprise application's object ID). Microsoft's page calls this out.
- Leaving `Mail.Send` consented in Entra. The union makes the RBAC scope meaningless and the app can still send as anyone. The symptom is silent over-permission, not an error.
- Trusting `Test-ServicePrincipalAuthorization` as proof. It excludes Entra grants and bypasses the 30 min-2 h cache. "InScope: True" can coexist with a live 403, and after tightening a scope, sends to now-out-of-scope mailboxes may still succeed for up to 2 h.
- Reading the first 403 as a code bug. Spec section 9 already says it is expected, but the first live test will be run right after setup.
- Mistaking the older Application Access Policy (`New-ApplicationAccessPolicy`) for the current method. Many blog posts still show it. Microsoft says RBAC for Applications replaces it.
- Scope filter errors. A `-RecipientRestrictionFilter` that matches no mailbox or too many. Exclusive scopes do not restrict apps.
- Role requirements: Organization Management in EXO and Exchange Administrator in Entra.
- Certificate upload blocked by a tenant setting (reported on Q&A). It looks like AADSTS700027.
- Secret hygiene: client secrets expire (cert too). A failure then is `AuthRequired` for every mail. Say so in the guide.

**How to avoid:** Write `docs/entra-setup.md` from the live Microsoft page at writing time (the spec says so) and keep every GUID as `00000000-0000-0000-0000-000000000000`. Add a verification section with both a positive and a negative check: send from an in-scope mailbox and confirm an out-of-scope one gets a 403 after waiting at least 30 min, and confirm the Entra `Mail.Send` consent is gone (Enterprise application, Permissions). State that `Test-ServicePrincipalAuthorization` shows configured state only.

**Phase to address:** P5 (guide), owner's live test at P4/P5.

---

### Pitfall 13: Template renderer corrupts values through regex replacement syntax

**What goes wrong:** Using `Matcher.appendReplacement(sb, value)` or `replaceAll(MatchResult -> value)` makes `$` and `\` in the value special. A first name like `"$5 Fund"`, a Windows path `C:\temp`, or any price in a CSV value throws `IllegalArgumentException` ("Illegal group reference"), `IndexOutOfBoundsException`, or silently drops characters. Even the Java 9+ `replaceAll(Function)` still interprets the returned string as a replacement pattern.

**Other renderer traps:**
- Re-scanning inserted values. A value containing `$$other$$` must stay literal. Replace in one pass over the template, never repeated `String.replace` per field.
- Overlap and adjacency: `$$a$$$$b$$`, `$$a$$b$$`, `$$$name$$`, `$$$$`, `$$ $$`, `$$a b$$`, `$$!$$`, a lone trailing `$$`. Pin them with tests. The spec leaves "almost a placeholder" text untouched (section 5.3, section 10.1).
- Names that are only digits (`$$5$$`) match `[A-Za-z0-9_.-]+` and are treated as placeholders. Price text such as `$$20$$` in a template raises "missing field". Document it. The spec defines the rule, so test it and do not widen it.
- `$$!name$$` vs `$$name$$` precedence: the `!` is part of the delimiter, not the name.
- Escaping set for HTML values: `& < > " '`. Escaping does not make a value safe inside `href="$$url$$"` or `<style>` (a `javascript:` URL remains). State it in Javadoc and tell consumers to use `$$!name$$` only for trusted values.
- `String.valueOf(null)` renders the text `null`. A map entry with a null value (CSV never produces one) therefore prints "null". Decide and pin it in a test.
- Subject guard: replace `\r`, `\n` (and consider U+2028/U+0085) in the *value*. The template's own trailing newline needs handling too, see Pitfall 17.
- The missing-field exception must name template part and placeholder, and must not echo the data map.

**How to avoid:** Use a manual `find()` loop that appends the literal value to a `StringBuilder`, with `Matcher.quoteReplacement` where `appendReplacement` is used. Add the escape characters explicitly to the property tests (`$`, `$1`, `\`, `&`, `<`, a value with `$$x$$`).

**Warning signs:** `appendReplacement`/`replaceAll` with an unquoted value. A renderer test suite with no value containing `$` or `\`.

**Phase to address:** P2.

---

### Pitfall 14: Hand-written CSV parser gets RFC 4180 and real-world exports wrong

**What goes wrong:**
- Splitting each line on `,` breaks quoted fields (`"Müller, Anna"`), `""` escapes and quoted newlines. A `BufferedReader.readLine` loop cannot represent a quoted line break.
- UTF-8 BOM: the first header becomes `\uFEFFemail`, so "column `email` required" fails on a correct file.
- CRLF vs LF, and a trailing blank line, produce an empty last row. A row with one empty field then fails email validation.
- Ragged rows (fewer or more fields than the header). Report them with the physical line number. Record number and line number differ once newlines are embedded in quotes.
- **Delimiter.** German Excel exports "CSV" with `;` and often Windows-1252, and "CSV UTF-8" with BOM. The spec does not name a delimiter or encoding. With `;` the header is one column called `email;firstName` and the error looks like a bug. This owner is likely to hit it first.
- Headers: whitespace, case (`Email`), and names with spaces or other characters outside `[A-Za-z0-9_.-]` can never be referenced as `$$...$$`. Warn about them in pre-flight instead of ignoring them.
- Duplicate emails: with the journal key `campaignId:lower(email)` the second row for the same address is skipped as "already accepted", so a campaign with one address twice but different personalisation silently loses data. Pre-flight must flag duplicate addresses.
- Case folding: use `toLowerCase(Locale.ROOT)`. Under a Turkish default locale `"I".toLowerCase()` becomes dotless i and the key changes between machines. Trim the address before building the key.

**Dependency note:** the spec allows picocli only in the sample. A correct CSV reader is about 60-80 lines and fully testable, so the recommendation is to write it by hand with a test table covering the points above and no new dependency. If the owner wants more than that (other delimiters, encodings), that is a CSV library such as `jackson-dataformat-csv` and **requires asking the owner first** (Rule 0 and the spec's "ask before adding any dependency"). Raise it with the owner at the start of P3 and pick one answer: comma only with a clear error when the header contains `;`, or an explicit `--delimiter`. Do not add an option the spec does not name without asking.

**Phase to address:** P3.

---

### Pitfall 15: Journal is not crash-safe, or corrupts itself on resume

**What goes wrong:**
- `PrintWriter` swallows `IOException`. A full disk or a revoked file leaves the journal incomplete, the run carries on, and the next run sends everything again. Use `BufferedWriter`/`Files.write(..., APPEND)` and treat any journal write failure as fatal for the run.
- "Flush after every line" gets the bytes to the OS. That survives `kill -9` and Ctrl-C, which is the realistic failure. Power loss needs `FileChannel.force(true)`. State which guarantee is made. Per-line `force` costs milliseconds at 20 mails/min, so it is affordable and worth doing.
- A crash can leave a torn final line. On the next run the first appended record concatenates onto the partial text, corrupting two records. On open: read the whole file, ignore (and warn about) an unparsable last line, and if the file does not end in `\n`, write one before appending. An unparsable line in the middle should stop the run, since the journal is the idempotency record.
- Writing the line in several `write` calls (JSON, then newline) can interleave with another process. Build the full line plus `\n` as one byte array and write it once with `APPEND`.
- Two simultaneous runs against one journal both send. Take a `FileChannel.tryLock()` (or an exclusive lock file) at start and exit with a clear message. It is a few lines and protects the one promise the MVP makes.
- Only `accepted` outcomes should suppress a send. `failed` rows are retried on rerun by design. Spell out in README that "stop on auth error" leaves untouched rows unjournaled.
- JSON via Jackson (escapes newlines and quotes in server messages). Never hand-concatenate JSON. Messages from Entra contain `\r\n`.
- Timestamps as `Instant.toString()` (UTC). Local time with a DST change makes the log unreadable.
- The journal holds real recipient addresses. A public repo with the default `./campaign/journal.jsonl` and a stray `git add -A` publishes them. Add `journal.jsonl`, `*.jsonl`, `out/` and `account.properties` to `.gitignore`, and ship `account.properties.example`.

**Phase to address:** P3.

---

### Pitfall 16: Campaign identity, `--limit` and rate limiting subtleties

**What goes wrong:**
- Default `campaignId` = directory name. `./campaign`, `campaign/`, `.`, and a renamed directory give different IDs. Renaming the folder changes the key and resends the whole list. Normalise with `toAbsolutePath().normalize()` and `getFileName()`. If the name comes out as `.`, empty, or contains `:`, fail with a message. README: use an explicit `--campaign-id` for real runs.
- `--limit N` takes "the first N recipients", and rows already journaled are skipped, so a second `--limit 1` run sends nothing. The owner's verification step 9 (`--limit 1` to their own address) and then the real run share a journal. Document: use a separate `--campaign-id` or journal for smoke tests, or put the owner's address in row 1 deliberately.
- Rate limit: sleeping a fixed `60000/rate` ms after each send ignores send latency (fine), but skipped rows must not consume a slot, or a resume of a 5,000-row run waits hours doing nothing. No sleep before the first send or after the last. `--rate 0` or negative divides by zero or never sleeps. Validate it in pre-flight (exit 2).
- Retry backoff and the rate limit interact: a retry must not trigger two sleeps back to back. A permanent failure should not skip the spacing for the next row either.
- Thread interruption (Ctrl-C) during `Thread.sleep`: stop cleanly, journal is already up to date, exit non-zero.
- picocli exit codes. picocli itself returns 2 for usage/parameter errors and 1 for exceptions. The spec uses 2 for "pre-flight failed" and 3 for "stopped on sender/auth error". A bad flag and a bad campaign are then both exit 2. Either document that, or set the CLI's own mapping with `IExitCodeGenerator`/`execute` return values. Return codes from `call()` rather than calling `System.exit` inside commands, or the sample tests cannot assert exit codes (use `new CommandLine(cmd).execute(args)`).

**Phase to address:** P3.

---

### Pitfall 17: Encoding and file-reading mistakes (this owner will meet umlauts first)

**What goes wrong:**
- `Properties.load(InputStream)` decodes ISO-8859-1. A UTF-8 `account.properties` or from-name with `ü` is corrupted. Use `load(Reader)` with an explicit UTF-8 reader.
- Java 17's default charset is the platform charset (UTF-8 default only from Java 18). `FileReader`, `new String(bytes)`, `getBytes()`, `PrintWriter(File)`, `System.out` are all platform-dependent. Dry-run `.html` and `.subject.txt` files written with the default charset come out wrong on Windows. Always pass `StandardCharsets.UTF_8`. `Files.readString`/`writeString` default to UTF-8 but `readString` throws `MalformedInputException` on a Windows-1252 template. Catch it and say which file is not UTF-8.
- `subject.txt` ends with a newline. `Files.readString` returns it, the template's own newline then appears in the subject (the guard in 5.3 only covers values). Use the first line, stripped. Strip a BOM from every template and CSV file as well.
- `body.html` needs a `<meta charset="utf-8">`? The Graph JSON body has no MIME charset concern, but the dry-run `.html` file opened in a browser needs one to display umlauts correctly. Write the dry-run file with the meta tag only if the template has none, or note it. Minor.
- Dry-run file names `out/<n>-<email>.html`: an address is attacker-influenced data from a CSV. Sanitise to `[A-Za-z0-9@._+-]` (and avoid Windows reserved characters). Never resolve `out.resolve(email + ".html")` raw. A value containing `../` would write outside `out/`.
- Attachment filenames: send `Path.getFileName()` only, never the full path. Generated attachments must not include the local username or directory in metadata (Pitfall 22).
- Attachment `contentType` null: the spec's `Attachment` has it. Default to `application/octet-stream` in the transport. Do not guess from the extension in the core.

**Phase to address:** P2 (renderer, builder), P3 (CLI file handling).

---

### Pitfall 18: Registry and `ServiceLoader` mistakes that make tests poison each other

**What goes wrong:**
- `RecordingTransport` registers via `META-INF/services` in the library's test resources, so it is on the test classpath of every library test. The spec test "an external transport that reuses a built-in ID fails" needs a second services entry that makes registry loading throw. If that is on the global test classpath, every other test that builds a registry throws.
- Test provider files ship inside the test-jar. Any module that depends on the test-jar (the sample) also gets the services file. That is intended for `recording`, but any fault provider added later leaks into the sample.
- `ServiceLoader.load(Class)` uses the thread context class loader. In app servers and some test runners that is not the loader that holds the library jars. A `ServiceConfigurationError` from a broken third-party provider escapes as an `Error`, not a `MailException`.
- Shaded or fat-jar consumers lose `META-INF/services` entries unless the shade plugin merges them. Mention it in the README.
- "Built-in explicit list" must not itself go through `ServiceLoader`, or the "built-in resolves with no ServiceLoader entry" test cannot prove anything.

**How to avoid:** Give the registry a package-private constructor that takes the external providers (`Iterable<TransportProvider>`). The public path calls `ServiceLoader` and passes the result. Tests of the duplicate-ID rule pass a list directly (or use a `URLClassLoader` over a temp dir), so no global state is touched. Catch `ServiceConfigurationError` and rethrow with the provider class named. Include the available IDs in the "unknown ID" message (spec) and a test for it.

**Phase to address:** P2.

---

### Pitfall 19: Jackson version choice, and Jackson/WireMock classpath collisions

**What goes wrong:**
- "Use the latest stable version" lands on Jackson 3.x: different group (`tools.jackson.core`), packages (`tools.jackson.databind`), unchecked exceptions and different builders. Jackson 2.x (2.21 LTS) is still maintained. A consumer on Spring Boot 3 / Jackson 2 gets an extra Jackson on the classpath if the library picks 3.x. The two do not clash by name, but it doubles the footprint of "Jackson only". Consumers on Jackson 2 with an older minimum would otherwise override the library's version. Pick deliberately, record it in `architecture.md`, and ask the owner, because it is hard to change after 0.1.0 when it leaks into dependency trees. My leaning: Jackson 2.x for 0.1.0 (widest compatibility, checked exceptions are explicit), unless the owner wants Jackson 3.
- WireMock 3.x (non-standalone) brings its own Jackson and Jetty, and Maven's "nearest wins" can downgrade the library's Jackson in the test classpath, giving `NoSuchMethodError` in tests only. Import a `jackson-bom` in `dependencyManagement` or use `wiremock-standalone`.
- Records and Jackson: building Graph JSON by serialising the model records pulls in `@odata.type` naming and `null` field issues. Build the request with `ObjectNode`/`Map`, not with annotated DTOs.
- JSON `null` fields: Graph rejects some `null` properties (`replyTo: null`) and `ccRecipients: []` is fine. Omit absent fields.
- `saveToSentItems`: send the JSON boolean `false`, only when false (the docs' sample shows a string `"false"`, a boolean also works).
- Both `html` and `text` present: the log-once INFO must really be once (a static `AtomicBoolean` or per-transport flag) and must never contain the subject or addresses.

**Phase to address:** P1 (parent POM, BOM, version decision), P4 (JSON building).

---

### Pitfall 20: Javadoc "builds in the release profile only", so failures surface at release time

**What goes wrong:** `maven-javadoc-plugin` is bound inside the `release` profile (spec 13.3), and CI runs plain `mvn verify` (13.1). Javadoc errors (and the "without warnings" acceptance check in milestone 5) therefore first appear when the owner tries to release. Typical JDK 17 doclint problems:
- Missing `@param` for **record components** on every public record (`MailAddress`, `Attachment`, `MailMessage`, `Mail`). They are warnings, which the spec's "without warnings" forbids.
- Raw `<`, `>`, `&` in comments (`List<String>` must be `{@code List<String>}`), self-closing `<p/>`, `<br/>`, empty `<p>`, malformed HTML, `@throws` for a checked exception that is not declared, a broken `{@link}`.
- `{@link}` to a class that only exists in test sources (`RecordingTransport`) or in another module.
- Missing first sentence, or "no comment" warnings on public members of public types, including the `Mailer.Builder`-type nested classes.
- A Javadoc plugin version that predates JDK 17 behaves differently. The `<release>17</release>` setting must be passed to Javadoc as well.
- Javadoc on the `graph`/`entra` implementation classes: decide which are public API. The core imports the built-in provider class, so at least that one must be public; keep the rest package-private to shrink Javadoc and the API surface.

**How to avoid:** Run the Javadoc goal from the first Javadoc commit. Add a CI step `mvn -B -Prelease verify -Dgpg.skip` (or bind `javadoc:javadoc` with `failOnWarnings=true` and `doclint` all into normal `verify` for the library module). Write Javadoc with `@param` on records from P1 (the thread-safety statement is already required there). Treat `-Dgpg.skip` as the way CI exercises the profile without keys.

**Warning signs:** The first `mvn -Prelease verify` happens in P5. Records with a one-line Javadoc and no `@param`.

**Phase to address:** P1 (introduce Javadoc discipline and CI check), P5 (final gate).

---

### Pitfall 21: Maven Central packaging traps (test-jar, sample module, SNAPSHOTs, wrapper, irrevocable coordinates)

**What goes wrong:**
- **Test-jar gets published.** The sample needs `RecordingTransport` from the library's test sources, so `maven-jar-plugin:test-jar` attaches `morimail-<v>-tests.jar`. The Central publishing plugin bundles every attached artifact of each reactor module. Result: a signed (or, worse, unsigned and rejected) `-tests.jar` on Central. Spec 13.2 says "the test-jar is not published" while 10.1 says it "is published as a test-jar" (probably "made available to the reactor"). Note the contradiction in `architecture.md`. The `excludeArtifacts` option exists on the plugin, but a project reported it filters on artifact ID and not classifier (LOW, uncertain). The alternatives are: do not attach the test-jar in the release path (a profile-activated `jar:test-jar` only when not releasing, which also breaks sample resolution under `-Prelease verify`, so test it), or move `RecordingTransport` into a small unpublished module. That is a spec change, so ask the owner.
- **Sample module deployed.** `maven.deploy.skip` on the sample may not stop the Central plugin from bundling it. Unverified. The mojo has its own skip parameter. Do not rely on it; check the deployment contents in the Portal.
- **Safe first release procedure.** Configure the plugin with `autoPublish=false` for 0.1.0, so the owner inspects the staged deployment (artifact list, `-tests.jar`, sample, signatures) in the Portal before pressing Publish. Central releases cannot be replaced.
- **Plugin wiring.** The Central publishing plugin must be declared in `<build><plugins>` of the profile with `<extensions>true</extensions>`. `publishingServerId` must match the server `id` in the owner's `settings.xml`. In `pluginManagement` alone it does nothing.
- **SNAPSHOT.** `0.1.0-SNAPSHOT` is not accepted by the Portal's release flow. The owner needs `mvn versions:set -DnewVersion=0.1.0` (or the release plugin), and the `<scm><tag>` and project URL need to be right. Doing that is the owner's job, but the profile must not break on it.
- **Parent POM** (`packaging pom`) needs signing and metadata but not sources/Javadoc. The sample module's parent reference needs `relativePath`. The library's `<parent>` version must be resolvable after release (`${revision}` flatten issues: do not use `${revision}` without `flatten-maven-plugin`, or published POMs have an unresolved property).
- **Metadata:** name, description, URL, license, developer, SCM on the parent. Central rejects missing fields. `<developers>` has the real name and email by decision.
- **Irrevocable coordinates:** `de.morisoft:morimail-parent` and `:morimail` are final after the first release. The namespace `de.morisoft` must be verified by DNS TXT on `morisoft.de` in the Central Portal first. Do that early (owner task) because DNS propagation and review can take a day. Do not leave it for release day.
- **Maven Wrapper vs `.gitignore`.** The current `.gitignore` has `*.jar`. The `only-script` wrapper type (current default, MEDIUM-HIGH) does not need `maven-wrapper.jar` at all. If the wrapper is generated with `bin` or `script`, the jar is silently ignored and the committed wrapper fails with "Could not find or load main class". Generate the wrapper, then run `git status --ignored` and `git ls-files .mvn`. If the jar is needed add `!.mvn/wrapper/maven-wrapper.jar`. Also commit `mvnw` with the executable bit (`git update-index --chmod=+x mvnw`) and pin `distributionSha256Sum`.
- **`.gitignore` gaps:** it ignores `*.zip` and `*.log`, but the test keystore `.p12` is not ignored (fine), and nothing ignores `target/`, `.idea/`, `out/`, `journal.jsonl`, `*.pfx`, `account.properties`. Add them in P1.

**Phase to address:** P1 (`.gitignore`, wrapper, parent POM), P5 (release profile and inspection).

---

### Pitfall 22: Public-repo hygiene leaks (real responses, generated files, CI, history)

**What goes wrong:**
- **Live-test output pasted back into the repo.** The checkpoint asks the owner to feed real Graph/Entra responses into spec 6.3 and tests. Real bodies and headers contain the tenant ID, client ID, object IDs, `request-id`/`client-request-id` GUIDs, the mailbox address, tenant names (`AADSTS700016` text includes them) and timestamps. Replace every one with zero GUIDs and `example.com` before it enters fixtures, docs, the spec or commit messages. Git history is permanent.
- **WireMock recording** against the real endpoint saves `Authorization` headers and bodies into mappings. Hand-write every stub. Never use record mode.
- **Generated attachments.** The PDF and PNG in `example-campaign/` carry metadata: PDF `Author`/`Creator`/`Producer`/dates, XMP, PNG `tEXt`/`tIME`/`eXIf`, and sometimes the local username or hostname. Generate them with a script that sets neutral metadata, check them with `strings`/`exiftool`, and commit the generator or a note, not just the blobs.
- **Test keystore.** Name it so its purpose is obvious (for example `test-only-throwaway-DO-NOT-USE.p12`, password `test`). Generate it fresh with JDK 17 `keytool` (RSA 2048+, long validity, SAN/CN `example.org`). The README gets the one-sentence explanation the spec asks for. Secret scanners (GitHub secret scanning, gitleaks) may flag it. Expect that and mention it.
- **Doc examples and logs.** `entra-setup.md` commands use zero GUIDs. Never include a real tenant domain (`*.onmicrosoft.com`) or the owner's mailbox. Never include an internal path such as `/home/<user>/...` from tool output in `.planning/` files (the repo's `.planning/` is public by decision).
- **Logging.** Never log the token, secret, assertion or request body. Exception messages from Graph can include the address. Recipients go to DEBUG only. `HttpRequest.toString()` is safe, `HttpHeaders.toString()` is not (contains Authorization). Do not log request objects wholesale.
- **CI.** Set `permissions: contents: read` on the workflow. Do not use `pull_request_target`. Pin third-party actions (`actions/checkout`, `actions/setup-java` with Temurin 17) to a version or SHA. No secrets. The live test must never be reachable from CI (its profile is not activated, and it needs env vars).
- **Git identity.** Local config is already `Stephan Wald <stephan@morisoft.de>` (verified 2026-11-21). The spec wants a check after each milestone: `git log --format='%an <%ae> | %cn <%ce>'`. Harness-added `Co-Authored-By` trailers are an owner decision (spec 12). Do not amend history later. If a wrong identity is committed, it is permanent once pushed.
- **No-network build.** `mvn verify` offline: WireMock on a dynamic port, no test resolves `login.microsoftonline.com` or `graph.microsoft.com` (a default authority left in a test would silently go to the network and pass on a developer machine). Prove it in CI with a second run using `mvn -o verify` after the dependency cache is warm.

**Phase to address:** P1 (`.gitignore`, CI, keystore naming), P3 (example campaign generation), P4 (fixtures), P5 (docs and README).

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Same `TokenProvider` token returned after 401 (no invalidate) | Keeps the interface at one method | The spec'd retry does nothing | Never |
| Splitting CSV lines on `,` | 5 lines of code | Breaks quoted names, BOM, CRLF; silently mis-sends | Never |
| `appendReplacement` with raw values | Short renderer | `$`/`\` in data corrupts mail or throws | Never |
| `PrintWriter` for the journal | Convenient API | Swallowed I/O errors, duplicates on rerun | Never |
| Javadoc only in `release` profile | Faster default build | Release-day failures | Only if CI also runs `-Prelease -Dgpg.skip` |
| Hard-coded 20 mails/min | Spec default | Wrong for large attachments (150 MB/5 min) and other mailbox limits | MVP only, verify at the P4 checkpoint |
| Raw-byte 3 MB check only | Matches spec text | Request near 4 MB with base64, possible 413 | MVP, if the live test confirms the boundary |
| Sample hand-rolled arg parsing instead of picocli | None | Spec says picocli | Never |
| Real response bodies pasted as fixtures | Fast, realistic | Leaks IDs into permanent history | Never without sanitising |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Entra token endpoint | Not URL-encoding form values; hand-concatenated body | `URLEncoder.encode(v, UTF_8)` for every value, `Content-Type: application/x-www-form-urlencoded` |
| Entra token endpoint | Cert thumbprint taken from the portal (SHA-1 hex) | `x5t#S256` computed from `cert.getEncoded()` with SHA-256 |
| Entra token endpoint | Using `common`/`organizations` as tenant | Specific tenant ID or verified domain only |
| Graph `/users/{from}/sendMail` | Using a proxy alias that isn't the UPN, or relying on `URLEncoder` for the path (space becomes `+`) | Mailbox UPN/primary address; encode as a path segment (the `URI` multi-argument constructor or manual percent-encoding) |
| Graph attachments | Posting `contentBytes` from a file read twice (size changed) | Read once, size-check the same bytes, base64 that array |
| Graph 202 | Treating accepted as delivered | Say "accepted"; NDRs land in the sender's mailbox |
| Exchange RBAC | Leaving the Entra `Mail.Send` consent in place | Remove it, then verify an out-of-scope mailbox gets 403 after the 30 min-2 h cache window |
| Exchange RBAC | Using App-registration IDs | Enterprise application object ID and app ID |
| WireMock | Default (shared) port; real hostnames in stubs | Dynamic port, authority configured to `http://localhost:<port>` |
| JUnit 5 + Surefire | `live` test runs by default or fails without env | `@Tag("live")` excluded by default, `-Plive` includes it, `Assumptions` skip when env missing |
| `ServiceLoader` | Test providers on the global test classpath | Inject provider list in tests (package-private constructor) |
| picocli | Exit codes colliding with picocli's 1/2 | Map explicitly, return from `call()`, document |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| New `HttpClient` per request | Thread count grows, slow sends | One client per transport/token provider | Around hundreds of sends in one JVM |
| Rate spacing counting skipped rows | Resume of a large campaign sleeps for hours | Sleep only after an actual send attempt | Resume of >100 skipped rows |
| 150 MB / 5 min upload limit per mailbox | 429s with large attachments at the default rate | Respect `Retry-After`; document average payload vs rate (1.5 MB at 20/min) | About 1.5 MB average payload at 20/min |
| 10,000 recipients/24 h per mailbox | Late rows "accepted" but not delivered | Pre-flight warning, document per-mailbox cap | Over 10,000 recipients in 24 h |
| Journal re-read per row | Quadratic work | Load the journal into a `Set<String>` once at start | About 10,000+ rows |
| Token request per send (margin bug, `expires_in` parse) | Many token calls, throttling on login endpoint | One request until the margin; cover with a test | Immediately, 1 per mail |
| 4 concurrent requests per mailbox limit | 429 `MailboxConcurrency` for threaded consumers | Document; consumer limits concurrency | More than 4 threads per mailbox |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Logging `HttpHeaders`/`HttpRequest` or token responses | Token leakage in logs | Log status and request-id only; no header dumps; unit test that logging captures no token |
| Exception messages that include the form body or `client_assertion` | Secret in stack traces and journal | Build messages from `error`, AADSTS code, first description line |
| Secrets in `account.properties` committed | Credential leak in public history | Secrets only via `*Env` variables (spec); ignore `account.properties`; ship `.example` |
| `$$!name$$` raw insertion of untrusted CSV data | HTML/script injection into mail | Default escaping; Javadoc warning; sample uses escaped form only |
| Header/subject injection through CR/LF | Broken or spoofed headers (relevant when SMTP arrives) | Subject guard on values and on the template's trailing newline |
| Tenant-wide `Mail.Send` consent left on | App can send as any user | Guide recommends RBAC and requires removing the Entra consent; verify with an out-of-scope mailbox |
| Dry-run file name from the CSV address | Path traversal outside `out/` | Sanitise, resolve against `out/` and check `startsWith(out)` |
| Loading a PKCS12 with an empty/wrong password silently | Confusing failure later | Fail in the factory with the keystore path (never the password) |
| Test keystore confusion | Reported as a leaked secret | Obvious file name, README sentence |
| CI with write permissions or `pull_request_target` | Supply-chain compromise of a public repo | `permissions: contents: read`, plain `pull_request` |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Pre-flight stops at the first problem | Fix-run-fix loops over a 5,000-row CSV | Collect all problems, print once, exit 2 (spec 8.3) |
| "sent" wording for `accepted` | False confidence | Counts labelled accepted / skipped / failed |
| Error says only "403" | Admin cannot tell scope from missing permission | Include Graph `error.code`, message and `request-id`; point to the guide section |
| Missing-placeholder error without row/part | Hard to find the problem | Name part (subject/html/text) and placeholder (spec), plus row in the CLI |
| `;`-delimited CSV gives "column email missing" | Looks like a bug | Detect and say "looks semicolon-delimited" |
| Long silent wait on `Retry-After` | CLI looks hung | Print one line with the wait and reason, cap the wait |
| Rename of campaign folder resends everything | Duplicate newsletters | Explicit `--campaign-id`, print the effective ID at start |
| First 403 right after setup | Perceived library bug | Setup guide states the 30 min-2 h delay, CLI hint on 403 |

## "Looks Done But Isn't" Checklist

- [ ] **PS256 assertion:** often only self-verified. Check header keys are exactly `alg`, `typ`, `x5t#S256`, base64url without padding, integer-second claims, PSS params set, and one live success.
- [ ] **401 retry:** often retried with the same token. Check that a stub provider returns a different token after invalidation and that the second request carries it.
- [ ] **Error table:** each row has a test and a real response behind it after the checkpoint. 429 is tested before the generic 4xx rule on the token endpoint.
- [ ] **Single-flight:** an 8-thread test proves one token request, with HTTP timeouts configured.
- [ ] **Renderer:** values with `$`, `\`, `$$x$$`, `<`, `&`, `"` and newline-in-subject tests exist.
- [ ] **CSV:** BOM, CRLF, quoted comma, quoted newline, `""`, trailing blank line, ragged row, duplicate address, `;` header tests exist.
- [ ] **Journal:** torn last line, missing trailing newline, failing write (read-only file), second concurrent run, case-folded key (`Locale.ROOT`) tests exist.
- [ ] **Attachments:** a 2.9 MB attachment was actually sent in the live test; the 413/400 boundary is recorded.
- [ ] **Registry:** duplicate-ID and unknown-ID tests do not rely on the global classpath.
- [ ] **Offline build:** `mvn -o verify` passes in CI after a warm cache. No test touches a real host.
- [ ] **Javadoc:** `mvn -Prelease verify -Dgpg.skip` passes with no warnings, and runs in CI, not just at release.
- [ ] **Central:** staged deployment (autoPublish off) contains only `morimail-parent` and `morimail`: no `-tests.jar`, no sample.
- [ ] **Wrapper:** a fresh clone runs `./mvnw -v`; executable bit and `.gitignore` exceptions checked via `git ls-files .mvn`.
- [ ] **Hygiene:** `git grep -nEi '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'` shows only zero GUIDs (and Microsoft's documented example IDs if quoted). Same for `onmicrosoft`, real domains and `/home/`. Generated PDF/PNG metadata is neutral. `git log --format='%an <%ae> | %cn <%ce>'` is clean.
- [ ] **Docs:** README says accepted is not delivered, at-least-once, the per-mailbox limits, and the bulk-mail caveat.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Secret or real ID committed to public repo | HIGH | Rotate the credential immediately (cert/secret, and treat IDs as exposed). History rewrite plus force-push only before others fork, and GitHub caches remain. Prefer rotation |
| Wrong git identity committed | MEDIUM | Before push: rewrite with `git rebase`/`filter-repo`. After push: accept, fix going forward |
| Bad coordinates or metadata released to Central | HIGH (irrevocable) | Cannot replace the version. Publish a fixed next version, document the broken one |
| `-tests.jar` or sample published to Central | HIGH | Cannot delete. Prevent with `autoPublish=false` and Portal inspection; otherwise next version and a README note |
| Duplicate mail sent after crash/timeout | MEDIUM | Inspect the journal and the sender's Sent Items, suppress affected rows by adding journal lines by hand, send an apology if needed |
| Journal corrupted by a torn line | LOW | Truncate the partial last line (journal is append-only JSON lines), rerun |
| RBAC over-permission (Entra consent not removed) | MEDIUM | Remove consent, wait 30 min-2 h, re-test with an out-of-scope mailbox, review sent logs |
| Wrong error mapping released | LOW | Pre-1.0 changes are allowed; fix and note in release notes |
| Javadoc failure on release day | LOW-MEDIUM | Fix and retry before publishing; nothing is on Central yet |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. 401 retry no-op / `TokenProvider` contract | P1/P2 contract, P4 | Stub provider changes token after invalidate; second request carries new token |
| 2. PS256 assertion rejected | P4 (+ live) | Independent decode/verify test, one live token success |
| 3. `aud`/token URL drift, tenant alias, scope | P4 | Test with custom authority; factory rejects `common` |
| 4. Token endpoint error mapping | P4 | Tests for 429, 401 invalid_client, HTML body, 500 |
| 5. Token cache / single-flight | P4 | 8-thread test, `expires_in` 299 vs 3600 |
| 6. HttpClient defaults | P4 (P3 interrupts) | Timeouts set; one client instance; interrupt flag test |
| 7. Duplicate sends on timeout/crash | P3, P4, P5 | Owner decision recorded; README caveat; journal-failure-is-fatal test |
| 8. 401/403/404 ambiguity | P4 + live | Messages carry code and request-id; live results replace table |
| 9. Attachment/base64 size boundary | P4 + live | Estimated-size validation; 2.9 MB live send |
| 10. Retry-After parsing | P3/P4 | Seconds, HTTP-date, missing, huge values tested |
| 11. Exchange limits / bulk | P3, P5, P4 checkpoint | Pre-flight warning; documented limits replace the guessed default |
| 12. RBAC set-up errors | P5 (+ live) | Guide includes negative scope test and Entra consent removal |
| 13. Renderer regex replacement | P2 | Tests with `$`, `\`, nested placeholder text |
| 14. CSV parser | P3 | Table-driven parser tests; owner decision on delimiter/dependency |
| 15. Journal crash safety | P3 | Torn-line, lock, write-failure tests |
| 16. Campaign ID / limit / rate / exit codes | P3 | Tests for path variants, `--rate 0`, exit code table |
| 17. Encoding and file reading | P2/P3 | UTF-8 round trip with umlauts, BOM, trailing newline in subject |
| 18. Registry / ServiceLoader | P2 | Duplicate-ID test with injected providers |
| 19. Jackson version, WireMock collisions, JSON shape | P1, P4 | Version decision in `architecture.md`; BOM import; request JSON asserted |
| 20. Javadoc only at release | P1, P5 | CI runs the release profile with `-Dgpg.skip` |
| 21. Central packaging, wrapper, `.gitignore` | P1, P5 | Staged deployment inspection; fresh-clone `./mvnw -v` |
| 22. Public-repo hygiene | P1, P3, P4, P5 | Grep checklist, metadata check on generated files, CI permissions |

## Items needing an owner decision (collect for the first checkpoint)

1. `TokenProvider` invalidation mechanism (default method vs another route) and whether to deviate from "do not widen the interface" (Pitfall 1).
2. CSV: comma only with a helpful error, an explicit delimiter option, or a CSV dependency (Pitfall 14). Any dependency needs the owner's approval.
3. Duplicate-send policy on timeouts/5xx and whether to add a write-ahead journal record (Pitfall 7).
4. Jackson 2.x vs 3.x for the library jar (Pitfall 19).
5. How to keep the test-jar and sample out of the Central bundle, given the spec's two statements (Pitfall 21).
6. Live-test additions: 2.9 MB and about 3.5 MB attachments; a negative RBAC scope check; PS256 confirmation; sanitised response capture (Pitfalls 2, 8, 9, 12, 22).

## Sources

- Microsoft Learn, "Microsoft identity platform certificate credentials", https://learn.microsoft.com/en-us/entra/identity-platform/certificate-credentials (read 2026-11-21; HIGH)
- Microsoft Learn, "user: sendMail", https://learn.microsoft.com/en-us/graph/api/user-sendmail (HIGH)
- Microsoft Learn, "Overview of the Microsoft Graph send mail process", https://learn.microsoft.com/en-us/graph/outlook-things-to-know-about-send-mail (HIGH)
- Microsoft Learn, "Attach large files to Outlook messages or events", https://learn.microsoft.com/en-us/graph/outlook-large-attachments (HIGH)
- Microsoft Learn, "Microsoft Graph service-specific throttling limits" (Outlook service limits), https://learn.microsoft.com/en-us/graph/throttling-limits (HIGH)
- Microsoft Learn, "Exchange Online limits", https://learn.microsoft.com/en-us/office365/servicedescriptions/exchange-online-service-description/exchange-online-limits (HIGH)
- Microsoft Learn, "Role Based Access Control for Applications in Exchange Online", https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac (HIGH)
- Microsoft Q&A, "Graph API - user: sendMail - Request size limit", https://learn.microsoft.com/en-in/answers/questions/1824204/graph-api-user-sendmail-request-size-limit (LOW-MEDIUM, forum)
- Microsoft Q&A, "How to resolve error AADSTS700027 the certificate", https://learn.microsoft.com/en-us/answers/questions/2280615/how-to-resolve-error-aadsts700027-the-certificate (LOW, contradicts the official page on PS256)
- Microsoft Q&A, "Client assertion is not within its valid time range", https://learn.microsoft.com/en-us/answers/questions/778031/client-assertion-is-not-within-its-valid-time-rang (MEDIUM)
- Microsoft Q&A and community threads on `MailboxNotEnabledForRESTAPI` and app-only 403, https://learn.microsoft.com/en-us/answers/questions/5842949/mailboxnotenabledforrestapi-error-in-multi-tenant and https://techcommunity.microsoft.com/discussions/officegraph/403-forbidden-when-sending-mail-with-app-only-token-via-microsoft-graph/4458775 (LOW)
- Sonatype, "Publishing By Using the Maven Plugin", https://central.sonatype.org/publish/publish-portal-maven (HIGH for plugin existence; `excludeArtifacts` classifier behaviour unverified)
- Jackson 3.0 GA announcement, https://cowtowncoder.medium.com/jackson-3-0-0-ga-released-1f669cda529a and migration guide, https://raw.githubusercontent.com/FasterXML/jackson/master/jackson3/MIGRATING_TO_JACKSON_3.md (MEDIUM-HIGH)
- Apache Maven Wrapper plugin docs, https://maven.apache.org/plugins/maven-wrapper-plugin/wrapper-mojo.html (MEDIUM-HIGH)
- Project files read: `spec.md`, `.planning/PROJECT.md`, `.gitignore`
- Standing knowledge (JDK 17 behaviour of `Matcher`, `Properties.load`, default charset, `HttpClient`, `RSASSA-PSS`, picocli exit codes): not re-verified this session, MEDIUM-HIGH

---
*Pitfalls research for: Java Microsoft Graph mail library with serial-mail CLI, Maven Central*
*Researched: 2026-11-21*
