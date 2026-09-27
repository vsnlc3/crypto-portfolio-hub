# Crypto Portfolio Hub API Design

Status: Vertical-slice contract; update this document alongside each API implementation.

This document records APIs implemented so far. It does not predefine future Connection or Portfolio endpoints. JSON responses use camelCase. Errors use the common RFC 9457 Problem Details response with the application's stable `code` and `requestId` fields.

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

### `POST /api/v1/auth/logout`

Requires an authenticated Session and a valid CSRF header. Invalidates the server-side Session and returns `204 No Content`. A subsequent request to a protected endpoint returns `401`.

Missing or invalid CSRF token returns `403` Problem Details. Authentication errors use the common Problem Details contract.

## Error and ownership conventions

- API responses do not expose JPA entities, OAuth tokens, credentials, or provider error bodies.
- User-owned endpoints derive the owner from the Backend-authenticated User. They do not trust a client-supplied `userId`.
- State-changing endpoints require CSRF protection while authentication uses the same-origin Session cookie.
- Add each subsequent API contract with its implementing vertical slice, including request / response DTO, status, authentication, ownership, pagination, and partial / stale / unavailable semantics where applicable.
- Confirmed external Provider contracts and remaining source-specification questions are tracked separately in [provider-specifications.md](./provider-specifications.md); they are not Frontend-facing application API contracts.
