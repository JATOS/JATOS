# Study source settings

Open a study and choose **More → Study Source**. Only the study owner or an
enabled superuser can read or change these settings. Ordinary study members
cannot configure sources or study-specific credentials.

Choose one source:

- **None** removes source configuration and study-specific Git credentials.
  The StudySource row and its latest synchronization metadata remain.
- **Git repository** accepts an HTTPS URL and an optional branch or tag.
  Leave the ref empty for the default branch. SSH URLs, embedded credentials,
  query strings, fragments, and arbitrary commit IDs are not supported.
- **Local folder** accepts an existing, readable absolute path on the machine
  running JATOS. It does not refer to the browser's computer.

Saving settings does not fetch a repository, copy files, or synchronize study
content. Connection testing, ingestion, credential fallback resolution, and
the Sync action are separate implementation steps. Export remains available
as a backup.

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
Saving settings neither modifies nor imports the source folder. Future creation
and synchronization must apply the same rules and revalidate access when reading.

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
temporary directory must already exist. The creation endpoint below now uses this staging operation. Synchronization is
not connected yet. The
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
be trusted during ingestion. Git ingestion can later supply the same staged-source
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
for future synchronization. Success returns HTTP 201 with the study in `data`.
The GUI displays a reminder to test the study and a link to open it.

An existing study UUID returns HTTP 409 with an informational message. No update
is performed; change the source on the existing study instead. Creation failures
roll back the database and delete newly allocated assets. Temporary staging is
cleaned up after success or failure. API validation errors identify the folder
field when staging fails. Session writes require CSRF protection.

## Import implementation

`StudyArchiveImportExport` handles `.jzip` uploads, confirmation, and archive exports.
`LocalFolderStudyImport` coordinates local-folder creation and source configuration,
using `LocalStudySourceIO` to stage external files. Both import paths use
`StudyImportService` for shared study validation and content application, including
assets-directory reservation and rollback cleanup. Git ingestion can reuse this
service when it is implemented.

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
