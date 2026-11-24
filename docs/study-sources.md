# Study source settings

Open a study and choose **More → Study Source**. Only the study owner or an
enabled superuser can read or change these settings. Ordinary study members
cannot configure sources or study-specific credentials.

Choose one source:

- **None** removes source configuration and study-specific Git credentials.
  The StudySource row is deleted; previous entries remain in the study log.
- **Git repository** accepts an HTTPS URL and an optional branch or tag.
  Leave the ref empty for the default branch. SSH URLs, embedded credentials,
  query strings, fragments, and arbitrary commit IDs are not supported.
- **Local folder** accepts an existing, readable absolute path on the machine
  running JATOS. It does not refer to the browser's computer.

Saving settings does not fetch a repository, copy files, or synchronize study
content. Local-folder creation/import/export and Git creation/pull/push are available.
Archive export remains available as a backup.

## Git credentials

Enter a username and a nonempty password/access token to set or replace study
credentials. An omitted or empty secret keeps the stored password/token. Changing the username
requires a new password/token. An omitted or
empty username leaves the stored username unchanged when no new secret is supplied.
Select **Remove study credentials** to delete them (`removeCredentials: true`
in API requests). Supplying both removal and a nonempty secret is rejected.
The stored study-specific username is returned and displayed; secrets are never returned to the browser. The credential
origin is derived from the repository URL. Removing credentials does not force
anonymous access; use the separate anonymous setting for that.

Changing the repository's origin requires removing or replacing saved
study-specific credentials. Changing a path on the same origin does not.
Anonymous access explicitly selects no authentication; saved study credentials
may remain configured but will not be used when anonymous access is selected.

Switching away from Git removes study-specific credentials and clears Git
settings. Switching to Git clears the folder path. Settings changes are
recorded in the study log after the database transaction commits; the log does
not contain repository URLs, paths, usernames for Git, or tokens.

## Installation settings

In conf/jatos.conf:

```hocon
jatos.isServer = false
jatos.studySources.allowedRoots = ["/home/jatos/studies", "/srv/study-sources"]
```

The default for isServer is false. Set it to true on server installations. It
also controls local-installation UI hints and can be set with JATOS_IS_SERVER.

| isServer | allowedRoots | Local-folder sources |
| --- | --- | --- |
| true | Empty (default) | Disabled |
| false | Empty (default) | Any readable, accessible folder |
| Either | Nonempty | Configured roots and their descendants only |

Git sources are unaffected. Configure absolute roots that exist on the JATOS
host. Validation uses real paths so symlinks cannot escape configured roots.
Saving settings neither modifies nor imports the source folder. Creation
and synchronization apply the same rules and revalidate access when reading.

If configuration changes disallow an existing source, its settings remain
visible and it can still be removed or switched to Git. Folder availability in
the GUI follows the same rules, including allowing configured roots on servers.

Study source settings and credentials are instance-local and are excluded from
ordinary study properties, .jas files, study cloning, and exports.

## API

The GUI uses the same endpoints as external clients:

- `GET /jatos/api/v1/studies/{studyId}/source` reads source settings.
- `PUT /jatos/api/v1/studies/{studyId}/source` saves a JSON configuration body.

Both accept session authentication or a personal API bearer token and require
study ownership or enabled-superuser privileges. Responses use the standard
API envelope (`data` on success, `error` on failure). Session writes require a
CSRF token. Supplying an Authorization header requires token authentication;
requests with that header cannot fall back to a session cookie.

## Local-folder ingestion

`LocalStudySourceIO.stage(path)` now copies source content into an isolated
`study-source-*` directory under JATOS's configured temporary directory. That
temporary directory must already exist. Creation and synchronization use this staging operation. The
caller must first authorize the operation, and must use try-with-resources on
the returned `StagedStudySource` to remove temporary files when finished.

Only one root file ending in `.jas` and the `assets/` directory are copied.
Multiple root `.jas` files are rejected. Other root entries are ignored; `.git`
entries inside assets are also ignored. No archive is created and the external
folder is not changed. The staging layer does not parse `.jas` or validate study
functionality; the import workflow will do the applicable validation later.

A source must contain a root `.jas` file or an `assets/` directory, or both. Folders
containing neither are rejected. Missing properties and missing assets are represented separately. Missing `.jas`
allows default properties on creation or an assets-only update. Missing `assets/`
must preserve existing assets. A present but empty `assets/` means an explicit
empty replacement. Assets are never merged during the later apply step.

Server mode and allowed roots are checked again on every ingestion, independently
of when source settings were saved. A source path resolving through a symlink
must still resolve inside an allowed root. Symbolic links and special files in
selected content are rejected, including dangling links. Assets are copied with
`Files.walkFileTree` without following links, using the same implementation on
Windows, macOS, and Linux. Real-path checks also reject junctions and paths outside
the source. These checks do not prevent concurrent path replacement between
checking and copying: source folders and the processes able to modify them must
be trusted during ingestion. Git ingestion supplies the same staged-source
abstraction.

Temporary storage inside the source's assets is rejected. Failed copies clean up
their staging directory, and repeated closing is safe. Sources should remain
unchanged during copying: staging is not a transactional snapshot of an external
folder, and concurrently edited regular files can yield inconsistent content.

## Create a study from a local folder

Use **My studies → + → From local folder**, or POST JSON to
`/jatos/api/v1/studies`:

```json
{"source":{"type":"LOCAL_FOLDER","localFolderPath":"/absolute/path/to/study"}}
```

The path is on the JATOS host, not necessarily on the browser's computer. The
menu is available when local-folder sources are permitted by the server/roots
policy. Study creation requires the USER role or an enabled SUPERUSER role.

A root `.jas` supplies study, component, and batch properties using the existing
archive-import validation. Without one, creation uses the title `New study`, a
fresh UUID, and the default batch. Missing assets creates an empty internal
assets directory. Existing internal directory names are allocated a suffix;
existing assets are never overwritten. The external folder is copied, not moved.

The initiating user owns the new study. Source database IDs and memberships are
not imported. The local-folder source configuration is saved automatically, ready
for synchronization. Success returns HTTP 201 with the study in `data`.
The GUI redirects to the new study and displays a flash message identifying the imported content.

An existing study UUID returns HTTP 409 with an informational message. No update
is performed; change the source on the existing study instead. Creation failures
roll back the database and delete newly allocated assets. Temporary staging is
cleaned up after success or failure. API validation errors identify the folder
field when staging fails. Session writes require CSRF protection.

## Import implementation

`StudyArchiveImportExport` handles `.jzip` uploads, confirmation, and archive exports.
`LocalFolderStudyImportExport` handles local-folder creation, import/export previews, and content replacement,
using `LocalFolderStudySourceIO` to stage external files. `StudySourceSync` coordinates
authorization, per-study locking, transactions, and logging for existing-study synchronization.
Both import paths use
`StudyImportService` for shared study validation and content application, including
assets-directory reservation and rollback cleanup. Git creation also uses this service.

## Pending archive imports

The GUI keeps one pending archive import per session. Cancel and close buttons send
`DELETE /jatos/import/study`, requiring session authentication and CSRF protection.
Cancellation and completed imports remove staging immediately. Starting another
upload also cleans up the previous pending import.

Abandoned uploads expire 24 hours after preparation finishes. A cleanup job runs
at startup and hourly, scanning only `study-import-*` directories in JATOS's configured
temporary directory. Confirmation of an expired upload asks the user to upload again.
File locks prevent cancellation or expiry cleanup during preparation or confirmation.
For shared temporary storage, the filesystem must support file locks across hosts.
Extracted archive content is kept in a `content/` subdirectory, separate from the
server's lock and expiry state. No new database records or client-facing import IDs
are used. Tabs in the same browser session share the pending import.

## Synchronize a local-folder source

On the study page, **Sync → Import** appears for a configured local-folder source. Study
members and enabled superusers can synchronize an unlocked study; only owners
and enabled superusers can change source settings or credentials. Removing the
source hides Sync. A newly disallowed folder source is rejected by server policy.

The confirmation displays the configured path and allows properties, assets, or
both when present. Missing `.jas` leaves properties unchanged; missing `assets/`
leaves assets unchanged. Selecting an empty `assets/` directory clears all installed
assets. Assets are replaced, never merged. Any supplied `.jas` must have the target
study UUID, even when only assets are selected. A source overlapping the installed
assets cannot be synchronized.

Properties follow archive-import semantics: study and component properties are
updated; removed components become inactive so results are retained. Ownership,
membership, batches, source configuration and the installed assets directory name
are preserved. This adds no study-functionality validation.

API clients use `GET /jatos/api/v1/studies/{id}/source/folder/import` to preview and then
`POST` the same endpoint with JSON:

```json
{"properties":true,"assets":true}
```

IDs and UUIDs are supported. POST reads the currently configured source again and
accepts changes made since preview. Keep source files unchanged during that read;
staging is not an atomic snapshot. A selected content type that has disappeared
is rejected, and any supplied .jas must still match the study UUID. Preview files
are deleted before the response, so confirmation does not require sticky sessions
or pending server state.
Source paths must be accessible on whichever node handles the request.

Updates use a study database lock and a filesystem lock held until transaction
completion and recovery callbacks finish. A competing synchronization returns 409.
Multi-node deployments require shared temporary storage supporting cross-host file
locks, as well as shared assets. Small lock files under `study-sync-locks`
remain in temporary storage; they must not be removed while nodes are running.

New assets are copied into a temporary sibling on the assets filesystem before
replacement. The previous directory is retained until commit and restored on
rollback. Failed restoration retains the backup and logs a recovery error. This
handles ordinary operation and transaction failures; it is not automatic recovery
from a process crash or storage failure. A directory replacement can briefly make
assets unavailable to concurrent readers; avoid synchronizing during active runs.

Successful synchronization writes a study-log entry after commit, recording the
selected content and source folder along with the time and initiating user. The GUI reloads the study and displays a success
message with the applied scope and a reminder to test. Export remains available
for backups. Git sources offer Pull and Push.

## Export to the configured local folder

Study members and enabled superusers can choose **Sync → Export** for an unlocked study.
The modal previews the destination and lets them select properties, assets, or both.
Members can still use **Sync → Import**. Git sources offer Pull and Push.

Export uses the same allowed-root policy as import and requires an existing writable
folder. An empty destination is supported. Properties replace the single existing
root .jas file, preserving its filename; if none exists, export creates study.jas.
Multiple .jas files, an unreadable/invalid .jas, or a different study UUID block
export (including assets-only export). Preview reports the first validation error, and apply checks again.

Selected assets replace the entire destination assets/ directory without merging.
An empty internal assets directory clears the destination assets. A missing internal
assets directory cannot be exported and never clears the destination. Unselected
content and unrelated root files, including .git, remain untouched. Files within the
selected assets/ directory belong to the replacement scope. Internal .git entries
are not copied. Destination entries and outgoing content may not use links or junctions.

The existing archive serializer writes the .jas, excluding source configuration,
credentials, and synchronization history. Export records the selected content and
destination folder in the study log after commit, with the time and initiating user.
It does not change study content. Changing or removing a source preserves the log.

The API uses the existing endpoints:
- GET /jatos/api/v1/studies/{id}/source/folder/export
- POST /jatos/api/v1/studies/{id}/source/folder/export with
  {"properties":true,"assets":true}

Import and export have separate endpoints and share the properties/assets request body;
there is no direction field. GET previews the operation and POST applies it.
Export preview returns an error if destination validation fails. Result flags
describe changes to the endpoint's destination (JATOS for import, the folder for export).

Import and export share the per-study synchronization lock. Export first stages
selected content in a .jatos-export-* temporary directory inside the destination
folder so each replacement move stays on one filesystem. Old entries remain as
backups until commit. Rollback restores replaced entries; restoration failure
retains the backups for manual recovery. The .jas and assets replacements are not
one atomic operation, and process crashes require manual recovery. Keep the folder
unchanged during an export; external editors and other studies using the same folder
are not coordinated by the per-study lock.

Synchronization history is available in **Study log**.

## Create a study from Git

Choose **+ → Clone from Git**, enter the HTTPS repository URL and optionally a branch
or tag. Empty selects the default branch. A name shared by a branch and tag requires
`refs/heads/name` or `refs/tags/name`. Commit-ID input is not supported.

The root layout is the same as for local folders: one `.jas`, `assets/`, or both.
Multiple `.jas` files or neither content type are errors. Without `.jas`, JATOS creates
a study with default properties and a new UUID. An existing UUID is rejected without
changing that study; change its source through **Study Source** instead.

JGit fetches the selected revision into temporary storage without using an external
Git command. JATOS reads Git objects directly and stages only the selected source
content. It does not run hooks, check out a working tree, update submodules, or download
Git LFS objects (LFS pointer files remain pointer files). Links and submodules within
selected content are rejected. Other root files are ignored. The resolved commit is
recorded in the study log on successful creation. The temporary repository and staged
content are removed after success or failure. Saving source settings alone still does
not access the repository.

Authentication is HTTPS username/password or username/token. GitHub requires a token;
other providers may accept passwords or tokens depending on their configuration.
The credentials selected are:

1. Explicit anonymous access, if enabled.
2. Credentials supplied for this study.
3. The new study owner's credentials, if their origin matches the repository.
4. Global credentials, if their origin matches the repository.
5. Anonymous access when no matching credentials are configured.

Selection happens once: failed authentication does not try another credential.
Origins include the HTTPS hostname and effective port; a token is never sent to a
different origin. HTTPS certificate verification stays enabled. Redirects are rejected;
use the repository's final HTTPS clone URL. Host Git configuration headers, cookies,
and TLS overrides are not used for these requests.

Set one global fallback credential in `application.conf` / `jatos.conf`:

```hocon
jatos.studySources.git.credentials.origin = "https://git.example.org:8443"
jatos.studySources.git.credentials.username = "git-user"
jatos.studySources.git.credentials.secret = "password-or-token"
jatos.studySources.git.timeout = 120s
```

Alternatively use `JATOS_GIT_ORIGIN`, `JATOS_GIT_USERNAME`, and `JATOS_GIT_SECRET`.
Global credentials are configuration secrets; user/study credentials remain encrypted
in the database. `timeout` accepts durations such as `30s` or `2m` (minimum `1s`),
and can also be set with `JATOS_STUDY_SOURCES_GIT_TIMEOUT`. It bounds network waits, rather than the total fetch or extraction duration.
Unavailable repositories and authentication
failures return an error without including remote server response bodies or secrets.

The API uses `POST /jatos/api/v1/studies` with JSON:

```json
{"source":{"type":"GIT","gitUrl":"https://git.example.org/group/study.git","gitRef":"main"}}
```

Optional `username` and `secret` fields configure study-specific credentials. Use
`anonymous: true` to disable fallback credentials. Creation returns HTTP 201 with the
study in `data` and a success `message`. Session-authenticated requests require CSRF
protection, just like other creation methods. Source configuration and credentials are
not included in `.jas` files or study exports.


## Push an existing study to Git

Choose **Sync → Push**, review the destination branch, and select properties, assets,
or both. Members may push; changing the configured source still requires the owner
or an enabled superuser. Push uses the same credentials as pull, but those credentials
must also permit writing to the repository. The preview cannot guarantee write access
or bypass protected-branch rules.

JATOS fetches the branch again when pushing. An existing `.jas` must be valid and
have the study's UUID; multiple root `.jas` files are rejected, even for assets-only
pushes. The selected `.jas` is replaced (or `study.jas` is created), and selected
assets replace `assets/` completely. Unselected content and unrelated repository
files are preserved. Source configuration, credentials, and synchronization history
are never included in exported properties.

Push supports existing default and named branches. Tags remain available for pulling
only. JATOS creates a normal child commit and rejects a concurrent remote branch
change instead of forcing or merging. Retry to fetch the latest branch. Empty
repositories need an initial branch commit before JATOS can push.

Author and committer are **JATOS <noreply@jatos.org>**; the commit message is
**Update study content**. No JATOS username, study title, or UUID is added to commit
metadata. The exported files themselves still contain normal study properties.
If the selected content is unchanged, JATOS reports that no new commit was needed.
Successful pushes are recorded in the study log with the initiating user and commit.

API clients can use GET and POST `/jatos/api/v1/studies/{id}/source/git/push` (ID or
UUID). POST accepts `{"properties":true,"assets":true}` without requiring a preview.
The response data contains `studyId`, `commitId`, `changed`, and `message`. Conflicting
synchronizations or a changed remote branch return HTTP 409.

A Git push is external to the database transaction and cannot be rolled back by JATOS.
If a connection fails after sending the update, check the remote branch before retrying.
