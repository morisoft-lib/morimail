---
status: resolved
trigger: "release-doc-gpg-signing: docs/release.md lets the owner run the release, including the signed deploy, without hitting an undocumented failure; the Portal validates the deployment and the bundle matches the 30-entry listing."
created: 2026-11-22T20:21:00Z
updated: 2026-11-22T21:26:00Z
goal: find_and_fix
---

## Current Focus

bug_class: Bohrbug (deterministic: same command, same environment, same failure on the first sign call)
hypothesis: CONFIRMED - H2. Maven `-B` (in both documented deploy commands) makes maven-gpg-plugin 3.2.8 run gpg with `--batch --no-tty --pinentry-mode error`, so gpg-agent can only hand over a cached passphrase. The owner's key is passphrase-protected and the cache was cold, so the first sign (morimail-parent POM) fails with gpg exit 2. docs/release.md never told the owner to prime the agent, said "gpg asks for the passphrase" (impossible under -B), and presented GPG_TTY as the remedy (it only matters for the warm-up).
resume_finding: the fix is ALREADY in HEAD. Gap closure 04-05 committed it in c359ec1 (20:45Z), and review fixes 49cfe7b, 7b97943, f9096a9 refined it. UAT test 5 (owner, real terminal, real deploy and Portal) passed with the fixed doc, and 0.1.0 was released (81ff531). No further doc edit is needed (Rule 0: do not write it twice).
test: done (each fix item checked against docs/release.md at HEAD; revert-and-reconfirm of the doc procedure with a throwaway protected key; `./mvnw -o -B -ntp verify` green)
expecting: n/a
next_action: none; session resolved and archived to .planning/debug/resolved/

reasoning_checkpoint:
  hypothesis: "docs/release.md step 5 fails with gpg exit 2 because its `-B` deploy commands put maven-gpg-plugin into non-interactive mode (--pinentry-mode error), so a passphrase-protected key can only be used from a primed gpg-agent cache, and the doc has no step that primes it; it instead promises a passphrase prompt and offers GPG_TTY, which has no effect under -B."
  confirming_evidence:
    - "GpgSigner bytecode: !isInteractive -> --batch --no-tty, and passphrase==null && gpg>=2.1 -> --pinentry-mode error; isInteractive = settings.isInteractiveMode() (false under -B); non-zero exit -> 'Exit code: N'"
    - "Real release profile from HEAD with a throwaway protected ed25519 key, cold cache: the user's exact [ERROR] line on morimail-parent, with 'gpg: signing failed: No pinentry' above the Reactor Summary; after one priming clearsign: BUILD SUCCESS, 5 .asc"
    - "Cold cache with -B args inside a real pty with GPG_TTY=/dev/pts/3 still fails 'No pinentry' (GPG_TTY alone does not fix it)"
    - "Owner's key is protection=P; after the failure someone primed the agent (cached=1) and the doc's skipUpload command then produced a 30-entry bundle at 20:20:00Z with a Good signature from <KEYID>"
    - "The rehearsal key was %no-protection; the same -B args with an unprotected key exit 0 cold (why the rehearsal did not catch it)"
  falsification_test: "If a cold-cache, protected-key run of the documented -B command signed successfully when GPG_TTY was set in a real terminal, or if priming did not make it pass, H2 would be wrong. Both were tested: case B failed, cases D/F2 and the owner's 20:19:59 run passed."
  fix_rationale: "Adding a warm-up in a real terminal (export GPG_TTY=$(tty); echo test | gpg --clearsign > /dev/null) right before each deploy puts the passphrase into the agent cache, which is the only source the plugin can use under -B. Correcting 'gpg asks for the passphrase' and adding a one-line hint on the raw gpg: line removes the undocumented failure. No POM change is needed."
  blind_spots: "The owner's own raw gpg: line from the failed run was not captured (prediction: 'gpg: signing failed: No pinentry'). The owner's settings.xml was not read (by constraint); the throwaway repro used an empty settings file. Whether the Central Portal accepts EdDSA (ed25519) signatures is not confirmed by docs found; that is a later, recoverable stage (autoPublish false). macOS/GUI pinentry behaviour was not tested (irrelevant under -B, since --pinentry-mode error blocks every pinentry)."
  candidate_causes:
    - "config/docs: -B in the documented deploy commands plus no agent warm-up step plus the wrong 'gpg asks for the passphrase' line (CONFIRMED)"
    - "environment: passphrase-protected key, cold agent cache, curses-only pinentry, GPG_TTY unset / not a tty (contributing; cold cache necessary, GPG_TTY not necessary under -B)"
    - "code (POM): maven-gpg-plugin has no configuration, so defaults apply (useAgent true, no keyname, bestPractices false); defaults are correct and need no change"
    - "data: wrong or missing secret key / default-key choice (ELIMINATED: exactly one sign-capable secret key, and it signed later with the same default)"
  and_gate: "yes - the failure needs all of: (1) Maven non-interactive (-B as documented) -> --pinentry-mode error, (2) a passphrase-protected key, (3) a cold agent cache, (4) no passphrase from MAVEN_GPG_PASSPHRASE or gpg.passphrase. Removing any one makes it pass (E removes 2, D/F2 and the owner's rerun remove 3). The doc controls (1) and (3); (2) is correct practice; (4) is deliberately avoided by the doc."

## Symptoms

expected: The owner follows docs/release.md step by step; the step 5 deploy (`./mvnw -B -ntp -Prelease clean deploy -DskipUpload=true -pl morimail -am`, then the real deploy) signs the artifacts, the bundle has 30 entries, and the Portal validates it.
actual: User reported "that's missing in the document". Step 5 failed with maven-gpg-plugin:3.2.8:sign (sign-artifacts) on project morimail-parent: Exit code: 2. Owner key exists (ed25519 <KEYID>), ~/.m2/settings.xml exists, GPG_TTY was unset. Likely pinentry could not prompt (no TTY, e.g. Claude Code `!` prefix). Release commit 72e7f7f pushed, CI green, nothing uploaded.
errors: maven-gpg-plugin:3.2.8:sign (sign-artifacts) on project morimail-parent: Exit code: 2
reproduction: Test 3 in UAT (.planning/phases/04-docs-and-release-readiness/04-UAT.md)
started: Discovered during UAT on 2026-11-22

## Eliminated

- hypothesis: H1 - GPG_TTY unset is the cause; `export GPG_TTY=$(tty)` in a real terminal makes the Maven deploy prompt and sign
  evidence: repro case B - in a real pty with GPG_TTY=/dev/pts/3 and a cold cache, the plugin's -B args still fail with "No pinentry" (exit 2), because the plugin passes --pinentry-mode error. GPG_TTY is needed only for the piped warm-up (W1 fails without it, W2 prompts with it) or for a run without -B (case C).
  timestamp: 2026-11-22T20:36:00Z

- hypothesis: wrong, missing or ambiguous secret key (default-key selection)
  evidence: exactly one secret primary key (<KEYID>, caps scESC); the plugin logs "signing ... with key default"; the same default key signed successfully at 20:19:59 once the cache was primed
  timestamp: 2026-11-22T20:31:00Z

- hypothesis: bestPractices or a passphrase in settings.xml causes the failure
  evidence: bestPractices defaults to false and only warns; a passphrase from settings/properties would switch gpg to --pinentry-mode loopback (no pinentry needed) and print a W A R N I N G banner; the user's exact failure reproduces with an empty settings file and no passphrase source
  timestamp: 2026-11-22T20:40:00Z

- hypothesis: something specific to the morimail-parent module
  evidence: morimail-parent is just the first module signed (its POM is the first file); morimail shows SKIPPED in the reactor summary
  timestamp: 2026-11-22T20:40:00Z

## Evidence

- timestamp: 2026-11-22T20:22:00Z
  checked: knowledge base (Phase 0)
  found: .planning/debug/ did not exist; no knowledge-base.md; no MemPalace tool available in this session
  implication: no known-pattern candidate; investigate from scratch

- timestamp: 2026-11-22T20:23:00Z
  checked: docs/release.md
  found: Step 2.3 (line 33) says "The passphrase goes through gpg-agent. Run `export GPG_TTY=$(tty)` in the shell that runs the deploy." Step 5 commands both use `./mvnw -B -ntp -Prelease clean deploy ...` (lines 71, 94). Line 97 says "Tests run, and gpg asks for the passphrase." No agent warm-up, no keyname/default-key note, no troubleshooting for gpg failures, no mention that the real gpg message is printed above Maven's [ERROR].
  implication: the doc assumes an interactive pinentry prompt during the Maven run

- timestamp: 2026-11-22T20:24:00Z
  checked: pom.xml release profile and pluginManagement
  found: maven-gpg-plugin 3.2.8, single execution sign-artifacts in verify, NO configuration block (no keyname, signer, useAgent, bestPractices, gpgArguments, homedir). Sample sets gpg.skip true. All defaults apply.
  implication: signer=gpg (external gpg), useAgent=true, keyname unset (gpg default key), interactive derived from Maven settings

- timestamp: 2026-11-22T20:25:00Z
  checked: 04-04-PLAN.md task 1 step c, 04-RESEARCH.md rehearsal recipe
  found: the rehearsal key was generated with `%no-protection` (no passphrase) in a throwaway GNUPGHOME. Research step 5 says "signing prompts via gpg-agent" and step 2 cites GPG_TTY from STACK notes; neither was executed with a protected key.
  implication: the rehearsal could never exercise the passphrase path, so the doc's passphrase/GPG_TTY lines were carried over untested

- timestamp: 2026-11-22T20:26:00Z
  checked: spec section 13.3-13.5
  found: spec requires a GPG signature per file, says owner creates the key and runs the release; it says nothing about how the passphrase reaches gpg
  implication: spec is silent; the gap is purely in docs/release.md (and the research that fed it)

- timestamp: 2026-11-22T20:27:00Z
  checked: workspace gpg environment (read-only: ls ~/.gnupg, which, update-alternatives, tty, env)
  found: ~/.gnupg exists with private-keys-v1.d and running agent sockets; no gpg.conf and no gpg-agent.conf; gpg 2.4.8; /usr/bin/pinentry -> pinentry-curses (only pinentry installed); DISPLAY and WAYLAND_DISPLAY unset; `tty` in a Claude Code shell prints "not a tty"; GPG_TTY unset
  implication: only a terminal pinentry exists; in a Claude Code shell `export GPG_TTY=$(tty)` would set GPG_TTY="not a tty"; no default-key configured in gpg.conf

- timestamp: 2026-11-22T20:28:00Z
  checked: maven-gpg-plugin-3.2.8.jar META-INF/maven/plugin.xml, goal sign
  found: defaults signer=gpg, useAgent=true, bestPractices=false, keyname=${gpg.keyname} (unset), passphraseEnvName=MAVEN_GPG_PASSPHRASE. useAgent description: "in 'batch' mode, gpg-agent will be prevented to pop up pinentry dialogue, hence best is to 'prime' the agent caches beforehand. GPG Signer: ... In non-interactive mode gpg options are appended with --pinentry-mode error, preventing gpg agent to pop up pinentry dialogue. Agent will be able to hand over only cached passwords."
  implication: with Maven -B (non-interactive) the plugin forbids pinentry; GPG_TTY cannot help; only a primed agent cache (or MAVEN_GPG_PASSPHRASE, or dropping -B) lets a protected key sign

- timestamp: 2026-11-22T20:30:00Z
  checked: javap -c -p GpgSigner.class and AbstractGpgMojo.class (maven-gpg-plugin 3.2.8)
  found: AbstractGpgMojo calls signer.setInteractive(settings.isInteractiveMode()). GpgSigner: `if (!isInteractive) { add --batch, --no-tty; if (passphrase == null && gpg >= 2.1) add --pinentry-mode error }`. Passphrase is looked up from env.MAVEN_GPG_PASSPHRASE, then mojo config, settings.xml, project properties (with a W A R N I N G banner for the last three); a non-null passphrase uses --pinentry-mode loopback instead. gpg runs with DefaultConsumer for stdout and stderr (raw lines on the console, no [INFO]/[ERROR] prefix); a non-zero exit becomes MojoExecutionException("Exit code: " + n). No --local-user unless keyname is set.
  implication: bytecode confirms the plugin.xml text. Maven -B -> interactive=false -> gpg --batch --no-tty --pinentry-mode error. gpg's real reason is the raw line printed just above Maven's [ERROR]; "Exit code: 2" carries no reason by itself.

- timestamp: 2026-11-22T20:31:00Z
  checked: owner keyring, read-only (gpg --list-secret-keys --with-colons; gpg-connect-agent --no-autostart KEYINFO <grip>; no signing, passphrase not touched)
  found: exactly 1 secret primary key, <KEYID>, algo 22 (EdDSA), caps scESC (signs), plus an encryption subkey. Agent KEYINFO for the signing key: protection=P (passphrase-protected), cached=1 (passphrase currently in the agent cache, observed 2026-11-22T20:31Z, after the failed run).
  implication: the key is protected, so it needs a passphrase from pinentry or the cache. Only one secret key, so default-key selection did not cause this failure (it becomes a risk only if a second secret key is added). The cache is warm now, so someone primed the agent after the failure; that cache expires (gpg-agent default-cache-ttl 600 s, no gpg-agent.conf overrides it).

- timestamp: 2026-11-22T20:36:00Z
  checked: scratchpad/scripts/repro.sh, throwaway ed25519 keys (protected key in g/, %no-protection key in h/), gpg 2.4.8 + pinentry-curses, the exact gpg args the plugin builds, stdin not a tty (as under Java), cache flushed with RELOADAGENT before each cold case
  found:
    A  -B args (--batch --no-tty --pinentry-mode error), cold, GPG_TTY unset            -> exit 2, "gpg: signing failed: No pinentry"
    B  -B args, cold, inside a real pty, GPG_TTY=$(tty)=/dev/pts/3                       -> exit 2, "gpg: signing failed: No pinentry"
    C  interactive args (no -B), cold, GPG_TTY unset                                     -> exit 2, "gpg: signing failed: Inappropriate ioctl for device"
    C2 interactive args, cold, GPG_TTY="not a tty" (what $(tty) yields in a Claude Code shell) -> exit 2, "gpg: signing failed: No such file or directory"
    D  -B args after priming the cache with one clearsign                               -> exit 0
    E  -B args, %no-protection key (the rehearsal's key type), cold                      -> exit 0
  cleanup: throwaway homes, agents and work dir removed; only the owner's agent (--homedir ~/.gnupg) runs; owner KEYINFO still cached=1 protection=P (untouched)
  implication: with the doc's -B commands, exit 2 is certain whenever the cache is cold, with or without GPG_TTY and with or without a terminal. A primed cache is the only thing that makes the documented commands work. GPG_TTY matters only if -B is dropped. The rehearsal passed only because its key had no passphrase.

- timestamp: 2026-11-22T20:40:00Z
  checked: scratchpad/scripts/maven.sh, the real release profile from `git archive HEAD` (0.1.0), `./mvnw -B -ntp -o -s <empty settings> -Prelease clean verify -DskipTests -pl morimail -am` (verify only, owner settings.xml not read), GNUPGHOME = throwaway protected ed25519 key, GPG_TTY unset
  found: F1 cold cache -> mvn exit 1 with the user's exact line "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-gpg-plugin:3.2.8:sign (sign-artifacts) on project morimail-parent: Exit code: 2". The console shows, in order: "[INFO] --- gpg:3.2.8:sign (sign-artifacts) @ morimail-parent ---", "[INFO] Signer 'gpg' is signing 1 file with key default", two raw lines "gpg: signing failed: No pinentry", then the Reactor Summary (morimail SKIPPED), BUILD FAILURE, and 10 lines later the [ERROR] Exit code: 2. F2 after one priming clearsign -> BUILD SUCCESS, 5 .asc files. Cleanup confirmed (throwaway home, agent and scratch copy gone).
  implication: the user's failure is reproduced byte for byte with only the passphrase cache varied. morimail-parent fails first only because it is the first module signed. The diagnostic line is above the Reactor Summary, not directly above [ERROR], so it is easy to miss.

- timestamp: 2026-11-22T20:42:00Z
  checked: repository target/central-publishing/central-bundle.zip and ~/.m2/repository/de/morisoft (timestamps, listing, read-only); gpg --verify of morimail-0.1.0.pom.asc (public key only)
  found: bundle written 2026-11-22T20:20:00Z with 30 entries, 5 .asc, no tests/sample entry; ~/.m2 de/morisoft 0.1.0 installed 20:19-20:20 (before this session started, so not created by this investigation). Signature "made 20:19:59 UTC using EDDSA key ...<KEYID>, Good signature from Stephan Wald".
  implication: after the failure, someone primed the owner's agent and re-ran the step 5 skipUpload check, and the doc's -B command then signed with the owner's key and produced the expected 30-entry bundle. The warm-up fix works on the owner's machine. The check installed 0.1.0 under ~/.m2 (the doc already says to remove it).

- timestamp: 2026-11-22T20:44:00Z
  checked: scratchpad/scripts/warmup.sh, throwaway protected key, inside a real pty (script), piped warm-up `echo test | gpg --clearsign > /dev/null`
  found: W1 GPG_TTY unset -> exit 2 "gpg: signing failed: Inappropriate ioctl for device". W2 GPG_TTY=$(tty) -> pinentry prompt drawn on the pty and waiting (timeout 124, prompt text seen). Library test phase takes about 7 s (surefire reports), well below gpg-agent's default-cache-ttl of 600 s.
  implication: GPG_TTY is needed for the warm-up (its stdin is a pipe, so gpg cannot find the tty itself), not for Maven. The warm-up must run in a real terminal; in a Claude Code `!` shell `$(tty)` is "not a tty" (C2 -> "No such file or directory"). The cache lasts 10 minutes after the last use, so a long pause between the skipUpload check and the real deploy needs a second warm-up.

- timestamp: 2026-11-22T21:15:00Z
  checked: resume (goal find_and_fix): git log -- docs/release.md, docs/release.md at HEAD, 04-UAT.md, git diff 72e7f7f HEAD of the three POMs
  found: the recommended fix is already committed. c359ec1 "fix(04-05): warm up gpg-agent before each deploy" (20:45Z) plus review fixes 49cfe7b (IN-01 name --pinentry-mode error), 7b97943 (IN-02 same-key check), f9096a9 (IN-03 warm-up prints nothing on success). At HEAD: step 2.3 (line 33) scopes GPG_TTY to the warm-up and says the deploy cannot prompt; step 5 (line 68) says to use a real terminal, not a Claude Code `!` command, and explains -B -> --pinentry-mode error; the warm-up `export GPG_TTY=$(tty)` + `echo test | gpg --clearsign > /dev/null` stands before the skipUpload check (lines 70-73) and again before the real deploy (lines 104-107); line 77 covers the 10-minute cache and several secret keys; line 115 replaces "gpg asks for the passphrase" with "signs from the agent cache and cannot prompt"; line 117 says where the raw `gpg:` line appears and that "No pinentry" means an empty cache. POMs changed only in the version (0.1.0 -> 0.2.0-SNAPSHOT); the gpg plugin config is untouched. 04-UAT.md test 5 (owner, real terminal: warm-up, 30-entry skipUpload check, warm-up, real deploy, Portal validation) is `result: pass`; 81ff531 bumps to 0.2.0-SNAPSHOT "after the 0.1.0 release".
  implication: every fix item from fix_rationale is in place and the owner has already used it for the real release. A second edit would duplicate it (Rule 0), so files_changed of this session is the existing commits.

- timestamp: 2026-11-22T21:22:00Z
  checked: scratchpad/verify-doc.sh + ptyrun.py (revert-and-reconfirm). Throwaway passphrase-protected ed25519 key in its own GNUPGHOME; pinentry-curses; each step in a fresh pty (python pty.fork, TERM=xterm); the warm-up taken verbatim from both step 5 code blocks at HEAD (awk, both blocks identical); Maven = `./mvnw -B -ntp -o -s <empty settings> -Prelease clean verify -DskipTests -pl morimail -am` on a `git archive HEAD` copy (the sign execution only; no deploy, no install, no upload)
  found:
    OLD (72e7f7f doc: GPG_TTY=$(tty) exported in the deploy shell, no warm-up, -B, cold cache cached=0 protection=P) -> mvn rc=1; "gpg: signing failed: No pinentry" twice at log line 25-26, under "--- gpg:3.2.8:sign (sign-artifacts) @ morimail-parent ---" (line 23), above "Reactor Summary" (line 28); line 38 "[ERROR] Failed to execute goal ...maven-gpg-plugin:3.2.8:sign (sign-artifacts) on project morimail-parent: Exit code: 2" (the owner's exact error)
    NEW warm-up (doc text, pinentry-curses prompt seen, passphrase typed into the pty) -> rc=0, no `gpg:` output; agent now cached=1
    NEW deploy-equivalent (same Maven -B command, GPG_TTY set, primed cache) -> mvn rc=0, BUILD SUCCESS, signs 1 + 4 files, 5 .asc, 5 good signatures by gpg --verify
  cleanup: throwaway GNUPGHOME, agent and archive copy removed; only the owner's agent (--homedir ~/.gnupg) runs, never used
  implication: the bug returns when the old procedure is followed and is gone when the new one is followed, with only the doc's steps varied. This closes the one link the earlier run did not exercise (passphrase typed into pinentry by the doc's exact warm-up, instead of a loopback priming). The doc's claims "prints nothing on success" and "gpg: line under --- gpg:3.2.8:sign, above the Reactor Summary" match the real output.

- timestamp: 2026-11-22T21:24:00Z
  checked: `./mvnw -o -B -ntp verify` in /home/coder/morimail at HEAD (0.2.0-SNAPSHOT)
  found: BUILD SUCCESS; morimail 316 tests, morimail-sample-serial 137 tests, 0 failures, 0 errors, 0 skipped (live test excluded by default)
  implication: the repository build is green; the doc fix touched no code

## Resolution

root_cause: "docs/release.md step 5 runs the deploy with Maven `-B`, which makes maven-gpg-plugin 3.2.8 call gpg with `--batch --no-tty --pinentry-mode error` (no passphrase source configured), so gpg-agent may only use an already cached passphrase; the owner's key is passphrase-protected and the cache was cold, so gpg exits 2 on the first file (morimail-parent POM); the doc has no agent warm-up step, wrongly says gpg asks for the passphrase during the deploy, offers GPG_TTY (step 2) as if it made the deploy prompt, and does not say where gpg's real message appears; the 04-04 rehearsal could not catch it because its throwaway key had no passphrase (%no-protection)"
fix: "docs/release.md only, no POM change (already committed by gap closure 04-05 before this session resumed: c359ec1, refined by review fixes 49cfe7b, 7b97943, f9096a9; this session made no further doc edit). Step 2.3 now says the deploy cannot ask for the passphrase and GPG_TTY is for the warm-up only. Step 5 says to run in a real terminal, not a Claude Code `!` command, explains that -B gives --pinentry-mode error, and puts the warm-up `export GPG_TTY=$(tty)` + `echo test | gpg --clearsign > /dev/null` before the skipUpload check and again before the real deploy, with the 10-minute cache note and the several-keys hint (--local-user / -Dgpg.keyname). 'gpg asks for the passphrase' became 'signs from the agent cache and cannot prompt, because of -B'. One paragraph says Exit code: 2 carries no reason, gpg's own unprefixed gpg: line sits under --- gpg:3.2.8:sign above the Reactor Summary, and 'No pinentry' means warm up and rerun."
verification:
  target_test: { result: pass, note: "driving check is the doc procedure itself (no automated test exists for docs): new step 5 with a throwaway protected key -> warm-up rc=0, Maven -B release-profile sign BUILD SUCCESS, 5 .asc, 5 good signatures" }
  mutation_check: { result: skipped, reason_if_skipped: "doc-only change; no mutation tool applies to Markdown (no Stryker/PIT configured)" }
  no_op_deletion: { result: pass, deletion_justified_by_rca: true, note: "docs/release.md 72e7f7f..HEAD: 27 insertions, 4 deletions; the 4 removed lines are the wrong 'gpg asks for the passphrase' / GPG_TTY-as-remedy text the RCA names, replaced by correct text and the warm-up" }
  adjacent_tests: { result: pass, suites_run: ["./mvnw -o -B -ntp verify: morimail 316, morimail-sample-serial 137, 0 failures"] }
  revert_and_reconfirm: { result: pass, bug_returned_on_revert: true, fixed_on_reapply: true, note: "old step 5 (72e7f7f) -> the owner's exact 'maven-gpg-plugin:3.2.8:sign ... on project morimail-parent: Exit code: 2' with 'gpg: signing failed: No pinentry'; new step 5 (HEAD, warm-up typed into pinentry-curses in a pty) -> BUILD SUCCESS" }
  human: "04-UAT.md test 5 passed: the owner followed the fixed step 5 in a real terminal (warm-up, 30-entry skipUpload check, warm-up, real deploy) and the Portal validated the deployment; 0.1.0 was released (81ff531)"
  guardrail_verdict: accepted
oracle_type: "specified (spec 13.3 + UAT test 3/5 expectation: the signed deploy completes without an undocumented failure; Maven exit code and gpg --verify of each .asc)"
files_changed: ["docs/release.md (c359ec1, 49cfe7b, 7b97943, f9096a9)"]
prevention:
  five_whys:
    - "docs branch: why exit 2? -B forbids pinentry and the cache was cold. Why no warm-up in the doc? The passphrase/GPG_TTY lines came from research notes ('signing prompts via gpg-agent') that were never executed with a protected key. Why not executed? The 04-04 rehearsal key was %no-protection, so its signing path never needed a passphrase."
    - "environment branch: why did GPG_TTY not help? The plugin passes --pinentry-mode error under -B, so no pinentry can start in any terminal. Why was GPG_TTY offered? The common gpg advice (GPG_TTY for pinentry) assumes gpg may prompt, which is false for maven-gpg-plugin in batch mode (its useAgent docs say to prime the agent)."
  why_not_caught: "verify step (the 04-04 release rehearsal): it ran the real release profile, but with an unprotected throwaway key, so the passphrase path was never exercised; no automated gate exists for doc procedures"
  recurrence_guard: "docs/release.md step 5 warm-up before each deploy + the 'Exit code: 2' / 'No pinentry' diagnosis paragraph (line 117); KB pattern 'release-doc-gpg-signing' in .planning/debug/knowledge-base.md: rehearse signing with a passphrase-protected throwaway key and a cold agent cache, not %no-protection"
