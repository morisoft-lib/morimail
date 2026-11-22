---
status: diagnosed
trigger: "release-doc-gpg-signing: docs/release.md lets the owner run the release, including the signed deploy, without hitting an undocumented failure; the Portal validates the deployment and the bundle matches the 30-entry listing."
created: 2026-11-22T20:21:00Z
updated: 2026-11-22T20:48:00Z
goal: find_root_cause_only
---

## Current Focus

bug_class: Bohrbug (deterministic: same command, same environment, same failure on the first sign call)
hypothesis: CONFIRMED - H2. Maven `-B` (in both documented deploy commands) makes maven-gpg-plugin 3.2.8 run gpg with `--batch --no-tty --pinentry-mode error`, so gpg-agent can only hand over a cached passphrase. The owner's key is passphrase-protected and the cache was cold, so the first sign (morimail-parent POM) fails with gpg exit 2. docs/release.md never tells the owner to prime the agent, says "gpg asks for the passphrase" (impossible under -B), and presents GPG_TTY as the remedy (it only matters for the warm-up).
test: done (bytecode, gpg matrix, real release-profile build, warm-up pty test, owner's post-priming success)
expecting: n/a
next_action: return ROOT CAUSE FOUND to the orchestrator (goal find_root_cause_only); no doc or source edits

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

## Resolution

root_cause: "docs/release.md step 5 runs the deploy with Maven `-B`, which makes maven-gpg-plugin 3.2.8 call gpg with `--batch --no-tty --pinentry-mode error` (no passphrase source configured), so gpg-agent may only use an already cached passphrase; the owner's key is passphrase-protected and the cache was cold, so gpg exits 2 on the first file (morimail-parent POM); the doc has no agent warm-up step, wrongly says gpg asks for the passphrase during the deploy, offers GPG_TTY (step 2) as if it made the deploy prompt, and does not say where gpg's real message appears; the 04-04 rehearsal could not catch it because its throwaway key had no passphrase (%no-protection)"
fix: "(diagnosis only; not applied) docs/release.md: add a warm-up in a real terminal right before each deploy command (export GPG_TTY=$(tty); echo test | gpg --clearsign > /dev/null), fix the 'gpg asks for the passphrase' sentence, re-scope the step 2.3 GPG_TTY line to the warm-up, and add one line on reading the raw gpg: line under gpg:3.2.8:sign"
verification: "n/a (find_root_cause_only)"
files_changed: []
