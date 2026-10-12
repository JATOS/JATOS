# Embedded MCP server (initial implementation)

The GUI module exposes ten read-only tools through `/jatos/mcp`, grouped like the API routes:

| Concern | Tool | Arguments |
| --- | --- | --- |
| Batches | `list_batches` | `study_id` |
| Batches | `get_batch` | `batch_id` |
| Batches | `get_batch_session` | `batch_id` |
| Components | `list_components` | `study_id` |
| Components | `get_component` | `component_id` |
| Studies | `list_studies` | None |
| Studies | `get_study` | `study_id` |
| Studies | `get_study_log` | `study_id`, optional `entry_limit` (1–1000, default 100) |
| Study assets | `list_study_assets` | `study_id` |
| Study assets | `read_study_asset` | `study_id`, `filepath` |

Each tool calls the existing JATOS HTTP API using the caller's personal API token.
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

All revisions expose the same ten tools and ping. Responses are JSON; GET and
DELETE return 405 after authentication. There are no session IDs, SSE streams,
prompts, resources, subscriptions, server-initiated requests or background tasks.
No legacy HTTP+SSE transport is offered. Optional protocol capabilities beyond
these read-only tools are not advertised.

Protocol references: [modern versioning](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning),
[modern HTTP transport](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http),
and [legacy initialization](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle).

Input messages are limited to 64 KiB. API calls default to a 10-second timeout and
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
databases at both root and prefixed URLs. They exercise all ten tools across all
four revisions, plus access isolation, response limits and token revocation.
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
