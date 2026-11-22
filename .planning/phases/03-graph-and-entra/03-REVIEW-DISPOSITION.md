---
phase: 03
review: 03-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "A malformed access token escapes `send` as an unchecked exception that contains the token"
  - id: WR-02
    severity: warning
    disposition: open
    title: "Server-echoed text goes into exception messages, journal and stdout, but the docs promise \"never addresses\""
  - id: WR-03
    severity: warning
    disposition: open
    title: "Plain `http` is accepted for the token authority and the Graph base URL, so the client secret and bearer token can travel in cleartext"
  - id: IN-01
    severity: info
    disposition: open
    title: "The private `Token` record's `toString()` prints the access token"
  - id: IN-02
    severity: info
    disposition: open
    title: "A 401 that is not about the token still discards a valid cached token"
  - id: IN-03
    severity: info
    disposition: open
    title: "The request-size estimate is looser than its Javadoc suggests, and the MB/MiB limits are unverified"
  - id: IN-04
    severity: info
    disposition: open
    title: "Under a token-endpoint outage, waiting threads are served one after another, each with its own timeout"
  - id: IN-05
    severity: info
    disposition: open
    title: "Rule 0: tolerance code for inputs the documentation says do not occur"
  - id: IN-06
    severity: info
    disposition: open
    title: "Test gaps and one weak assertion"
---

# Phase 03 code review disposition

All nine findings from 03-REVIEW.md are open. None is critical. Fixing is the owner's call (`/gsd-code-review 03 --fix`).
