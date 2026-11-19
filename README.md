# Kasina - Real-Time Messaging Platform

Kasina is a full-stack, real-time messaging monorepo built with **Spring Boot 3 (Java 17)**, **React 18 + Vite**, **Zustand**, and **MongoDB**. It provides authenticated 1-to-1 direct messaging, group channels, profile management, and access-controlled file attachments behind an **Nginx** reverse proxy.

---

## Architecture Overview

```
                     +----------------------------------+
                     |       Browser (React 18 SPA)     |
                     |   Tailwind CSS · Zustand · STOMP |
                     +----------------+-----------------+
                                      |
                        HTTPS / HTTP  |  WebSocket (/ws)
                        (Same-Origin) |  (HttpOnly JWT Cookie)
                                      v
                     +----------------------------------+
                     |      Nginx Reverse Proxy         |
                     |  Static SPA · Gzip · Sec Headers |
                     |  /api/*  /uploads/*  /ws proxy   |
                     +----------------+-----------------+
                                      | (kasina-edge network)
                                      v
                     +----------------------------------+
                     |     Spring Boot 3.4 Backend      |
                     | Spring Security · CSRF · STOMP   |
                     | Rate Limiter · Magic-Byte Upload |
                     +----------------+-----------------+
                                      | (kasina-internal network)
                                      v
                     +----------------------------------+
                     |      MongoDB 7 (Auth Enabled)    |
                     | Users · Messages · Channels ·    |
                     | Attachments (Indexed)            |
                     +----------------------------------+
```

### Core Components

1. **Frontend (`frontend/`)**:
   - React 18 SPA bundled with Vite and styled with Tailwind CSS.
   - Zustand state management (`auth-slice`, `chat-slice`) with conversation-scoped race-condition guards (`AbortController` + `activeConversationKey`).
   - Reliable STOMP over SockJS manager (`src/lib/socket-manager.js`) with heartbeats, exponential backoff reconnection, offline message outbox queueing, delivery acknowledgements, automatic retries, and duplicate prevention.
   - Cursor-based infinite history loading with scroll-position preservation, smart auto-scroll, and memoized message rows.
   - Axios API client (`src/lib/api-client.js`) with automatic CSRF double-submit cookie/header handling (`XSRF-TOKEN` / `X-XSRF-TOKEN`).

2. **Backend (`backend/`)**:
   - Spring Boot 3.4 (Java 17) with stateless JWT authentication (HttpOnly `jwt` cookie and `Authorization: Bearer <token>` support).
   - Mandatory startup validation of `JWT_SECRET` (minimum 32 bytes, rejects blank or placeholder values).
   - Double-submit cookie CSRF protection for cookie-authenticated mutations, sliding-window login/signup rate limiting (`429 Too Many Requests` + `Retry-After`), consistent `401` login error responses, and unique MongoDB email indexing.
   - Cryptographically authenticated WebSocket/STOMP layer (`JwtHandshakeInterceptor` + `StompAuthChannelInterceptor`) with strict channel/user subscription authorization, heartbeats, and multi-session tracking (`WebSocketSessionManager` + `WebSocketEventListener`).
   - Private attachment storage with extension allowlist + magic-byte file signature validation (`FileValidationService`) and per-request conversation/channel membership authorization (`FileController`).
   - Cursor-based keyset pagination for direct and channel histories, MongoDB aggregation pipeline for DM contact lists, indexed user channel lookup, and UTC `Instant` timestamps.

3. **Deployment (`docker-compose.yml`, `frontend/nginx.conf`)**:
   - Nginx reverse proxy serving the SPA and proxying `/api/*`, `/uploads/*`, and `/ws` (with WebSocket upgrade support), gzip compression, immutable asset caching, and security headers (`CSP`, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`).
   - Authenticated MongoDB isolated on an `internal: true` Docker network with no host port exposure.
   - Backend isolated from direct host exposure with liveness (`/api/health/live`) and readiness (`/api/health/ready`) health probes.

---

## Environment Configuration

Copy `.env.example` to `.env` and populate strong secrets before running with Docker Compose:

```bash
cp .env.example .env
```

### Environment Variables

| Variable | Description | Required / Default |
| :--- | :--- | :--- |
| `APP_PORT` | Host port exposed by the Nginx reverse proxy | `3000` |
| `MONGO_ROOT_USERNAME` | MongoDB root username (Docker Compose) | **Required** in Docker |
| `MONGO_ROOT_PASSWORD` | MongoDB root password (Docker Compose) | **Required** in Docker |
| `MONGODB_NAME` | MongoDB database name | `chat_app` |
| `MONGODB_CONNECTION_STRING` | MongoDB URI (for local non-Docker backend runs) | `mongodb://localhost:27017/chat_app` |
| `PORT` | Backend HTTP port | `8080` |
| `JWT_SECRET` | HMAC signing secret (minimum 32 bytes; no default fallback) | **Required** |
| `JWT_EXPIRATION_MS` | JWT validity duration in milliseconds | `86400000` (24h) |
| `FRONTEND_URL` | Comma-separated list of allowed CORS & WebSocket origins | **Required** in Docker (`http://localhost:5173` locally) |
| `COOKIE_SECURE` | Set `true` in production over HTTPS | `false` |
| `COOKIE_SAME_SITE` | `SameSite` attribute for JWT and CSRF cookies | `Lax` |
| `TRUST_FORWARDED_HEADERS` | Trust `X-Forwarded-For` / `X-Real-IP` behind Nginx | `true` |
| `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` | Max failed login attempts per IP+email per window | `5` |
| `LOGIN_RATE_LIMIT_MAX_IP_ATTEMPTS` | Max failed login attempts per IP per window | `20` |
| `SIGNUP_RATE_LIMIT_MAX_IP_ATTEMPTS` | Max signup requests per IP per window | `10` |
| `RATE_LIMIT_WINDOW_MS` | Sliding rate-limit window in milliseconds | `900000` (15m) |
| `WEBSOCKET_HEARTBEAT_MS` | STOMP broker heartbeat interval in milliseconds | `10000` |
| `FILE_UPLOAD_DIR` | Directory for uploaded private attachments | `uploads/files` (`/app/uploads/files` in Docker) |
| `VITE_SERVER_URL` | Frontend API base URL (leave empty for same-origin proxy) | `""` |
| `VITE_SOCKET_URL` | Frontend STOMP endpoint URL | `/ws` |
| `VITE_DEV_PROXY_TARGET` | Backend target for Vite dev server proxy | `http://localhost:8080` |

---

## Docker Deployment (Production / Staging)

Ensure `.env` defines `MONGO_ROOT_USERNAME`, `MONGO_ROOT_PASSWORD`, `JWT_SECRET` (32+ random characters), and `FRONTEND_URL` (e.g., `http://localhost:3000` or `https://chat.yourdomain.com`):

```bash
# Build and start MongoDB, Spring Boot backend, and Nginx frontend
docker compose up -d --build

# Check container health status
docker compose ps

# Follow service logs
docker compose logs -f

# Stop services (preserves mongo_data and upload_data volumes)
docker compose down
```

Once healthy, access Kasina at `http://localhost:${APP_PORT:-3000}`. Neither MongoDB nor the Spring Boot backend is exposed directly on host ports; all traffic enters through the Nginx reverse proxy.

> **Production HTTPS note:** Terminate TLS at your edge load balancer / reverse proxy and set `COOKIE_SECURE=true` and `FRONTEND_URL=https://your-domain.com`.

---

## Local Development Setup

### Prerequisites
- Java 17+ JDK
- Node.js 20+
- MongoDB 7 running locally on `localhost:27017`

### 1. Start the Backend

```bash
cd backend
export JWT_SECRET="local-development-secret-key-at-least-32-characters"
export FRONTEND_URL="http://localhost:5173"
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
cd backend
$env:JWT_SECRET="local-development-secret-key-at-least-32-characters"
$env:FRONTEND_URL="http://localhost:5173"
.\mvnw.cmd spring-boot:run
```

### 2. Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

Vite starts on `http://localhost:5173` and automatically proxies `/api`, `/uploads`, and `/ws` (WebSocket) to `http://localhost:8080` so cookies and CSRF tokens work identically to production.

---

## API & WebSocket Reference

### Health Probes
- `GET /api/health/live` — Liveness probe (process responsive).
- `GET /api/health/ready` (and `GET /api/health`) — Readiness probe (verifies MongoDB `ping` and writable attachment storage; returns `503` if degraded).

### Authentication (`/api/auth`)
- `GET /api/auth/csrf` — Issues/refreshes the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` response header.
- `POST /api/auth/signup` — Registers a user (unique email enforced; `409 Conflict` on duplicate; rate limited).
- `POST /api/auth/login` — Authenticates credentials (`401 Unauthorized` with consistent error on failure; `429 Too Many Requests` on rate limit); sets HttpOnly `jwt` cookie.
- `POST /api/auth/logout` — Clears the `jwt` cookie.

### User Profile (`/api/user`)
- `GET /api/user/info` — Current authenticated user profile.
- `POST /api/user/update-profile` — Updates name, avatar color, and validated profile image.
- `DELETE /api/user/remove-profileImage` — Clears profile image.

### Contacts (`/api/contacts`)
- `POST /api/contacts/search?limit=50` — Bounded case-insensitive search excluding current user.
- `GET /api/contacts/dm-list?limit=100` — Recent DM contacts computed via MongoDB aggregation pipeline.
- `GET /api/contacts/get-all-contacts?page=0&limit=50` — Paginated directory listing.
- `POST /api/contacts/find-user` — Lookup user DTO by ID.

### Messages & Private Attachments (`/api/messages`)
- `POST /api/messages/get-messages` — Direct message history. Supports `{ id, cursor, limit, paginated: true }` returning `{ messages, nextCursor, hasMore }`.
- `POST /api/messages/upload-file` — Multipart upload (`file`, optional `recipientId` or `channelId`) validated by extension allowlist and magic-byte file signature inspection.
- `GET /uploads/files/{dateDir}/{fileName}` (also `/api/messages/file/{dateDir}/{fileName}` and `/api/messages/download-file?path=...`) — Authenticated attachment download authorized strictly by conversation/channel membership.

### Channels (`/api/channels`)
- `POST /api/channels/create-channel` — Creates a channel with validated members.
- `GET /api/channels/user-channels` — Indexed lookup of channels where the authenticated user is admin or member.
- `GET /api/channels/get-channel-messages/{channelId}?cursor=...&limit=50&paginated=true` — Channel message history with membership check and cursor pagination.

### WebSocket / STOMP (`/ws`)
- **Authentication**: Validated cryptographically via the HttpOnly `jwt` cookie on handshake (`JwtHandshakeInterceptor`) or `Authorization: Bearer <jwt>` on STOMP `CONNECT`. Unauthenticated or header-spoofed connections are rejected.
- **Outbound destinations**:
  - `/app/chat.sendMessage` — Direct message (`recipient`, `messageType`, `content`/`fileUrl`, `clientMessageId`).
  - `/app/chat.sendChannelMessage` — Channel message (`channelId`, `messageType`, `content`/`fileUrl`, `clientMessageId`).
- **Inbound subscriptions**:
  - `/user/queue/messages` — Private direct messages.
  - `/user/queue/channel-messages` — Authorized channel messages.
  - `/user/queue/ack` — Delivery acknowledgements (`DELIVERED` / `FAILED` keyed by `clientMessageId`).
  - `/user/queue/errors` — Structured validation/processing errors.
  - `/topic/channel/{channelId}` — Channel topic (membership verified on `SUBSCRIBE`).

---

## Testing & Quality Assurance

### Backend Tests

```bash
cd backend
./mvnw test -B
```

Covers JWT startup validation, auth/rate limiting, CSRF protection, WebSocket authentication & subscription authorization, session tracking, magic-byte file validation, private attachment authorization, idempotent message delivery/ACKs, cursor pagination, and readiness checks.

### Frontend Tests, Lint & Build

```bash
cd frontend
npm test
npm run lint
npm run build
```

Covers Zustand chat slice state, conversation-switching race-condition guards, cursor pagination merging, and reliable WebSocket outbox queueing, reconnection, ACK reconciliation, and retry behavior.
