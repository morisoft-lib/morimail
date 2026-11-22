# API Coverage — Microsoft Graph (Outlook mail send) and the Microsoft Entra token endpoint

> Full coverage by default. Opt-outs are explicit, reasoned decisions.
> Scope: the send-relevant surface of Graph mail and the app-only token endpoint, read on 2026-11-22 (see 03-RESEARCH.md, "Spec vs Docs").
> Plans: 03-01 (tracer), 03-02 (Entra), 03-03 (Graph request and limits), 03-04 (Graph error mapping and the 401 retry), 03-05 (sample, live test, docs).

| capability | decision | reason |
|---|---|---|
| graph: POST /users/{id}/sendMail with a JSON message | INTEGRATE | |
| graph: message.subject | INTEGRATE | |
| graph: message.body as HTML or Text (one itemBody) | INTEGRATE | |
| graph: toRecipients, ccRecipients, bccRecipients | INTEGRATE | |
| graph: replyTo | INTEGRATE | |
| graph: emailAddress.name (display names) | INTEGRATE | |
| graph: fileAttachment with base64 contentBytes (below 3 MiB) | INTEGRATE | |
| graph: saveToSentItems parameter | INTEGRATE | |
| graph: alternative base URL (option baseUrl) | INTEGRATE | |
| graph: error body (error.code, error.message, innerError request-id) | INTEGRATE | |
| graph: request-id response header | INTEGRATE | |
| graph: Retry-After on 429, 503 and 504 | INTEGRATE | |
| graph: sendMail with MIME content | OPT-OUT | not needed yet: HTML and text as alternative parts is a later milestone (LATER-05) |
| graph: inline attachments (isInline, contentId) | OPT-OUT | not needed yet: milestone 6 (INLN-03); build() rejects a contentId in the MVP |
| graph: upload session for attachments from 3 MB to 150 MB | OPT-OUT | not needed yet: later milestone (LATER-04); needs a draft and the Mail.ReadWrite permission |
| graph: create a draft message, then send it | OPT-OUT | not needed yet: only the upload-session route (LATER-04) needs a draft |
| graph: itemAttachment (attached Outlook items) | OPT-OUT | explicitly out of scope: the mail model has file attachments only |
| graph: referenceAttachment (links to cloud files) | OPT-OUT | explicitly out of scope: the mail model has file attachments only |
| graph: message.from and message.sender (send as, send on behalf) | OPT-OUT | not needed: the mailbox in the URL is the sender (spec 6.1); the display-name effect is a live-test item |
| graph: internetMessageHeaders (custom headers) | OPT-OUT | explicitly out of scope (spec 2: no custom internet headers, no List-Unsubscribe) |
| graph: importance, flag, categories | OPT-OUT | not needed: the mail model has no such fields |
| graph: read receipt and delivery receipt requests | OPT-OUT | not needed: the mail model has no such fields |
| graph: extended properties and mentions | OPT-OUT | not needed: the mail model has no such fields |
| graph: JSON batching ($batch) | OPT-OUT | explicitly out of scope: the library sends one mail at a time (spec 1) |
| graph: reading mail, folders, delta queries, search | OPT-OUT | explicitly out of scope: send-only library |
| graph: change notifications (webhook subscriptions) | OPT-OUT | explicitly out of scope: send-only library |
| graph: delegated /me/sendMail | OPT-OUT | explicitly out of scope: app-only in the MVP; delegated flows are LATER-03 |
| graph: client-request-id request header | OPT-OUT | not needed yet: the request-id of the response is enough to trace a call |
| graph: national cloud endpoints and their scopes | OPT-OUT | not needed yet: deferred in 03-CONTEXT.md (sovereign clouds); the scope stays graph.microsoft.com |
| graph: S/MIME | OPT-OUT | explicitly out of scope (spec 2) |
| graph: Microsoft Graph SDK | OPT-OUT | explicitly out of scope: no vendor SDK where a REST call does the job (spec 2) |
| entra: POST /{tenant}/oauth2/v2.0/token, client_credentials grant | INTEGRATE | |
| entra: client secret credential | INTEGRATE | |
| entra: certificate credential, client_assertion JWT (PS256, x5t#S256) | INTEGRATE | |
| entra: key and certificate from a PKCS12 file | INTEGRATE | |
| entra: the /.default scope | INTEGRATE | |
| entra: alternative authority host (authority(URI)) | INTEGRATE | |
| entra: token response access_token and expires_in | INTEGRATE | |
| entra: error response error and error_description | INTEGRATE | |
| entra: Retry-After on 429 and 5xx | INTEGRATE | |
| entra: RS256 assertion with a SHA-1 x5t header | OPT-OUT | explicitly out of scope: the current page requires PS256 with x5t#S256; no speculative fallback (ROADMAP Phase 3) |
| entra: x5c header (subject name and issuer authentication) | OPT-OUT | not needed: the certificate is registered on the app, so no chain is sent |
| entra: PEM key and certificate files | OPT-OUT | explicitly out of scope (spec 7.2) |
| entra: federated credentials (workload identity assertions) | OPT-OUT | not needed yet |
| entra: managed identity | OPT-OUT | explicitly out of scope (REQUIREMENTS.md, Out of Scope) |
| entra: authorization code, device code, on-behalf-of and refresh flows | OPT-OUT | explicitly out of scope: delegated flows are LATER-03 |
| entra: persistent token store | OPT-OUT | explicitly out of scope: one in-memory cache per instance (spec 7.3) |
| entra: error_codes, trace_id, correlation_id, timestamp fields | OPT-OUT | not needed: error and error_description already go into the message, and the description carries the trace and correlation ids |
| entra: OpenID metadata and instance discovery | OPT-OUT | not needed: the token URL is built from the authority and the tenant |
| entra: v1.0 token endpoint (resource parameter) | OPT-OUT | not needed: the v2.0 endpoint is the current one |
| entra: MSAL4J | OPT-OUT | explicitly out of scope: no vendor SDK and no JWT library (spec 7) |
