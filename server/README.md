# Lovanta API

This Express API is the server-side boundary between the Android chatter app, the subscriber PWA, and MongoDB. Clients must never connect directly to MongoDB.

## Local setup

1. Install Node.js 22.18 or newer.
2. Install MongoDB configured as a replica set (or use MongoDB Atlas).
3. Copy `.env.example` to `.env`, then set `MONGODB_URI` and a private `JWT_SECRET` of at least 32 characters.
4. Run `npm install`, `npm run build`, and `npm run dev`.

The `.env` file is local-only and must not be committed. A MongoDB replica set is required for the transactional message-send endpoint.

## Initial API

- `POST /api/v1/auth/login` — authenticate an active worker and receive a 12-hour bearer token.
- `GET /api/v1/auth/me` — return the authenticated worker.
- `GET /api/v1/models` — list model accounts assigned to that worker.
- `GET /api/v1/models/:modelAccountId/conversations` — list active conversations for an assigned account.
- `GET /api/v1/conversations/:conversationId/messages` — list messages if the worker is assigned to that conversation's model account.
- `POST /api/v1/conversations/:conversationId/messages` — send a message if the worker is currently assigned; the message records the worker identity.
- `GET /health` — API process health.

## Staff and admin API

- `POST /api/staff/auth/login` — centralized staff login; returns role permissions and redirect.
- `POST /api/staff/auth/logout` — end the current bearer session.
- `POST /api/staff/auth/password/change` — change the authenticated staff password.
- `GET /api/v1/admin/dashboard` — real MongoDB overview metrics.
- `/api/v1/admin/models` — model management.
- `/api/v1/admin/chatters` — staff account management.
- `/api/v1/admin/assignments` — model-to-chatter assignments.
- `/api/v1/admin/subscribers`, `/conversations`, `/ppv`, `/transactions`, `/commissions`, `/audit` — management and reporting.
- `GET /api/v1/admin/reports/:type.csv` — CSV export for supported report types.

The shared Android app uses `API_BASE_URL` as a Gradle property. The default debug URL
is `http://10.0.2.2:4000` for an Android emulator connecting to a local API; use the
deployed HTTPS API URL when building for a device or release.

To create the first administrator, set `BOOTSTRAP_ADMIN_EMAIL` in the server
environment, then run `npm run admin:bootstrap` from `server`. It creates a random
temporary password, prints it once, and requires a password change on first sign-in.
The command does nothing if an admin already exists.

Worker and assignment provisioning is intentionally not exposed as public registration. It should be added as an admin-only workflow after the first trusted admin is provisioned.
