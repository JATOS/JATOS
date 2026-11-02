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
