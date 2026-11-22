# Releasing morimail

The owner runs every step on their own machine. Claude prepares the build and rehearses the bundle, and never runs these steps. Use `./mvnw`, which pins Maven 3.10.1. Never use Maven 4, which the Central publishing plugin documents as not working.

## Before the first release

- Enable GitHub private vulnerability reporting (Settings, Code security), because `SECURITY.md` routes reports there.
- CI must be green on Temurin 17 for the commit you release. That includes the release build with `-Prelease verify -Dgpg.skip -pl morimail -am`. Step 4 pushes that commit and waits for CI before the deploy.

## 1. Verify the namespace

1. Sign in to the Central Portal at https://central.sonatype.com.
2. Open the username menu, then View Namespaces, then Add Namespace, and enter `de.morisoft`.
3. Choose Verify Namespace and copy the verification key.
4. Add the key as a TXT record on `morisoft.de`.
5. Confirm in the Portal only once the record is live. A cached negative DNS answer can delay the check if you confirm too early (spec section 13.2).

## 2. Create and publish the GPG key

1. Create the key with the maintainer identity `Stephan Wald <stephan@morisoft.de>` (spec section 13.4):

   ```
   gpg --gen-key
   gpg --list-keys
   ```

2. Publish the public key. Central accepts keyserver.ubuntu.com, keys.openpgp.org and pgp.mit.edu:

   ```
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>
   ```

3. The passphrase goes through gpg-agent. The deploy cannot ask for it, so step 5 starts with a warm-up that puts it into the agent's cache. `GPG_TTY` is for that warm-up; it does not make the deploy prompt. Never set `gpg.passphrase`; the plugin deprecates it.

## 3. Put the Portal token in settings.xml

1. Open https://central.sonatype.com/usertoken and choose Generate User Token. Copy both parts at once, because a token cannot be shown again after the dialog closes.
2. Put them into `~/.m2/settings.xml` as a server with the id `central`:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN_USERNAME</username>
         <password>TOKEN_PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

3. The token never goes into the repository or into CI (spec section 13.5).

## 4. Set the release version

1. Change the three POM versions from `0.1.0-SNAPSHOT` to `0.1.0`. On macOS use `sed -i ''`.

   ```
   sed -i 's/0\.1\.0-SNAPSHOT/0.1.0/' pom.xml morimail/pom.xml morimail-sample-serial/pom.xml
   ```

2. `git diff` must show exactly three changed lines.
3. Run `./mvnw -B -ntp verify`, then commit with the message "release 0.1.0" and push the commit.
4. Wait until CI is green on that commit. Only then go on to the deploy, because publishing to Central cannot be undone.

## 5. Deploy

Run this step in a real terminal, not as a `!` command in Claude Code, where `tty` prints `not a tty`. Both deploy commands use `-B`, so the gpg plugin calls gpg with `--pinentry-mode error`, which forbids a passphrase prompt. It signs only if gpg-agent already holds the passphrase. So warm up the agent right before each deploy command, and enter the passphrase when asked:

```
export GPG_TTY=$(tty)
echo test | gpg --clearsign > /dev/null
```

The warm-up prints nothing on success. If it prints a `gpg:` line, fix that before you deploy.

By default the agent keeps the passphrase for 10 minutes after its last use. If more time passes before the deploy, warm up again. If you have several secret keys, add `--local-user <KEYID>` to the `gpg` line and `-Dgpg.keyname=<KEYID>` to both deploy commands.

First, an optional check that sends nothing. It builds and signs the bundle and skips the upload:

```
./mvnw -B -ntp -Prelease clean deploy -DskipUpload=true -pl morimail -am
```

It writes `target/central-publishing/central-bundle.zip`. List it with `unzip -Z1`. It must hold exactly 30 entries. These are the five base files:

- `morimail-parent-0.1.0.pom`
- `morimail-0.1.0.pom`
- `morimail-0.1.0.jar`
- `morimail-0.1.0-sources.jar`
- `morimail-0.1.0-javadoc.jar`

Each one comes with `.asc`, `.md5`, `.sha1`, `.sha256` and `.sha512` files. There is no `-tests.jar` and no sample artifact. This is the bundle seen in the rehearsal on 2026-11-22, run with a throwaway key.

Two notes on that check:

- At a SNAPSHOT version no zip is made, so run it after step 4.
- With `-o` (offline) the publish goal is skipped, so never add `-o`.

The check installs the 0.1.0 artifacts under `~/.m2/repository/de/morisoft`. Remove that directory afterwards if you do not want them there.

Then the real deploy. Warm up the agent again:

```
export GPG_TTY=$(tty)
echo test | gpg --clearsign > /dev/null
```

Then deploy:

```
./mvnw -B -ntp -Prelease clean deploy -pl morimail -am
```

Tests run. The deploy signs from the agent cache and cannot prompt, because of `-B`. `autoPublish` is false, and the plugin waits until the deployment is validated, so nothing is public yet.

If a deploy fails with `maven-gpg-plugin:3.2.8:sign` and `Exit code: 2`, that line gives no reason. gpg's own message is a `gpg:` line without `[INFO]` or `[ERROR]`, under `--- gpg:3.2.8:sign` and above the Reactor Summary. `gpg: signing failed: No pinentry` means the agent held no passphrase for the signing key: warm up and run the same command again. If you have several secret keys, check that the warm-up and the deploy name the same `<KEYID>`.

## 6. Review and publish in the Portal

1. Under Deployments, check the files, the signatures and the validation result, then choose Publish.
2. A release on Central is permanent and cannot be replaced (spec section 13.2). Check twice.
3. If validation fails, drop the deployment, fix the cause and deploy again. That is possible because nothing was published.

## 7. Tag

Tag the "release 0.1.0" commit from step 4, not whatever `HEAD` is now. Later commits can sit on top of it. Check that the second command shows that commit before you push the tag.

```
git tag -a v0.1.0 -m "morimail 0.1.0" "$(git log -1 --format=%H --grep='^release 0\.1\.0$')"
git show --no-patch --format='%h %s' v0.1.0^{commit}
git push origin v0.1.0
```

## 8. Bump to the next SNAPSHOT

1. Set the three versions to the next SNAPSHOT, for example `0.2.0-SNAPSHOT` (or `0.1.1-SNAPSHOT` for a fix line). Use the same `sed` pattern as in step 4, with the new version.
2. Update the README sentences that say 0.1.0 is not released or not published yet.
3. Run `./mvnw -B -ntp verify`, commit, and push.
