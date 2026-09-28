# Crypto Portfolio Hub API Design

Status: Vertical-slice contract; update this document alongside each API implementation.

This document records APIs implemented so far and evolves alongside each vertical slice. JSON responses use camelCase. Errors use the common RFC 9457 Problem Details response with the application's stable `code` and `requestId` fields.

## Authentication

The Backend uses Spring Security Google OpenID Connect Login and a server-side HTTP Session. The browser sends the HttpOnly session cookie automatically. Google access / ID tokens and the Google subject are not returned by the application API. The authenticated OIDC `sub` resolves to the internal `users.id`; request parameters supplied by a client are never used to choose the owner.

OAuth login is enabled only when the Backend is configured with `GOOGLE_OAUTH_ENABLED=true` and Google client credentials. The Frontend starts a browser redirect to the OAuth authorization endpoint; it must not call that endpoint using `fetch`.

The Frontend `GET /auth/google` route redirects the browser to the configured `GOOGLE_OAUTH_AUTHORIZATION_URL`. The Backend OAuth success and failure handlers return to `FRONTEND_BASE_URL` (`http://localhost:3000` in local Compose by default); failure uses `/signin?error=AUTHENTICATION_FAILED`. The local Google callback remains `http://localhost:8080/login/oauth2/code/google`, and Compose publishes Backend port 8080 for that browser callback. Production uses its public Frontend URL and matching provider callback registration.

Frontend API requests use same-origin `/api/*` paths. Next.js rewrites them to `BACKEND_INTERNAL_URL` (`http://backend:8080` in Compose), preserving the browser Session request. The browser does not call a Compose hostname or use cross-origin CORS for API requests.

### `GET /oauth2/authorization/google`

Starts Google's Authorization Code flow and redirects the browser to Google. This is a Spring Security browser route, not a JSON API. The callback is `/login/oauth2/code/google`. The callback creates or refreshes the internal User by Google `sub`, then establishes the Backend Session and redirects to `FRONTEND_BASE_URL`. Login failures redirect to the Frontend `/signin?error=AUTHENTICATION_FAILED` without exposing provider details.

OAuth is disabled by default, so local and test application startup does not require real Google credentials. Enabling it without both client values fails application startup rather than falling back to an unauthenticated mode.

### `GET /api/v1/auth/csrf`

Returns a CSRF token for the current browser Session. It is accessible before login so the Frontend can use it for Logout and future state-changing APIs.

Response `200`:

```json
{
  "headerName": "X-CSRF-TOKEN",
  "token": "<session-bound-token>"
}
```

The Frontend sends the returned token in the named header on state-changing requests. The response is session-specific and must not be treated as an authentication credential.

### `GET /api/v1/auth/me`

Requires an authenticated Google OIDC Session. Resolves the internal User from the authenticated OIDC `sub`; no user ID query or body field is accepted as an ownership selector.

Response `200`:

```json
{
  "id": "<internal-user-uuid>",
  "email": "user@example.com",
  "displayName": "Example User",
  "avatarUrl": "https://example.test/avatar.png"
}
```

`avatarUrl` and `displayName` may be `null`. Google `sub`, tokens, and Connection / Portfolio data are not included.

Unauthenticated response: `401` Problem Details with `code: AUTHENTICATION_REQUIRED`.

## Connections

All Connection endpoints require an authenticated Google Session. `POST` and `DELETE` require the Session's CSRF token. The owner is resolved from the authenticated OIDC `sub`; a client-supplied `userId` is ignored and never determines ownership.

### `GET /api/v1/connections`

Returns the authenticated user's active Connections ordered by creation time, newest first. Soft-deleted Connections are excluded. Each item contains:

```json
{
  "id": "<connection-uuid>",
  "provider": "SOLANA",
  "displayName": "Phantom",
  "maskedIdentifier": "11111…1111",
  "status": "CONNECTED",
  "capabilities": ["BALANCE", "ACTIVITY"]
}
```

`maskedIdentifier` is omitted when the Provider exposes no safe account identifier. In particular, bitbank API Key / Secret are credentials, not account identifiers, and are never masked or returned. `lastAttemptAt` / `lastSuccessAt` are omitted until synchronization runs. This slice does not yet return tracked JPY value.

Capabilities are `BALANCE` and `ACTIVITY` for bitbank and Solana; Hyperliquid adds `POSITION` and `ACCOUNT`.

### `POST /api/v1/connections`

Creates an active Connection and returns `201 Created` with the same safe response shape as the list item. `displayName` is optional (maximum 100 characters); defaults are `bitbank`, `Phantom`, and `Hyperliquid`.

Provider-specific JSON fields:

| `provider` | Required fields | Stored account reference |
| --- | --- | --- |
| `BITBANK` | `apiKey`, `apiSecret` | None. Credentials are independently encrypted with AES-256-GCM and never returned. |
| `SOLANA` | `walletAddress` | Valid Base58 encoding of a 32-byte Solana Address. No Wallet secret or signature is requested. |
| `HYPERLIQUID` | `accountAddress` | `0x` followed by 40 hexadecimal characters; stored lowercase. No Private Key or Agent Secret is requested. |

Example:

```json
{
  "provider": "BITBANK",
  "displayName": "My bitbank",
  "apiKey": "<read-only-api-key>",
  "apiSecret": "<api-secret>"
}
```

The create endpoint performs local validation and persistence only; it does not call a Provider API. `CONNECTED` means the source configuration has been registered. Provider authentication / data availability is checked during later sync, which can update the status. If the credential encryption key is missing or invalid, bitbank creation fails closed with `503 CREDENTIAL_ENCRYPTION_UNAVAILABLE`; no plaintext value is stored.

At most one active bitbank Connection is allowed per User because the Provider does not expose a verified account identifier in the confirmed private API. Active Solana and Hyperliquid duplicates are rejected by `(user, provider, account address)`; a deleted Connection may be added again as a new Connection.

Invalid fields return `400 VALIDATION_ERROR` with safe field names only. An active duplicate returns `409 CONNECTION_ALREADY_EXISTS`. The API never includes rejected field values in the error response.

### `DELETE /api/v1/connections/{connectionId}`

Soft-deletes only a Connection owned by the authenticated User and returns `204 No Content`. An unknown, deleted, or other user's Connection returns `404 RESOURCE_NOT_FOUND`. Deletion is rejected with `409 SYNC_ALREADY_RUNNING` while a Sync Run is active so a Provider operation cannot write Current State after the Connection's state has been removed.

Within the same transaction, it removes that Connection's encrypted Credentials, Current Balances, Current Positions, Provider Account States, and Connection Sync States. The Connection is retained with `status: DISCONNECTED` and `deletedAt`. Activity (including Legs and detail rows) and Sync Run history (including results) remain available to their owner; User-level Portfolio Snapshots are unchanged. Repeating the delete returns `404`.

### Manual Connection Sync (Step 7-1 contract; implementation in Step 7-3)

`POST /api/v1/connections/{connectionId}/sync` requires an authenticated Google Session and CSRF token. It has no request body; the Backend derives the owner from the Session and always uses trigger type `MANUAL`.

The Backend atomically reserves the active Connection, records a `RUNNING` Sync Run, updates supported Capability states to `SYNCING`, and dispatches Provider work asynchronously. Response `202 Accepted`:

```json
{
  "syncRunId": "<sync-run-uuid>",
  "connectionId": "<connection-uuid>",
  "triggerType": "MANUAL",
  "status": "RUNNING",
  "capabilities": ["BALANCE", "ACTIVITY"],
  "startedAt": "2026-09-28T12:00:00Z"
}
```

If any Capability is already `SYNCING` for the Connection, a second request returns `409` Problem Details with `code: SYNC_ALREADY_RUNNING`. An unknown, deleted, or other User's Connection returns `404 RESOURCE_NOT_FOUND`. Provider bodies, credentials, and raw exception messages are never returned.

`GET /api/v1/connections/{connectionId}/sync-runs/{syncRunId}` requires the same authenticated Session and returns the owner-scoped run status and Capability results (`SUCCESS`, `FAILED`, or `SKIPPED`), including safe error categories and optional fetched / persisted record counts. This allows the Frontend to poll an accepted run. A partial run remains `PARTIAL`; successful Capabilities keep their own success timestamps while failed Capabilities retain their previous successful Current State.

### `POST /api/v1/auth/logout`

Requires an authenticated Session and a valid CSRF header. Invalidates the server-side Session and returns `204 No Content`. A subsequent request to a protected endpoint returns `401`.

Missing or invalid CSRF token returns `403` Problem Details. Authentication errors use the common Problem Details contract.

## Error and ownership conventions

- API responses do not expose JPA entities, OAuth tokens, credentials, or provider error bodies.
- User-owned endpoints derive the owner from the Backend-authenticated User. They do not trust a client-supplied `userId`.
- State-changing endpoints require CSRF protection while authentication uses the same-origin Session cookie.
- Add each subsequent API contract with its implementing vertical slice, including request / response DTO, status, authentication, ownership, pagination, and partial / stale / unavailable semantics where applicable.
- Confirmed external Provider contracts and remaining source-specification questions are tracked separately in [provider-specifications.md](./provider-specifications.md); they are not Frontend-facing application API contracts.
