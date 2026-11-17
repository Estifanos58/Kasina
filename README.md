# Kasina - Real-Time Messaging Platform

Kasina is a modern, real-time messaging monorepo application engineered with a Spring Boot backend, a React + Vite frontend, and MongoDB for scalable document persistence. It supports authenticated direct 1-to-1 messaging, group channels, profile personalization, and secure file attachments.

---

## Architecture Overview

```
                      +-----------------------------+
                      |   React 18 + Vite Client    |
                      | (Tailwind CSS, Zustand, UI) |
                      +--------------+--------------+
                                     |
                    HTTP / REST      |      WebSocket (STOMP)
                    (JWT Cookie/Auth)|      (/ws endpoint)
                                     v
                      +-----------------------------+
                      |    Spring Boot 3 Backend    |
                      |   (Spring Security + STOMP) |
                      +--------------+--------------+
                                     |
                                     v
                      +-----------------------------+
                      |      MongoDB Database       |
                      | (Users, Messages, Channels) |
                      +-----------------------------+
```

### Components

1. **Frontend (`frontend/`)**:
   - Single Page Application built with React 18 and Vite.
   - Global state management using Zustand slices (`chat-slice`, `auth-slice`).
   - STOMP WebSocket client over SockJS with automatic exponential-backoff reconnection.
   - Dynamic message sorting, duplicate prevention, and active conversation filtering.

2. **Backend (`backend/`)**:
   - Spring Boot 3.4 application running Java 17.
   - Stateless JWT authentication via `JwtAuthenticationFilter` with support for both HttpOnly cookies and `Authorization: Bearer <token>` headers.
   - WebSocket message broker configuration with channel authorization interceptors and multi-session user session tracking (`WebSocketSessionManager`).
   - Secure REST controllers utilizing structured Data Transfer Objects (DTOs) to eliminate entity/password leakage.
   - Decoupled message storage with compound MongoDB indexes (`sender_recipient_idx`, `channel_timestamp_idx`).
   - Centralized exception handling (`@RestControllerAdvice`).

3. **Storage & Infrastructure (`docker-compose.yml`)**:
   - MongoDB container with persistent data volume (`mongo_data`).
   - Persistent volume for uploaded user files and media (`upload_data`).
   - Multi-stage Dockerfiles with unprivileged execution users (`appuser`) and container healthchecks.

---

## Security Architecture

Kasina implements enterprise-grade security controls:

- **Identity Derivation**: Client-supplied sender IDs are never trusted. All outbound messages and actions derive the sender identity from the authenticated security principal.
- **WebSocket Access Control**:
  - `CONNECT` frames require valid JWT authentication.
  - Channel subscription (`/topic/channel/{id}`) verifies active channel membership before granting access (HTTP 403 / STOMP error if unauthorized).
  - Direct messages are routed strictly through authenticated user queues (`/user/queue/messages`), preventing eavesdropping on public topics.
- **Zero Credential Exposure**: Response DTOs (`UserDTO`, `UserInfoResponse`, `ContactResponseDTO`, `ChannelResponseDTO`, `MessageResponseDTO`) sanitize sensitive fields. Database entities and password hashes are never returned across the wire.
- **Hardened File Storage**:
  - Validates upload size (default max 20MB) and content type.
  - Rejects dangerous file extensions (`.exe`, `.sh`, `.bat`, `.cmd`, `.php`, `.js`, etc.).
  - Generates secure UUID-based file paths to completely prevent path traversal (`../`) attacks.
- **Network Boundaries & CORS**:
  - Configurable CORS allowed origins with credential support (`Access-Control-Allow-Credentials: true`).
  - Secure cookie attributes (`HttpOnly`, `SameSite=Lax`, configurable `Secure` flag).

---

## Environment Configuration

Copy the example environment files to configure your environment:

```bash
cp .env.example .env
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
```

### Backend Configuration (`backend/.env` or system environment)

| Variable | Description | Default |
| :--- | :--- | :--- |
| `PORT` | Backend server port | `8080` |
| `MONGODB_CONNECTION_STRING` | MongoDB URI | `mongodb://localhost:27017/chat_app` |
| `JWT_SECRET` | 256-bit+ HMAC-SHA key for signing JWTs | *(Required)* |
| `JWT_EXPIRATION_MS` | JWT token validity in milliseconds | `86400000` (24 hours) |
| `FRONTEND_URL` | Comma-separated list of allowed CORS origins | `http://localhost:5173,http://localhost:3000` |
| `UPLOAD_DIR` | Absolute or relative path for attachment storage | `uploads` |
| `COOKIE_SECURE` | Set `true` in production with HTTPS | `false` |

### Frontend Configuration (`frontend/.env` or system environment)

| Variable | Description | Default |
| :--- | :--- | :--- |
| `VITE_SERVER_URL` | Base HTTP URL for the Spring Boot backend | `http://localhost:8080` |
| `VITE_SOCKET_URL` | Base WebSocket STOMP endpoint URL | `http://localhost:8080/ws` |

---

## Running with Docker Compose

To build and start the entire stack (MongoDB, Backend, and Frontend) in production containers:

```bash
# Build and run containers in detached mode
docker compose up -d --build

# View container logs
docker compose logs -f

# Check container health status
docker compose ps

# Tear down containers and networks (preserving persistent volumes)
docker compose down
```

Services will be accessible at:
- **Frontend SPA**: `http://localhost:3000`
- **Backend API**: `http://localhost:8080`
- **MongoDB**: `localhost:27017`

---

## Local Development Setup

### Prerequisites
- Java 17+ JDK
- Node.js 18+ (tested on Node 20 and Node 24)
- MongoDB instance running locally on `localhost:27017` or accessible via URI

### 1. Backend Service

```bash
cd backend

# On Linux / macOS:
./mvnw clean spring-boot:run

# On Windows:
.\mvnw.cmd clean spring-boot:run
```

The backend server starts on port `8080`.

### 2. Frontend Client

```bash
cd frontend

# Install project dependencies
npm install

# Start Vite development server with HMR
npm run dev
```

The client application will start on `http://localhost:5173`.

---

## Testing & Quality Assurance

### Backend Unit & Integration Tests

The backend test suite covers authentication, JWT security, private messaging, channel access authorization, and secure file uploads.

```bash
cd backend

# Execute all tests using the Maven wrapper
.\mvnw.cmd test
```

### Frontend Linting & Unit Tests

```bash
cd frontend

# Run ESLint across code and components
npm run lint

# Execute unit tests (verifying message sorting, deduplication, and store actions)
npm test

# Produce production build bundle
npm run build
```

---

## Continuous Integration (CI)

A GitHub Actions workflow is located at `.github/workflows/ci.yml`. On every pull request and push to `main`:
1. Compiles and executes backend unit & integration tests on JDK 17.
2. Lints frontend source code with ESLint, runs frontend tests, and compiles the production build.

---

## REST API Reference

### Authentication (`/api/auth`)
- `POST /api/auth/signup` - Register a new user account.
- `POST /api/auth/login` - Authenticate with email/password; returns JWT and sets session cookie.
- `POST /api/auth/logout` - Clear user session cookie.

### User Profile (`/api/user`)
- `GET /api/user/info` - Get current authenticated user profile.
- `POST /api/user/update-profile` - Update user details (name, color, profile setup status).
- `POST /api/user/add-profile-image` - Upload user profile avatar.
- `DELETE /api/user/remove-profileImage` - Remove current user avatar.

### Contacts (`/api/contacts`)
- `POST /api/contacts/search` - Search contacts by name or email.
- `GET /api/contacts/dm-list` - Retrieve recent direct message contacts sorted chronologically.
- `GET /api/contacts/get-all-contacts` - Retrieve contact list formatted for new chats.
- `POST /api/contacts/find-user` - Lookup contact details by user ID.

### Messages & Attachments (`/api/messages`)
- `POST /api/messages/get-messages` - Retrieve direct messages between authenticated user and a recipient.
- `POST /api/messages/upload-file` - Upload file attachment (returns sanitized file path).
- `GET /api/messages/file/**` - Download or view uploaded attachment.

### Channels (`/api/channels`)
- `POST /api/channels/create-channel` - Create a new channel with members.
- `GET /api/channels/user-channels` - List channels where authenticated user is a member.
- `POST /api/channels/get-channel-messages` - Retrieve message history for a channel (verifies membership).
