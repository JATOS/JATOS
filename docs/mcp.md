# Embedded MCP server

The GUI module exposes forty-six tools through `/jatos/mcp`, grouped like the API routes:

| Concern | Tool | Arguments |
| --- | --- | --- |
| Current token | `get_current_token` | None |
| Batches | `list_batches` | `study_id` |
| Batches | `get_batch` | `batch_id` |
| Batches | `get_batch_session` | `batch_id` |
| Batches | `update_batch_session` | `batch_id`, `version`, `properties` |
| Batches | `create_batch` | `study_id`, `properties` |
| Batches | `set_batch_active` | `batch_id`, `properties` (`active`) |
| Batches | `update_batch` | `batch_id`, `properties` |
| Components | `list_components` | `study_id` |
| Components | `get_component` | `component_id` |
| Components | `create_component` | `study_id`, `properties` |
| Components | `set_component_active` | `component_id`, `properties` (`active`) |
| Components | `update_component` | `component_id`, `properties` |
| Studies | `list_studies` | None |
| Studies | `get_study` | `study_id`, optional `with_components`, `with_batches` |
| Studies | `create_study` | `properties` |
| Studies | `set_study_active` | `study_id`, `properties` (`active`) |
| Studies | `update_study` | `study_id`, `properties` |
| Studies | `study_import` | Optional `keep_properties`, `keep_assets`, `keep_current_assets_name`, `rename_assets` |
| Studies | `export_study` | `study_id` |
| Studies | `download_study_log` | `study_id` |
| Studies | `get_study_log` | `study_id`, optional `entry_limit` (1–1000, default 100) |
| Study assets | `list_study_assets` | `study_id` |
| Study assets | `write_study_asset` | `study_id`, `filepath`, `content` |
| Study assets | `upload_study_asset` | `study_id`, `filepath` |
| Study assets | `download_study_asset` | `study_id`, `filepath` |
| Study assets | `read_study_asset` | `study_id`, `filepath` |
| Study membership | `list_study_members` | `study_id` |
| Study membership | `add_study_member` | `study_id`, `user_id` |
| Study membership | `remove_study_member` | `study_id`, `user_id` |
| Groups | `list_groups` | `batch_id` |
| Groups | `get_group_session` | `group_id` |
| Groups | `update_group_session` | `group_id`, `version`, `properties` |
| Study codes | `get_or_generate_study_codes` | `study_id`, `properties` (`type`, optional `batchId`, `amount`, `comment`) |
| Study codes | `get_study_code` | `code` |
| Study codes | `set_study_code_active` | `code`, `properties` (`active`) |
| Results | `get_result_metadata` | `properties` (result selectors) |
| Results | `get_result_data` | `properties` (result selectors) |
| Results | `get_result_file` | `component_result_id`, `filename` |
| Results | `get_result_files` | `properties` (result selectors) |
| Results | `get_results` | `properties` (result selectors) |
| Users | `list_users` | none (ADMIN permission required) |
| Users | `add_user` | `properties` (ADMIN permission required) |
| Users | `get_user` | `user_id` (ADMIN or self) |
| Users | `set_user_active` | `user_id`, `properties` (`active`) |
| Users | `update_user` | `user_id`, `properties` (ADMIN or self) |

Tools use the existing JATOS HTTP API with the caller's personal API token; result
export tools return authenticated request instructions for direct downloads.
API permissions, study membership and token expiry/revocation still apply.

Asset paths are unencoded and relative to the study assets directory. Traversal,
absolute paths, percent escapes, backslashes and control characters are rejected.
Any filename or extension is accepted, including files without an extension.
The entire file must decode as valid UTF-8; this does not guarantee it was intended as text.
Control characters in file contents are preserved. Text is
returned as a string under `data`; it is never executed or rendered. The configured
response-size limit applies to all reads, including assets and logs.

Study logs return the API's JSON array in newest-first order, including any API
truncation notice. Batch sessions retain the API's session data and version fields.

## Creating and updating

`properties` is a JSON object using the API's field names; discovery describes the
allowed fields. Creation requires `title`. Updates send only supplied fields, including
explicit nulls for nullable fields. API validation, roles, study locks and membership
remain authoritative. The only DELETE operation exposed removes study membership; it does not delete the user or study.

Clone tools are not exposed. Study export preserves the study UUID.

Read tools advertise `readOnlyHint=true`; write tools advertise false. Updates and membership removal advertise `destructiveHint=true` because they can
replace data or revoke access.
Writes are not advertised as idempotent and are never retried automatically. If a
write times out or its response cannot be read, inspect the study before retrying:
the API may already have applied the change.

## File uploads

`write_study_asset` writes supplied `content` directly as UTF-8 text through the
asset API. It creates missing parent directories and overwrites existing files;
empty content truncates the file. It requires USER permission and study access.
The entire JSON request must fit `jatos.mcp.maxRequestSize`, and the uploaded file
must fit `jatos.studyAssets.maxFileSize`. Use `upload_study_asset` for large or
binary files instead.

`study_import` and `upload_study_asset` return HTTP upload instructions; calling them
does not transfer files or mutate JATOS. Their read-only annotations describe this
preparation step. The subsequent HTTP POST can overwrite existing content.

`study_import` returns an `application/zip` request for a raw .jzip file body.
Optional `keep_properties` and `keep_assets` default to false (overwrite);
`keep_current_assets_name` and `rename_assets` default to true, matching the API.
`upload_study_asset` returns a multipart request with file field `studyAssetsFile`;
the HTTP client must generate the multipart boundary. Existing destination files
are overwritten. Each response includes `maxFileSizeBytes` from the configured API
limit. File bodies bypass MCP size limits. Neither tool accepts file bytes or Base64.

Both transfers require USER permission, with study access checked by the API. Read `jatos://docs/api-usage` for endpoints, authentication,
and streaming transfer guidance. Clients need their own HTTP capability and credentials.

## Study archives

`export_study` first reads study properties through the API to check access, then
returns `url`, `filename`, `mimeType` and authentication instructions. The URL is
based on the incoming MCP request's host/scheme and the configured context path;
configure Play's trusted proxy settings correctly when using a reverse proxy.
The link targets `GET /jatos/api/v1/studies/:id`, which downloads the archive after
normal API authorization (USER permission is required). The URL contains no token;
download with an Authorization header or an authenticated JATOS browser session.
No archive is downloaded into the MCP response, so export is not limited by the
MCP response-size bound for archive bytes.

## Enable and connect

Enable the existing JATOS API and set this in `conf/jatos.conf`, then restart:

```hocon
jatos.mcp.enabled = true
```

Alternatively set `JATOS_MCP_ENABLED=true`. MCP is disabled by default. With a
URL prefix such as `/research/`, the endpoint is `/research/jatos/mcp`.

Configure Codex with the endpoint and a token environment variable:

```toml
[mcp_servers.jatos]
url = "http://localhost:9000/jatos/mcp"
bearer_token_env_var = "JATOS_API_TOKEN"
```

Make `JATOS_API_TOKEN` available in Codex's environment. Use HTTPS for connections
to a remote installation. No separate MCP process, port, or additional runtime
is required. Reverse proxies must forward the MCP path, POST/GET/DELETE methods,
and the Authorization, Accept, Content-Type, MCP-Protocol-Version, Mcp-Method
and Mcp-Name headers.

Ask the client: “List my studies, then show the components of study X.”
IDs are positive integers. `list_studies` returns compact summaries; the other
tools return API data under `structuredContent.data` for June 2025 and
newer revisions, with a text JSON copy for client compatibility. March 2025
clients receive the text JSON representation only. Tools do not access participant result data.

## Internal HTTP connection

The adapter connects to `http://<JATOS HTTP address>:<JATOS HTTP port>/<context>/jatos/api/v1`.
The host comes from `Common.getJatosHttpAddress()`, which normalizes wildcard binds
`0.0.0.0` and `::` to `127.0.0.1` and `::1`, respectively. Explicit addresses are preserved,
with IPv6 literals bracketed for the URL. Request headers cannot override the host.
Endpoint templates are fixed. Asset filepaths are validated and URL-encoded before being appended.
The configured `play.http.context` is included. Redirects are never followed.
The internal port comes from `play.server.http.port` via `Common.getJatosHttpPort()`.
HTTPS-only internal listeners are not supported in this first version.

After checking that MCP is enabled, the Origin is allowed and a bearer header is present,
every request authenticates through `/admin/token` before HTTP or MCP message validation.
This includes discovery, ping, notifications, malformed messages and unsupported methods.
Valid tool calls then make a second API request, whose endpoint enforces authentication and
permissions again. Authentication failures remain HTTP 401 with `WWW-Authenticate: Bearer`.
No tokens, authenticated sessions or results
are cached. Session cookies are not accepted or forwarded. Disabling the API
also prevents MCP authentication. This first version supports manually configured
bearer tokens; it does not implement OAuth discovery or login.

Native clients normally omit Origin. Requests carrying Origin are rejected unless
it exactly matches an entry in `jatos.mcp.allowedOrigins`, for example:

```hocon
jatos.mcp.allowedOrigins = ["https://jatos.example.org"]
```

This is an origin check, not a CORS configuration; browser clients may also need
appropriate existing Play CORS settings.

## Protocol and limits

This is a deliberately small Play-native implementation of stateless Streamable
HTTP, supporting four protocol revisions on the same endpoint:

| Revision | Discovery | Request format |
| --- | --- | --- |
| `2025-03-26` | `initialize`, then `notifications/initialized` | Legacy; omitted version headers default to this revision |
| `2025-06-18` | `initialize`, then `notifications/initialized` | Legacy with `MCP-Protocol-Version` header |
| `2025-11-25` | `initialize`, then `notifications/initialized` | Legacy with `MCP-Protocol-Version` header |
| `2026-07-28` | Optional `server/discover`; no handshake required | Per-request metadata and matching HTTP headers |

Legacy initialization only negotiates legacy revisions, falling back to
`2025-11-25` if the proposed revision is unsupported by that handshake.
Modern discovery lists all four supported revisions. Modern calls must include
`params._meta["io.modelcontextprotocol/protocolVersion"]` and an object at
`params._meta["io.modelcontextprotocol/clientCapabilities"]`. The HTTP
`MCP-Protocol-Version` and `Mcp-Method` headers must match the body; `tools/call`
also requires a matching `Mcp-Name`. Standard Base64-encoded names are supported.
Missing metadata yields HTTP 400 / JSON-RPC -32602. Missing or mismatched headers
yield HTTP 400 / -32020; unsupported versions yield HTTP 400 / -32022 with a list
of supported versions. Unknown modern RPC methods yield HTTP 404 / -32601.

Modern results include `resultType: "complete"` and server identity metadata.
Legacy responses omit these fields. March 2025 tool results contain text JSON
only, because structured tool output was introduced in June 2025.

All revisions expose the same forty-six tools, API usage resource, and ping. Responses are JSON; GET and
DELETE return 405 after authentication. There are no session IDs, SSE streams,
prompts, subscriptions, server-initiated requests or background tasks.
No legacy HTTP+SSE transport is offered. Optional protocol capabilities beyond
tools and resources are not advertised.

Protocol references: [modern versioning](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning),
[modern HTTP transport](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http),
and [legacy initialization](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle).

Input messages default to a 64 KiB limit, configurable with `jatos.mcp.maxRequestSize`. API calls default to a 10-second timeout and
1 MiB response limit, configurable with `jatos.mcp.apiTimeout` and
`jatos.mcp.maxResponseSize`. Oversized API responses fail explicitly, rather than
silently truncating data. Study lists are summarized after reading the bounded
API response, so a large installation may need a higher limit. Pagination is a
future enhancement. API failures during tool execution become MCP tool errors;
authentication failures return HTTP 401/403. Transport failures during token
validation return HTTP 502 without exposing credentials or exception details.

## Verification

```sh
sbt 'gui/testOnly controllers.gui.mcp.McpTest services.gui.mcp.*Test' \
    'testOnly controllers.gui.mcp.McpIntegrationTest'
```

The integration tests use real HTTP, API token authentication and isolated H2
databases at both root and prefixed URLs. They exercise the read tools across all four revisions, plus create/update operations,
archive round trips, access isolation, response limits and token revocation.
The legacy connection has also been exercised with Codex. Modern native-client
compatibility remains a separate check after restarting JATOS with this update.

### API failure reporting

Expected failures have controlled messages for connection errors, timeouts, response-size
limits, malformed JSON and invalid UTF-8 assets. Tool execution returns these as
`isError: true`; authentication-stage failures return HTTP 502. HTTP error statuses
are preserved even when their bodies are empty, non-JSON or cannot be fully read
within the configured limits. Such bodies use a safe fallback message.

Unexpected processing failures return HTTP 500 with JSON-RPC `INTERNAL_ERROR` and
retain a valid request ID once the message has been parsed. Before parsing, the ID
is null. Expected API failures are logged once by the API client; unexpected failures
are logged once by the controller with the exception type and stack trace. Raw exception
messages, response bodies and authorization headers are excluded from these logs.

## API usage resource

The server advertises a read-only resource, `jatos://docs/api-usage` (`text/markdown`).
Discover it with `resources/list` and retrieve it with `resources/read`, passing
`{"uri":"jatos://docs/api-usage"}`. The same token authentication as tools applies.
The resource describes the deployment's API base path, authentication, response
formats, the installation’s `/openapi.yaml` route (including its deployment prefix),
and streaming HTTP transfers for archives and assets,
including the configured API file-size limits. It never returns the caller's token.
Clients still need their own HTTP execution capability and credentials to perform
these transfers. Resource subscriptions and pagination are not supported.

## Participation links

`get_or_generate_study_codes` uses the API's POST endpoint. PersonalSingle and
PersonalMultiple create new codes; GeneralSingle, GeneralMultiple and MTurk reuse
existing links or create them if needed. `properties.type` is required. Omit
`batchId` to use the default batch; `amount` defaults to 1 (maximum 1000), and
`comment` is limited to 255 characters. Amount and comment apply to personal types.
Generation requires USER permission and must not be blindly retried after a timeout.

Pass a returned code to `get_study_code` to inspect its settings and participation
paths. The paths include the deployment prefix and resolve against the public
JATOS origin. To disable or enable the link, call `set_study_code_active` with
`{"code":"...","properties":{"active":false}}` or `active:true`. Updates require
USER permission. API study membership checks apply to all three tools.

## Result inspection and downloads

All four result tools accept a required `properties` object containing at least
one of `studyIds`, `studyUuids`, `componentIds`, `componentUuids`, `studyResultIds`,
`componentResultIds`, `batchIds`, or `groupIds`. Each accepts a single ID/UUID or a
non-empty array. Numeric IDs must be positive. Range strings are not supported by
these tools. Multiple selectors are combined as a **union**, not an intersection.
JATOS omits missing results. Its endpoints may omit inaccessible results or return
an access error; the MCP tools preserve that behavior.

`get_result_metadata` returns JSON metadata; `get_result_data` returns text with
one result per line. Both are bounded by the MCP response limit: narrow selections
or use `get_results` for larger exports. Metadata includes uploaded-file information.

`get_results` (metadata, data and files) and `get_result_files` (files only) return
ZIP download instructions: `url`, `method: POST`, `contentType: application/json`,
`body`, `mimeType`, and authentication guidance. These are not ordinary browser GET
links. Use an HTTP client to POST the supplied JSON with the caller's API token or
session. Credentials are never embedded. Permission filtering occurs when the API
handles that download; obtaining instructions does not confirm access to selected
results. Archives do not pass through MCP and are not constrained by its size limit.
All four tools are read-only, despite the API's use of POST.

## Groups and shared sessions

`list_groups` returns all groups in one batch, including active and finished groups.
To inspect a whole group study, call `list_batches` and list the groups of each batch.
An empty batch returns an empty list.

`get_batch_session` and `get_group_session` return `sessionData` and `version`.
`update_batch_session` and `update_group_session` accept the corresponding ID,
the `version` just read, and `properties` containing the **entire replacement JSON
object**. Omitted keys are removed; `{}` clears the session. Unlike the API's
optional version parameter, these tools require a version to prevent accidental
concurrent overwrites. A stale version returns the API's conflict error; reread
and reconcile before trying again. Successful updates return the new version.
Updates require USER permission; API access checks apply to both reads and writes.

## Study membership

`list_users` returns only numeric user IDs and requires ADMIN permission, as does
the underlying API. `list_study_members` returns `{id, uuid, members}` for a study.
`add_study_member` and `remove_study_member` accept `study_id` and `user_id` and
return the updated membership object. They require USER permission and access to
the study. Adding an existing member or removing a non-member leaves membership
unchanged. Removal revokes membership-based access; API safeguards remain authoritative.
These tools do not create or delete user accounts.

### User accounts

`add_user` accepts `properties` with required `username` and `name`, optional `email`,
`authMethod` (`DB` by default or `LDAP`) and `role` (`USER` by default or `VIEWER`).
DB accounts require a `password` satisfying the server's password policy.
`update_user` accepts only `name`, `email`, `password` and `active`; omitted fields
are unchanged. Roles, usernames and authentication methods cannot be changed by
this tool. API rules prevent self-deactivation and protect the built-in admin account.
Passwords are not returned, but MCP clients may retain supplied passwords in their
conversation or tool-call history.

### Activation and single result files

- `set_study_active(study_id, properties)` and `set_user_active(user_id, properties)` require ADMIN permission in MCP.
- `set_component_active(component_id, properties)` and `set_batch_active(batch_id, properties)` use the API's USER and study-access checks.
- All four accept exactly `properties: {"active": true}` or `properties: {"active": false}` and return the updated API data.
- Existing general update tools retain their existing API permissions.
- `get_result_file(component_result_id, filename)` returns an authenticated GET download URL for a single file, without loading its bytes into MCP. Use the exact filename from result metadata; directories are not accepted. Access and existence are checked by the API when downloaded.

### Read conveniences

`get_study` accepts optional `with_components` and `with_batches` booleans (both
false by default). Enabled flags include the full properties instead of the
usual ID/UUID references. They do not include files, results or session data.

`download_study_asset` and `download_study_log` return authenticated GET download
instructions. The asset can be any file format; the log download contains the
complete log. Files are transferred directly through the API, outside MCP response
size limits. The API checks access and existence at download time. Credentials
are not embedded in the URLs. `export_study` remains unchanged.

`get_current_token` returns the caller's token metadata, including owner, roles,
expiry and active status. It does not return the token secret or hash.
