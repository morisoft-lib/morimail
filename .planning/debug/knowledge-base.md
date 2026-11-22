# GSD Debug Knowledge Base

Resolved debug sessions. Used by `gsd-debugger` to surface known-pattern hypotheses at the start of new investigations.

---

## release-doc-gpg-signing — signed Maven deploy fails with gpg exit code 2 because -B forbids pinentry and the agent cache is cold
- **Date:** 2026-11-22
- **Error patterns:** maven-gpg-plugin, sign, sign-artifacts, Exit code: 2, gpg: signing failed: No pinentry, Inappropriate ioctl for device, GPG_TTY, pinentry, gpg-agent, passphrase, -B, batch mode, release, deploy, morimail-parent
- **Root cause(s):** Maven -B makes maven-gpg-plugin 3.2.8 run gpg with --batch --no-tty --pinentry-mode error (no passphrase source), so gpg-agent can only use a cached passphrase; passphrase-protected key; cold agent cache; docs/release.md had no warm-up, said "gpg asks for the passphrase" and offered GPG_TTY as the remedy
- **Fix:** docs/release.md step 5: in a real terminal, warm up the agent right before each deploy (`export GPG_TTY=$(tty)` then `echo test | gpg --clearsign > /dev/null`); GPG_TTY scoped to the warm-up; "signs from the agent cache and cannot prompt"; read the unprefixed `gpg:` line under `--- gpg:3.2.8:sign`, above the Reactor Summary; no POM change
- **Files changed:** docs/release.md
- **Why not caught:** the 04-04 release rehearsal ran the real release profile with a %no-protection throwaway key, so the passphrase path was never exercised; no automated gate exists for doc procedures
- **Recurrence guard:** docs/release.md step 5 warm-up and the "Exit code: 2" / "No pinentry" paragraph; this KB pattern: rehearse signing with a passphrase-protected throwaway key and a cold agent cache
---

