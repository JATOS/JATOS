# Git credential storage

HTTPS Git passwords and tokens can be managed in **My settings → Git credentials**
and in the **Study Source** modal. Credential fallback and Git network operations
are not implemented yet.

In My settings, enter an HTTPS origin, Git username, and password/token, then
choose **Save**. Profile and credential changes are saved together in one transaction.
There is one credential per user. Enter a new password/token to replace it, or
select **Remove Git credentials** and save to delete it. External sign-in users
can manage credentials while their name and email remain read-only.

GET `/jatos/api/v1/users/:id` includes `gitCredentials` metadata only for the
signed-in user's own account. PATCH on the same endpoint accepts a nested object:

```json
{"name":"My name","gitCredentials":{"origin":"https://github.com","username":"git-user","secret":"new-token"}}
```

Omitting `gitCredentials` leaves it unchanged. Omitted username/origin retain
existing values, and an empty or omitted secret preserves the stored secret.
Use `{"gitCredentials":{"removeCredentials":true}}` to remove it. Removal cannot
be combined with a new secret. Administrators cannot read or modify other users'
Git credentials through these endpoints. Session-authenticated writes require CSRF
protection; API tokens are also supported. Stored secrets are never returned.

Each credential belongs to either a user or a study source. The existing
database constraints enforce exactly one owner and at most one credential per
owner. A token is stored in the same way as a password.

## Service API

`GitCredentialsService` provides status, save and remove operations:

- User operations always apply to the signed-in user.
- Study-source operations require the study owner or an enabled superuser.
  Saving also requires a configured Git source.
- Status returns whether credentials exist, their origin, and their Git username.
- A null or empty secret preserves an existing secret. A new credential requires
  a secret. Removal is a separate operation.
- Changing the origin or username requires entering a new password/token.
- Passwords and tokens are not trimmed. A supplied new secret replaces even an
  unreadable stored value.

The service rechecks permissions and updates managed credential relationships
inside a transaction. Cascading persists credentials, and orphan removal deletes
them when their relationship is cleared. The service calls `UserDao` and
`StudyDao` directly to load, lock, and refresh the user and study with their
credential relationships, following the existing user-before-study lock order. Credential changes join an existing transaction and roll back
with it. Validation, encryption, and safe status responses remain in the service.

## Origins

An origin is an HTTPS scheme, hostname, and optional port, such as
`https://github.com` or `https://git.example.org:8443`. Hostnames are normalized
to lowercase and the default HTTPS port 443 is omitted. A trailing slash is
accepted and removed. Paths, embedded credentials, queries, and fragments are
rejected. Internationalized hostnames must use their ASCII (punycode) form.

Study credentials must match the configured repository URL's origin. Origin
validation does not perform DNS lookup or contact the server. Future Git
transport code must also enforce the credential destination when connecting
and handling redirects; parsing an origin alone is not that enforcement.

## Encryption

`GitCredentialEncryption` uses Java's built-in cryptographic APIs, without a
new dependency. It derives a 256-bit AES key once per instance from
`play.http.secret.key`, using PBKDF2-HMAC-SHA256 with 210,000 iterations and
the fixed purpose-specific salt `jatos-git-credentials-v1`. The salt separates
this encryption key from other uses of the application secret. It is not a
per-user password salt: the input is the existing random application secret.

AES-GCM uses a fresh random 12-byte nonce and a 128-bit authentication tag for
each encryption. The stored field has this format:

```text
v1:<Base64 nonce>:<Base64 ciphertext followed by authentication tag>
```

The format version, owner identity, normalized origin, and Git username are
authenticated as additional data. Copying an encrypted value to another owner
or changing its destination or username causes decryption to fail. The service
encrypts the newly supplied secret when changing these fields.

Keep the JATOS secret stable, random, and backed up separately from the database.
The implementation requires at least 32 UTF-8 bytes and rejects the default
`changeme` value. Length alone does not establish randomness. Changing or losing
the secret makes existing Git credentials unreadable; users must enter them
again. Unknown versions, malformed values, and failed authentication produce a
generic re-entry error without including the stored value or cryptographic
provider details.

Credential relationships and encrypted secrets remain excluded from normal
entity JSON serialization and study exports. Controllers use the safe status response rather than serialize credential entities.


## Generated deployment secret

The Linux/macOS and Windows loaders save their generated fallback secret in
`conf/generated-secret` and reuse it on subsequent starts. Both generate 64
ASCII characters. Explicit `JATOS_SECRET`, `jatos.secret`, or
`play.http.secret.key` settings retain their existing precedence over this
fallback. Both loaders always prepare the fallback file. Explicit configuration
still overrides its value, but an invalid or inaccessible fallback file prevents
startup even when an explicit secret is configured.

The Unix loader restricts the file to mode 600; the Windows loader restricts
access to the current Windows account. These restrictions are reapplied when
loading an existing fallback file. Concurrent creation
does not overwrite an already published secret. Invalid or unreadable saved
files cause startup to fail instead of generating a replacement.

Keep this file when moving or backing up an installation, and persist it on a
volume when running in a container. The Unix updater leaves it in place and
rejects update packages containing `conf/generated-secret`. Release packages
must never contain an installation's generated secret.

An installation previously relying on a new fallback at each startup receives
a new persistent secret on its first start with these loaders. This cannot
recover credentials encrypted under an earlier, unsaved secret.

User settings validation failures return HTTP 400 with `error.code` set to
`VALIDATION_ERROR` and an `error.fields` object mapping field names to arrays of
messages, e.g. `name` or `gitCredentials.secret`. My settings displays these
messages below the corresponding inputs. General errors retain `error.message`.
