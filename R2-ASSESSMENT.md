# Lovanta media and Cloudflare R2 assessment

## Scope and summary

This assessment is based on the checked-in application and server source, configuration, and documentation. No files were changed during the initial inspection. This does not verify the live Render deployment, MongoDB contents, or the hosts behind any existing media URLs.

**Bottom line:** Lovanta currently has model profile/gallery URL fields, but no file upload, durable media storage, private-media delivery, or PPV media authorization flow in the inspected source. R2 can be added without rebuilding the app: keep Express as the authorization and metadata boundary, store bytes in R2, and use short-lived presigned URLs for upload and authorized private viewing.

## 1. Project architecture

- **Android:** Native Android application written in Java, with XML layouts and Gradle Kotlin DSL configuration. It targets Java 11; min SDK is 24 and target SDK is 37. See [app/build.gradle.kts](./app/build.gradle.kts) and [AndroidManifest.xml](./app/src/main/AndroidManifest.xml). No Kotlin application source was found.
- **Backend:** Node.js and Express with TypeScript source and Mongoose for MongoDB. The server requires Node 22.18+ and uses Express 5 and Mongoose. See [server/package.json](./server/package.json) and [server/tsconfig.json](./server/tsconfig.json).
- **MongoDB models:**
  - `Worker`: staff/admin/chatter accounts — [Worker.ts](./server/src/models/Worker.ts)
  - `Subscriber`: subscriber records — [Subscriber.ts](./server/src/models/Subscriber.ts)
  - `ModelAccount`: model profiles and current media URL fields — [ModelAccount.ts](./server/src/models/ModelAccount.ts)
  - `Conversation`, `ChatMessage`: conversations and messages — [Conversation.ts](./server/src/models/Conversation.ts), [ChatMessage.ts](./server/src/models/ChatMessage.ts)
  - `PPVPurchase`, `Transaction`: purchase and transaction records — [PPVPurchase.ts](./server/src/models/PPVPurchase.ts), [Transaction.ts](./server/src/models/Transaction.ts)
  - No standalone media model was found. Schemas do not specify custom collection names, so Mongoose's normal model-to-collection naming applies.
- **Dashboard/front end:** The repo contains Android admin screens in [AdminActivity.java](./app/src/main/java/com/example/lovanta/AdminActivity.java) and [ModelManagementActivity.java](./app/src/main/java/com/example/lovanta/ModelManagementActivity.java), and a chatter workspace in [VipActivity.java](./app/src/main/java/com/example/lovanta/VipActivity.java) and [ChatDetailActivity.java](./app/src/main/java/com/example/lovanta/ChatDetailActivity.java). The API README describes a subscriber PWA, but no web frontend source or web package manifest was found in the project tree. Its actual implementation and technologies cannot be confirmed from this repository.
- **Hosting:** [render.yaml](./render.yaml) configures the API as a Render Node web service. The Android app's default API URL is `https://lovanta1.onrender.com`; see [app/build.gradle.kts](./app/build.gradle.kts) and [server/README.md](./server/README.md). This confirms Render configuration and a configured URL, not that a deployment is currently live.

## 2. Existing media storage and behavior

- There is no upload or durable media-storage implementation in the checked-in code.
- `ModelAccount` stores `avatarUrl` and `galleryUrls` as strings ([ModelAccount.ts:7-9](./server/src/models/ModelAccount.ts)). Admin model create/update APIs accept these URL strings, not files ([admin.ts:251-315](./server/src/routes/admin.ts)).
- The Android model-management screen lets admins enter a profile-picture URL and view/remove public gallery URLs. It explicitly says private uploads/protected delivery and new uploads are unavailable ([ModelManagementActivity.java:156-197](./app/src/main/java/com/example/lovanta/ModelManagementActivity.java)).
- The app downloads a model-management avatar by URL and opens gallery URLs with an Android view intent; it does not upload those files ([ModelManagementActivity.java:315-325](./app/src/main/java/com/example/lovanta/ModelManagementActivity.java), [ModelManagementActivity.java:394-447](./app/src/main/java/com/example/lovanta/ModelManagementActivity.java)).
- The chatter workspace API returns `avatarUrl`, but the VIP model list currently displays a bundled placeholder avatar ([workspace.ts:69-90](./server/src/routes/workspace.ts), [VipActivity.java:62-72](./app/src/main/java/com/example/lovanta/VipActivity.java)).
- The backend uses JSON parsing with a 32 KB limit and mounts auth, admin, and workspace routers; it has no multipart upload parser or media-serving route ([app.ts:26-35](./server/src/app.ts)). The server dependency manifest has no Multer or R2/S3 SDK ([server/package.json](./server/package.json)).
- **Where the actual photo/video bytes live cannot be confirmed.** The app stores URL references, so any existing files may be on whatever external host those URLs point to. No such provider or local/server file-storage implementation is identifiable in the source.

## 3. Media permissions and PPV

- The current `avatarUrl` and `galleryUrls` fields do not encode a public/private flag or media-access policy. The Android UI treats the gallery as public and has no working private gallery.
- `PPVPurchase` records a subscriber, model, chatter, **message ID**, amount, status, and purchase time ([PPVPurchase.ts:5-13](./server/src/models/PPVPurchase.ts)). `Transaction` can reference a PPV purchase ([Transaction.ts:5-15](./server/src/models/Transaction.ts)).
- No subscriber-facing purchase or media authorization flow was found. The existing PPV API is an authenticated admin/reporting `GET`; other code counts paid purchases, but does not check payment to grant access to specific media ([admin.ts:1052-1058](./server/src/routes/admin.ts), [workspace.ts:25-66](./server/src/routes/workspace.ts)). `ChatMessage` currently contains text, not media or PPV attachment data ([ChatMessage.ts](./server/src/models/ChatMessage.ts)).
- Chatter APIs require staff authentication and check that the chatter is assigned to the model for conversation access ([workspace.ts:23-24](./server/src/routes/workspace.ts), [workspace.ts:94-160](./server/src/routes/workspace.ts)). Admin routes require staff authentication and admin/supervisor roles; model writes are admin-only ([admin.ts:25-26](./server/src/routes/admin.ts), [admin.ts:251-315](./server/src/routes/admin.ts)). Subscriber media access, model logins, and model-specific media permissions are not implemented in the routes found.
- **Copying a URL:** There is no existing private-media delivery system to test. Public gallery entries are direct URLs; anyone who obtains a URL to a publicly reachable file can generally open or share it. The backend does not currently protect such a URL with PPV authorization.

## 4. Existing API structure

Router mounts are in [app.ts:31-35](./server/src/app.ts).

- **Model/media metadata:** `GET /api/v1/admin/models`, `GET /api/v1/admin/models/:id`, `POST /api/v1/admin/models`, and `PATCH /api/v1/admin/models/:id`. The create/update endpoints accept avatar/gallery URL strings; there is no file upload endpoint ([admin.ts:197-315](./server/src/routes/admin.ts)).
- **PPV and transactions:** `GET /api/v1/admin/ppv` and `GET /api/v1/admin/transactions` are reporting endpoints, not purchase or delivery flows ([admin.ts:1043-1058](./server/src/routes/admin.ts)).
- **Chatter workspace:** `GET /api/v1/models`, `GET /api/v1/models/:modelAccountId/conversations`, and `GET`/`POST /api/v1/conversations/:conversationId/messages`; message POST accepts text ([workspace.ts:69-92](./server/src/routes/workspace.ts), [workspace.ts:94-160](./server/src/routes/workspace.ts), [workspace.ts:165-225](./server/src/routes/workspace.ts)).
- **Upload tooling:** The Android `ApiClient` sends JSON using `HttpURLConnection`; its request wrapper has no file/multipart upload path ([ApiClient.java:145-160](./app/src/main/java/com/example/lovanta/ApiClient.java), [ApiClient.java:259-310](./app/src/main/java/com/example/lovanta/ApiClient.java)). No Multer or equivalent upload middleware is present in the backend dependencies ([server/package.json](./server/package.json)).

## 5. How R2 could fit without rebuilding the app

Keep Express as the authorization and metadata boundary, MongoDB as the metadata/purchase record, and R2 as the byte store:

1. Add authenticated API routes that authorize an upload, generate a random object key, and return a **short-lived R2 presigned PUT URL**. Add a completion/registration step that records the uploaded object and its metadata. Node can use R2's S3-compatible API.
2. Upload bytes **directly from the Java app to R2 using the presigned URL**. The app asks the Lovanta API for permission first; it never receives R2 credentials. The current `ApiClient` needs a non-JSON upload request path and a file-picker/streaming flow.
3. For an admin/chatter web dashboard, use the same API-issued upload authorization. The dashboard source is absent here, so its implementation changes cannot be specified more precisely.
4. Keep intentional public gallery media in a public bucket/custom domain. Put PPV/private objects in a **private bucket without a public URL/domain**. For private viewing, the backend authenticates the subscriber and verifies a paid, non-refunded purchase for that exact media/message before issuing a short-lived signed GET URL—or serves media through an authenticated proxy. A signed URL is a transferable bearer link until it expires, and cannot prevent copying after download.
5. Store object keys and media metadata in MongoDB, not long-lived signed URLs. A `MediaAsset` model could record object key, visibility, model/message reference, content type, size, creator, and timestamps; model profiles can reference media IDs. Associate PPV purchases with the exact media or message content they unlock.

For large video uploads, use an R2 multipart-upload flow where appropriate. Validate file size and actual content type, use server-generated keys, and avoid logging presigned URLs.

## 6. Deployment and migration

- **Backend secrets/config:** Add server-only environment settings such as `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET_PUBLIC`, `R2_BUCKET_PRIVATE`, and (if used) `R2_PUBLIC_BASE_URL`. Configure and validate them in [server/src/config/env.ts](./server/src/config/env.ts); add secrets to Render's environment configuration in [render.yaml](./render.yaml), keeping secret values out of the repository. Restrict the R2 token to required buckets/actions.
- **Dependencies:** Backend: `@aws-sdk/client-s3` and `@aws-sdk/s3-request-presigner`. A server-proxied Multer flow is possible, but presigned direct upload avoids sending large files through the API. Android can use standard Java HTTP for presigned PUTs; no R2 credential or SDK needs to go in the app. Keep `API_BASE_URL` as the API address.
- **Platform settings:** Configure browser-to-R2 CORS for the exact dashboard origins and required upload methods/headers if a browser dashboard is used. Keep the private bucket off public domains. Render needs the environment secrets; R2 avoids depending on local server disk for durable uploads.
- **Existing media:** The source contains URL fields, not uploaded files or a defined storage provider. No code-backed media migration can be identified. Before cutover, inspect actual MongoDB records and test those URLs; migrate only the assets that need to move, then replace URL references with the new media records/keys. Private-media migration cannot be scoped from this repo because private uploads are marked unavailable.

## 7. Security findings and recommendations

The review found **no confirmed current exploitable vulnerability in a working media/PPV delivery path**, because that path is not implemented. There is, however, a high-impact **implementation risk** if PPV assets are made public or returned without server-side purchase checks.

| # | Severity | File | Lines | Vulnerability | Confidence |
|---|----------|------|-------|---------------|------------|
| — | — | — | — | No confirmed current media/PPV vulnerability found; private-media authorization and delivery are not implemented in the examined source. | High |

Principal risks for the planned integration:

- **Exposed credentials:** No R2 configuration appears in the tracked server config examined. Keep R2 credentials only in Render/server secrets—never in Android, dashboard JavaScript, or MongoDB.
- **Unauthorized private access / PPV bypass:** Do not rely on obscured object keys or a public bucket. Authorize every private access grant server-side against the authenticated subscriber and the exact media/message's paid, non-refunded purchase. Validate payment notifications and make purchase updates idempotent.
- **URL sharing:** Treat signed GET URLs as bearer credentials: keep expiry short, don't store or log them, and don't mistake them for revocable access. For tighter control, proxy/private-gate delivery; even then, downloaded media can be copied.
- **Upload abuse:** Enforce role/model assignment, size and content validation, and server-generated object keys before signing uploads. Keep public and private assets in separate buckets, and make private objects inaccessible through any public domain.

## Source files reviewed

- Android app and UI: [app/src/main](./app/src/main)
- Backend source: [server/src](./server/src)
- Server dependencies and setup: [server/package.json](./server/package.json), [server/README.md](./server/README.md)
- Render deployment configuration: [render.yaml](./render.yaml)
