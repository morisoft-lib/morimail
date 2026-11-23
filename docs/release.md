# Releasing morimail

The owner runs every step on their own machine. Claude prepares the build and rehearses the bundle, and never runs these steps. Use `./mvnw`, which pins Maven 3.10.1. Never use Maven 4, which the Central publishing plugin documents as not working. A release publishes the parent POM `de.morisoft:morimail-parent`, the library `de.morisoft:morimail` and the helper `de.morisoft:morimail-html`, all with the same version; the sample and the test-jar are never published.

## Before every release

- Enable GitHub private vulnerability reporting (Settings, Code security), because `SECURITY.md` routes reports there.
- CI must be green on Temurin 17 for the commit you release. That includes the release build with `-Prelease verify -Dgpg.skip -pl morimail,morimail-html -am`. Step 4 pushes that commit and waits for CI before the deploy.

## One-time setup (steps 1 to 3)

Steps 1 to 3 are one-time. If you already released 0.1.0, check that the namespace shows as verified, that `gpg --list-secret-keys` shows the key that signed 0.1.0, and that the token in settings.xml is still valid, then continue at step 4. Do not create a second signing key, because consumers who verify signatures expect the same key.

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

1. Set the release version and the next SNAPSHOT in the shell:

   ```
   VERSION=0.2.0
   NEXT=0.3.0-SNAPSHOT
   ```

   Steps 5 to 8 use both variables, so set them again in every new shell.

2. Change the four POM versions from `${VERSION}-SNAPSHOT` to `${VERSION}`. On macOS use `sed -i ''`.

   ```
   sed -i "s#<version>${VERSION}-SNAPSHOT</version>#<version>${VERSION}</version>#" pom.xml morimail/pom.xml morimail-html/pom.xml morimail-sample-serial/pom.xml
   ```

3. `git diff` must show exactly four changed lines, the `<version>` line of each POM.
4. Run `./mvnw -B -ntp verify`. It runs the tests of every module, `morimail-html` included. Then commit and push:

   ```
   git commit -m "release ${VERSION}" pom.xml morimail/pom.xml morimail-html/pom.xml morimail-sample-serial/pom.xml
   git push
   ```

5. Wait until CI is green on that commit. Only then go on to the deploy, because publishing to Central cannot be undone.

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
./mvnw -B -ntp -Prelease clean deploy -DskipUpload=true -pl morimail,morimail-html -am
```

It writes `target/central-publishing/central-bundle.zip`. List it with `unzip -Z1`. It must hold exactly 54 entries. These are the nine base files, with `${VERSION}` standing for the release version:

- `morimail-parent-${VERSION}.pom`
- `morimail-${VERSION}.pom`
- `morimail-${VERSION}.jar`
- `morimail-${VERSION}-sources.jar`
- `morimail-${VERSION}-javadoc.jar`
- `morimail-html-${VERSION}.pom`
- `morimail-html-${VERSION}.jar`
- `morimail-html-${VERSION}-sources.jar`
- `morimail-html-${VERSION}-javadoc.jar`

Each one comes with `.asc`, `.md5`, `.sha1`, `.sha256` and `.sha512` files. There is no `-tests.jar` and no sample artifact. This list was checked in a rehearsal with a throwaway key.

Two notes on that check:

- At a SNAPSHOT version no zip is made, so run it after step 4.
- With `-o` (offline) the publish goal is skipped, so never add `-o`.

The check installs the `$VERSION` artifacts under `~/.m2/repository/de/morisoft`. Remove that directory afterwards if you do not want them there.

Then the real deploy. Warm up the agent again:

```
export GPG_TTY=$(tty)
echo test | gpg --clearsign > /dev/null
```

Then deploy:

```
./mvnw -B -ntp -Prelease clean deploy -pl morimail,morimail-html -am
```

The `morimail` tests run again. The `morimail-html` tests do not run in a release-profile build, because its profile skips them; step 4 ran them. The deploy signs from the agent cache and cannot prompt, because of `-B`. `autoPublish` is false, and the plugin waits until the deployment is validated, so nothing is public yet.

If a deploy fails with `maven-gpg-plugin:3.2.8:sign` and `Exit code: 2`, that line gives no reason. gpg's own message is a `gpg:` line without `[INFO]` or `[ERROR]`, under `--- gpg:3.2.8:sign` and above the Reactor Summary. `gpg: signing failed: No pinentry` means the agent held no passphrase for the signing key: warm up and run the same command again. If you have several secret keys, check that the warm-up and the deploy name the same `<KEYID>`.

## 6. Review and publish in the Portal

1. Under Deployments, check the files, the signatures and the validation result, then choose Publish.
2. A release on Central is permanent and cannot be replaced (spec section 13.2). Check twice.
3. If validation fails, drop the deployment, fix the cause and deploy again. That is possible because nothing was published.

## 7. Tag

Tag the "release ${VERSION}" commit from step 4, not whatever `HEAD` is now. Later commits can sit on top of it. Check that the second command shows that commit before you push the tag.

```
git tag -a "v${VERSION}" -m "morimail ${VERSION}" "$(git log -1 --format=%H --grep="^release ${VERSION}\$")"
git show --no-patch --format='%h %s' "v${VERSION}^{commit}"
git push origin "v${VERSION}"
```

## 8. Bump to the next SNAPSHOT

1. Set the four versions to the next SNAPSHOT:

   ```
   sed -i "s#<version>${VERSION}</version>#<version>${NEXT}</version>#" pom.xml morimail/pom.xml morimail-html/pom.xml morimail-sample-serial/pom.xml
   ```

2. `git diff` must show exactly four changed lines.
3. In `README.md`, set the dependency blocks to `${VERSION}` where they show another version, and change the sentence under Coordinates so it names `${VERSION}` as the current release on Maven Central.
4. Run `./mvnw -B -ntp verify`, commit, and push.
