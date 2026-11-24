# Lovanta API

This Express API is the server-side boundary between the Android chatter app, the subscriber PWA, and MongoDB. Clients must never connect directly to MongoDB.

## Local setup

1. Install Node.js 22.18 or newer.
2. Install MongoDB configured as a replica set (or use MongoDB Atlas).
3. Copy `.env.example` to `.env`, then set `MONGODB_URI` and a private `JWT_SECRET` of at least 32 characters.
4. Run `npm install`, `npm run build`, and `npm run dev`.

The `.env` file is local-only and must not be committed. A MongoDB replica set is required for the transactional message-send endpoint.

## Chatter workspace API

- `GET /api/v1/auth/me` — return the authenticated worker.
- `GET /api/v1/models` — list model accounts assigned to that worker.
- `GET /api/v1/models/:modelAccountId/conversations` — list active conversations for an assigned account.
- `GET /api/v1/conversations/:conversationId/messages` — list messages if the worker is assigned to that conversation's model account.
- `POST /api/v1/conversations/:conversationId/messages` — send a message if the worker is currently assigned; the message records the worker identity.
- `GET /health` — API process health.

## Staff and admin API

- `POST /api/staff/auth/login` — the single staff login endpoint for administrators, supervisors, and chatters; accepts an email or username and returns the database-derived role, permissions, and redirect.
- `POST /api/staff/auth/logout` — end the current bearer session.
- `POST /api/staff/auth/password/change` — change the authenticated staff password.
- `GET /api/v1/admin/dashboard` — management counts, recent recruitment/actions, and reporting aggregates.
- `/api/v1/admin/models` — model management, including name/username/Model ID search and status filtering. MongoDB `_id` is the permanent Model ID; responses also expose it as `modelId`.
- `GET /api/v1/admin/models/:id` — model profile and active chatter assignments.
- `POST /api/v1/admin/chatters` — create a chatter with a generated login ID and one-time random password.
- `GET /api/v1/admin/chatters` — search and filter chatter accounts by `ACTIVE`, `DISABLED`, `BANNED`, or `ARCHIVED`.
- `GET /api/v1/admin/chatters/:id` — chatter profile, assigned models, and administrative history.
- `PATCH /api/v1/admin/chatters/:id/status` — disable or reactivate a chatter.
- `POST /api/v1/admin/chatters/:id/ban` and `/unban` — reason-required account actions.
- `POST /api/v1/admin/chatters/:id/archive`, `/password-reset`, and `/assignments` — soft archive, one-time password reset, and model assignment.
- `/api/v1/admin/assignments` — model-to-chatter assignments.
- `/api/v1/admin/subscribers`, `/conversations`, `/ppv`, `/transactions`, `/commissions`, `/audit` — management and reporting.
- `GET /api/v1/admin/reports/:type.csv` — CSV export for supported report types.

Admin-only chatter creation generates the next available `chatterN@gmail.com` login
and a cryptographically random password. These addresses are internal application
login identifiers, not Gmail inboxes. Only the password hash is stored; the generated
password is returned once at creation or reset. A MongoDB sequence document is
initialized from existing chatter IDs and atomically incremented; the unique Worker
email index remains the final collision guard. New accounts record their creator,
account status, and audit history. Disable, ban, unban, and archive actions invalidate
active tokens without deleting historical records.

Worker account status fields are additive; existing records are retained and legacy
`active`/`suspendedAt` values continue to block inactive accounts until they are
updated through the account-management API.

The model-management screen supports existing profile fields, status changes, public
gallery URL management, and chatter assignments. Media file uploads and protected
private-media delivery are not available until durable media storage is configured.

The shared Android app uses `API_BASE_URL` as a Gradle property. Its default is the
deployed Render HTTPS API URL; set the property at build time to target another
environment.

To create the first administrator, set `BOOTSTRAP_ADMIN_EMAIL` in the server
environment, then run `npm run admin:bootstrap` from `server`. It creates a random
temporary password, prints it once, and requires a password change on first sign-in.
The command does nothing if an admin already exists.

Worker and assignment provisioning is restricted to authenticated administrators;
there is no public staff registration.
