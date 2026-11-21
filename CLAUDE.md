# CLAUDE.md

These are the working rules for Claude Code in this repository. They are copied verbatim from section 11 of [docs/spec.md](docs/spec.md); where they disagree with the spec, the spec wins. GSD-managed project context lives in `.claude/CLAUDE.md`.

### Rule 0: do not overthink, do not overengineer

This rule ranks above the others. When in doubt, build less.

- Write the simplest code that meets this spec and passes its tests. A reader should be able to follow a send from `Mail.using(...)` to the HTTP call in a few minutes.
- The library has two extension points: `TransportProvider` and `TokenProvider`. Add no further interface, SPI, factory, strategy or abstract base class unless two real implementations exist in this repository.
- Prepare for later milestones only where this spec says so (the registry requirements in section 2, the `contentId` field). Write no code, option or hook for anything else that might come later.
- No feature, flag or option this spec does not ask for.
- No frameworks, dependency injection, annotation processing or reflection. Plain constructors, records and static factories.
- No dependency in the library jar beyond Jackson. No vendor SDK for something that is a REST call. A transport that needs more is an external jar. Ask before adding any dependency.
- Prefer one clear class over several small ones that only forward calls. Prefer a 20-line method over a pattern.
- The library sends one mail. Batching, campaigns, scheduling, retries, queues and configuration files belong to the consumer.
- If a requirement seems to need something elaborate, stop and ask. The spec is more likely unclear than the problem is hard.

### Further rules

- The repository is public from the first commit, and git history is permanent. Never write a secret, tenant ID, real mail address, internal host name or customer name into any file, test fixture, commit message or issue text. Examples use `example.com`, `example.org` and zero GUIDs. Two exceptions: the test keystore from section 10.1, and the maintainer identity from section 13.4 where that section and the POM, `LICENSE` and `SECURITY.md` call for it.
- Commit as `Stephan Wald <stephan@morisoft.de>` (section 13.4).
- Never publish, deploy or tag a release. Releasing to Maven Central is done by the owner.
- Never send real mail. Live tests are run by the owner only.
- Before coding against the token endpoint or a Graph endpoint, read the current Microsoft documentation page for it. Where it differs from this spec, follow the documentation and note the difference in `docs/architecture.md`.
- Public types get Javadoc that states thread safety. `Mailer`, transports and token providers must be safe for concurrent use.
- Do not log tokens, secrets, assertions or full mail bodies. Recipient addresses may be logged at DEBUG only.
- Keep the MVP scope. If something from "out of scope" seems necessary, stop and ask.
