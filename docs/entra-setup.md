# Setting up Microsoft Entra and Exchange Online for morimail

This guide takes you from an empty app registration in Microsoft Entra to a serial mail whose second run sends nothing. It follows spec section 9. Follow the steps in order.

## Before you start

You need:

- An Entra admin who can register apps and grant admin consent.
- For path B in step 3: the Organization Management role group in Exchange Online, and the Exchange Administrator role in Microsoft Entra ID.
- A mailbox licensed for Exchange Online, on a verified custom domain. This is the sender.
- JDK 17 and a checkout of this repository.
- openssl.
- For path B only: PowerShell with the ExchangeOnlineManagement module.

The repository is public. Every file this guide creates stays outside the repository: the key, the certificate, the `.p12`, the campaign copy with real addresses, and `account.properties` with real ids. Create them in a directory of your own, for example `~/morimail-keys` and `~/morimail-campaigns`.

All values shown are examples: the zero GUID, `example.com` and `example.org`. Replace them with your own.

## 1. Register the app

1. Open the Microsoft Entra admin center.
2. Go to Entra ID, App registrations, New registration.
3. Enter a name, for example `morimail`. Choose "Single tenant only". Select Register.
4. On the Overview page, note the Application (client) ID and the Directory (tenant) ID. You need both in step 8.

Client credentials need no redirect URI.

## 2. Create a certificate

morimail signs its token request with a certificate. You upload the public part to Entra. The private key stays on your machine.

Work in a directory outside the repository. Set the password for the `.p12` file. The shell reads it without echoing it:

```bash
read -rs MAIL_CERT_PASSWORD && export MAIL_CERT_PASSWORD
```

Choose a strong password. Never put a password or a secret itself on a command line.

Create a self-signed certificate and a PKCS12 file for it:

```bash
openssl req -x509 -newkey rsa:2048 -sha256 -nodes -keyout app.key -out app.cer -days 730 -subj "/CN=morimail-app"
```

```bash
openssl pkcs12 -export -inkey app.key -in app.cer -name morimail -out app.p12 -passout env:MAIL_CERT_PASSWORD
```

Then delete `app.key`. The file `app.p12` holds the key behind the password.

Upload `app.cer` to the app registration: Certificates & secrets, Certificates, Upload certificate, select `app.cer`, then Add. morimail does not need the thumbprint, because it computes `x5t#S256` itself.

Fallback: if an old JDK 17 patch level cannot read `app.p12`, create the file again with `-legacy` after `-export`:

```bash
openssl pkcs12 -export -legacy -inkey app.key -in app.cer -name morimail -out app.p12 -passout env:MAIL_CERT_PASSWORD
```

This needs `app.key`, so delete it only after you know that `app.p12` loads.

Quick alternative for a first test: a client secret. Go to Certificates & secrets, Client secrets, New client secret. Copy the Value at once, because Entra never shows it again. A secret lives at most 24 months, and Microsoft recommends under 12. Microsoft says client secrets should not be used in production. Give the secret to the shell the same way as the password:

```bash
read -rs MAIL_CLIENT_SECRET && export MAIL_CLIENT_SECRET
```

## 3. Grant Mail.Send

Path A (Entra admin consent) is the path verified live on 2026-11-22. Path B (Exchange RBAC for Applications) is recommended, because it limits the app to chosen mailboxes, but it is not yet verified live.

**Path A: Entra admin consent.**

1. Open the app registration, then API permissions, Add a permission.
2. Choose Microsoft Graph, Application permissions, `Mail.Send`, then Add permissions.
3. Select Grant admin consent for your tenant. The status column shows "Granted for" and your tenant name.

The app can now send as any user in the organization.

**Path B: Exchange RBAC for Applications, scoped to chosen mailboxes.**

The cmdlets below come from Microsoft's page "Role Based Access Control for Applications in Exchange Online" (https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac). The page shows "Last updated 2026-08-21". They were copied from it on 2026-11-22. Check the page again before you run them.

Take the ids from Enterprise applications in the Entra admin center, not from App registrations, which shows different values. The Application ID is the AppId. The Object ID is the ObjectId.

Exchange cannot create the service principal. `New-ServicePrincipal` creates a pointer to the Entra one, which exists once the app is registered. If Enterprise applications does not list your app, create the service principal in Entra first.

Connect, then create the pointer, the scope and the role assignment:

```powershell
Connect-ExchangeOnline -UserPrincipalName admin@example.com
New-ServicePrincipal -AppId 00000000-0000-0000-0000-000000000000 -ObjectId 00000000-0000-0000-0000-000000000000 -DisplayName "morimail"
New-ManagementScope -Name "morimail senders" -RecipientRestrictionFilter "Alias -eq 'newsletter'"
New-ManagementRoleAssignment -App 00000000-0000-0000-0000-000000000000 -Role "Application Mail.Send" -CustomResourceScope "morimail senders"
```

In `New-ManagementRoleAssignment`, `-App` takes the ObjectId, the AppId or the display name. The scope filter here selects the mailbox with the alias `newsletter`. Do not filter on `PrimarySmtpAddress`. Microsoft says to use `EmailAddresses` instead, and `Alias` is a single value that is easy to check.

Test the result:

```powershell
Test-ServicePrincipalAuthorization -Identity 00000000-0000-0000-0000-000000000000 -Resource newsletter@example.com
```

`InScope` must be True for the sending mailbox. If it is False, adjust the scope filter until it is True. The test shows the configured state at once. Without `-Resource`, `InScope` shows "Not Run".

## 4. The union trap

Entra grants and RBAC grants combine as a union, and each authority acts on its own. If `Mail.Send` is also consented in Entra, the RBAC scope has no effect: the app can still send as every user.

With path B, remove the Entra consent:

1. In the Entra admin center, go to Entra ID, Enterprise apps, All applications, and select the app.
2. Select Permissions, then the tab Admin consent.
3. Select the "..." of `Mail.Send`, then Revoke permission.
4. Open the app registration, API permissions, and remove `Mail.Send` from the list, so that nobody consents to it again.

`Test-ServicePrincipalAuthorization` shows RBAC grants only. It excludes grants made in Entra. So `InScope` True does not prove that the trap is cleared. Check the Admin consent tab as well.

## 5. The delay

Permission changes take 30 minutes to 2 hours to reach live API calls. This is how Microsoft's page states it. The cache of an app with no calls is reset after 30 minutes. The cache of an active app is kept for up to 2 hours. The test cmdlet bypasses the cache.

A 403 `ErrorAccessDenied` right after setup is expected. In the live test of 2026-11-22, sends got intermittent 403 answers for minutes after the admin consent.

The sample treats a 403 as a rejected sender and stops with exit 3. Wait, then run the same command again. Rows already accepted are skipped.

## 6. Required roles

- Path B: Organization Management in Exchange Online, and the Exchange Administrator role in Microsoft Entra ID. Microsoft's page names both.
- Path A: an administrator who can grant admin consent for Microsoft Graph application permissions. Revoking a consent needs at least Cloud Application Administrator.

## 7. The sending mailbox

The `from` mailbox must exist and be licensed for Exchange Online. A sender without an Exchange mailbox gets 404 `ErrorInvalidUser`. morimail raises a SenderRejectedException for it and the sample stops with exit 3. This was seen live.

Use an address on a verified custom domain. A sender on an `onmicrosoft.com` address is limited to 100 external recipients per 24 hours.

Exchange Online documents a limit of 30 messages per minute per mailbox for SMTP client submission, and 10,000 recipients per day. Microsoft does not say whether Graph `sendMail` counts against the per-minute limit. The sample default `--rate 20` stays below it either way.

A shared mailbox has no licence of its own. Whether the app can send from one is listed under Not yet verified live.

Graph saves every sent mail in the mailbox's Sent Items by default. The `saveToSentItems` option is not set by the sample, so each campaign mail lands in Sent Items as an audit trail. Library users can set the account option `saveToSentItems` to false.

## 8. Fill in account.properties

Copy the example campaign to a directory outside the repository. Run this from the repository root:

```bash
mkdir -p ~/morimail-campaigns && cp -r morimail-sample-serial/example-campaign ~/morimail-campaigns/first-test
```

Edit `account.properties` in the copy. It uses these keys:

| Key | Value |
|-----|-------|
| `account.id` | Any short id for this account, for example `entra-main`. |
| `tenantId` | The Directory (tenant) ID from step 1. |
| `clientId` | The Application (client) ID from step 1. |
| `from` | The sending mailbox from step 7. |
| `certificate.path` | The absolute path to `app.p12`. Use forward slashes, because a backslash starts an escape in a `.properties` file. |
| `certificate.passwordEnv` | The name of the environment variable that holds the password: `MAIL_CERT_PASSWORD`. |
| `secretEnv` | The name of the environment variable that holds a client secret: `MAIL_CLIENT_SECRET`. Use it in place of the two certificate keys. |

The file holds the names of environment variables, never a secret. A password or a secret reaches the sample only through the environment.

Edit `recipients.csv` in the copy. Keep the header line `email,firstName,city,topic`. Put your own address in row 1. Put test addresses that you control in the other rows.

The copy, its `journal.jsonl` and its output directory hold real addresses, so keep them outside the repository.

## 9. Verify with the sample

Run one command at a time. Wait for each to finish before you start the next. Run all of them from the repository root.

**(a) Build the sample.**

```bash
./mvnw -B -ntp -DskipTests package -pl morimail-sample-serial -am
```

**(b) Dry run.** This writes the rendered mails and lists the attachments. It needs no environment variables and does not touch the network or the journal. It ends with `dry run: <n> mails written to` and the output directory.

```bash
java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send --account ~/morimail-campaigns/first-test/account.properties --campaign ~/morimail-campaigns/first-test --dry-run --out ~/morimail-campaigns/first-test-out
```

Open the files in the output directory and read the subject and the body of a mail.

**(c) One real mail.** The real runs need the password or secret in the environment. If you work in a new shell, run the `read` command from step 2 again. This command sends exactly one mail: row 1, to your own address.

```bash
java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send --account ~/morimail-campaigns/first-test/account.properties --campaign ~/morimail-campaigns/first-test --limit 1
```

The run ends with `accepted 1, skipped 0, failed 0`. Check your inbox and the Sent Items folder of the sending mailbox.

**(d) The campaign.** Run the same command without `--limit`. Row 1 is skipped, because the journal records it as accepted. The other rows go to your test addresses, at most `--rate` mails per minute (default 20).

```bash
java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send --account ~/morimail-campaigns/first-test/account.properties --campaign ~/morimail-campaigns/first-test
```

**(e) The same command again.** It sends nothing and prints `accepted 0, skipped <rows>, failed 0`, where `<rows>` is the number of rows in `recipients.csv`.

```bash
java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send --account ~/morimail-campaigns/first-test/account.properties --campaign ~/morimail-campaigns/first-test
```

**Exit codes.**

| Exit | Meaning |
|------|---------|
| 0 | Nothing failed. |
| 1 | At least one mail failed, or the journal could not be written. |
| 2 | A pre-flight or argument problem. Nothing was sent. |
| 3 | The run was stopped by a rejected sender or required authentication. |

What to do for exit 3:

- A 403 `ErrorAccessDenied` right after setup: wait for the delay in step 5, then run the same command again. If it stays, check the scope with `Test-ServicePrincipalAuthorization`.
- A 404 `ErrorInvalidUser`: the sender has no Exchange mailbox (step 7).
- Authentication required: check `tenantId`, `clientId`, the certificate or the secret, and the consent in step 3.

A stopped or interrupted run is resumed by running the same command again. Rows already accepted are skipped. A row whose send was interrupted can be sent twice, because morimail delivers at-least-once.

The journal is `journal.jsonl` in the campaign copy. Its keys start with the campaign id, which defaults to the name of the campaign directory. A new directory name starts a new journal.

To run the live test of the library itself, use `./mvnw -B -ntp -Plive test -pl morimail` with the `MORIMAIL_LIVE_*` variables that `LiveGraphTest` describes. It sends real mail, so only the owner runs it.

## Not yet verified live

These are the items that no live run has confirmed. The first live test of 2026-11-22 used path A only.

- Path B (Exchange RBAC for Applications) as a whole: the cmdlets, the scope filter on a real mailbox, and a send under the scoped assignment.
- A shared mailbox as the sender.
- The `from` display name: only the address was seen.
- The refusal of a mailbox outside the RBAC scope.
- `MailboxNotEnabledForRESTAPI` as an error code.
- A 401 for the mailbox after a fresh token.
