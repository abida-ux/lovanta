# Lovanta Cloudflare R2 implementation plan

## Status and scope

**Plan only — no implementation has been made.** This plan retains the existing native Java Android app, Node.js/Express/TypeScript API, MongoDB, and staff authentication. It adds media and subscriber-payment capabilities where the checked-in source currently has gaps, while preserving existing endpoints and URL fields during migration.

## Existing-code constraints

- Model media currently consists of `avatarUrl` and `galleryUrls` string fields in [ModelAccount.ts](./server/src/models/ModelAccount.ts). No standalone media model, upload route, or durable media-storage implementation was found.
- Admin model create/update accepts URL strings in [admin.ts](./server/src/routes/admin.ts). [ModelManagementActivity.java](./app/src/main/java/com/example/lovanta/ModelManagementActivity.java) also takes a profile-picture URL and indicates protected private uploads are unavailable.
- [ChatMessage.ts](./server/src/models/ChatMessage.ts) stores text only. [PPVPurchase.ts](./server/src/models/PPVPurchase.ts) already references `messageId`, subscriber, amount, and status, which supports a message-level PPV design without replacing the purchase model.
- The checked-in API has staff/admin/chatter routes but no subscriber authentication, purchase, or subscriber media-access routes. [Subscriber.ts](./server/src/models/Subscriber.ts) has no authentication fields; [auth.ts](./server/src/routes/auth.ts) authenticates workers. No payment provider is identified in the repository.
- [Conversation.ts](./server/src/models/Conversation.ts) stores `subscriberId` as a string, while [PPVPurchase.ts](./server/src/models/PPVPurchase.ts) stores it as an ObjectId reference. Inspect actual data and settle the mapping before implementing ownership checks.
- The Android [ApiClient.java](./app/src/main/java/com/example/lovanta/ApiClient.java) currently sends JSON, not files. The subscriber PWA/dashboard source is not present in the repository.

## Proposed MongoDB design

Create `server/src/models/MediaAsset.ts` with:

- `modelAccountId`: required `ModelAccount` reference.
- `objectKey`: unique, server-generated R2 object key; do not use a client-supplied path or filename as the key.
- `visibility`: `public` or `private`, used to select the corresponding R2 bucket.
- `purpose`: for example, `avatar`, `gallery`, or `message`.
- `mediaType`, validated `contentType`, and `sizeBytes`.
- `status`: `pending_upload`, `ready`, or `deleted`, with a rejected/error state if needed.
- `uploadedByWorkerId`; optional `conversationId` and `messageId` for chat attachments.
- timestamps and soft-delete metadata.

Associations and compatibility:

- Add `avatarMediaId` and `galleryMediaIds` to [ModelAccount.ts](./server/src/models/ModelAccount.ts). Retain `avatarUrl` and `galleryUrls` during migration so existing clients and stored links continue to work.
- Add `mediaAssetIds` and PPV pricing/paywall metadata to [ChatMessage.ts](./server/src/models/ChatMessage.ts). Verify each attached asset is ready and belongs to the same model/conversation.
- Link chat media through `ChatMessage.mediaAssetIds`. The message already belongs to a conversation and model. Keep `PPVPurchase.messageId` as the purchase unit: a successful purchase unlocks PPV media attached to that message.
- Add an index to [PPVPurchase.ts](./server/src/models/PPVPurchase.ts) that supports efficient subscriber/message purchase checks. Consider provider transaction references/idempotency fields on [Transaction.ts](./server/src/models/Transaction.ts) after the payment provider is selected.

## Proposed API endpoints

These are proposals, **not existing endpoints**:

| Endpoint | Purpose and authorization |
|---|---|
| `POST /api/v1/media/upload-intents` | Authenticated staff request for a presigned upload URL. Admins can upload for any model; chatters must be active and assigned to that model. Validate purpose, visibility, content type, and size before creating a pending `MediaAsset`. |
| `POST /api/v1/media/:mediaAssetId/complete` | Verify the object exists in R2 and check its metadata/size before marking the asset ready. Require the uploader or an authorized admin. |
| `GET /api/v1/admin/models/:modelId/media` | Admin-facing asset listing/status. Alternatively, extend the existing model-detail response without breaking its current shape. |
| `POST /api/v1/conversations/:conversationId/messages` | Extend the existing chatter message endpoint to accept optional `mediaAssetIds` and PPV pricing. Keep text-only requests working and retain the current assignment check in [workspace.ts](./server/src/routes/workspace.ts). |
| `GET /api/v1/public/models/:modelId/media` | Return public gallery metadata only. Public objects may use the public media domain; never return private keys or URLs here. |
| `GET /api/v1/subscriber/conversations/:conversationId/messages` | New subscriber-facing message endpoint. Authenticate the subscriber and verify conversation ownership before returning message metadata. |
| `POST /api/v1/subscriber/messages/:messageId/ppv-purchases` | Create/reuse a pending `PPVPurchase` and `Transaction`, then initiate checkout with the selected provider. Never mark a purchase paid based on client input. |
| `POST /api/v1/webhooks/payments/:provider` | Verify the provider webhook signature, correlate the event to the expected purchase/amount/currency, and update purchase/transaction state idempotently. |
| `GET /api/v1/subscriber/media/:mediaAssetId/access` | Verify subscriber ownership and a paid, non-refunded purchase for the asset's PPV message. Only then return a short-lived signed GET URL. |

The webhook route may need provider-specific raw-body/signature handling. The current general JSON parser is configured in [app.ts](./server/src/app.ts); its ordering/configuration should be checked after selecting the provider.

## Authorization and payment behavior

- Keep the existing worker JWT flow and admin/chatter routes. Add explicit media permissions or role checks rather than broadening access accidentally.
- Recommended initial policy: admins can manage all model media; assigned chatters can upload and attach media only for their assigned model/conversation. Keep supervisors read-only unless the product explicitly grants upload rights.
- Add separate subscriber authentication middleware. Do not treat staff tokens as subscriber identity. First establish whether the external PWA has a trusted identity/token issuer the API can validate. If not, subscriber authentication must be implemented before private-media delivery.
- A purchase is valid only after verified provider confirmation. Before issuing a private-media URL, check subscriber status, conversation/message ownership, and a matching `PPVPurchase` with `status: paid` and no refund. Do not trust a client-supplied subscriber ID, purchase ID, or `paid` flag.
- Keep private R2 objects off public domains. Signed GET URLs are bearer links while valid; use short expiry, do not log/store them, and recognize they cannot prevent copying after download.

## Files to create

- `server/src/models/MediaAsset.ts`
- `server/src/services/r2Storage.ts` — R2 client, server-generated keys, presigning, and object verification.
- `server/src/services/ppvAccess.ts` — reusable ownership and paid-purchase checks.
- `server/src/services/paymentProvider.ts` — provider adapter after provider selection.
- `server/src/middleware/authenticateSubscriber.ts` — after establishing subscriber identity/token contract.
- `server/src/routes/media.ts`
- `server/src/routes/subscriberMedia.ts`
- `server/src/routes/paymentWebhook.ts`

## Files to modify

### Backend

- [server/src/config/env.ts](./server/src/config/env.ts), [server/.env.example](./server/.env.example), and [render.yaml](./render.yaml): validate/configure server-only R2 settings.
- [server/package.json](./server/package.json) and its lockfile: add backend dependencies.
- [server/src/app.ts](./server/src/app.ts): mount new routes and configure any provider-specific webhook parsing.
- [server/src/models/ModelAccount.ts](./server/src/models/ModelAccount.ts) and [server/src/models/ChatMessage.ts](./server/src/models/ChatMessage.ts): add media references and paywall metadata.
- [server/src/routes/admin.ts](./server/src/routes/admin.ts) and [server/src/routes/workspace.ts](./server/src/routes/workspace.ts): model media management, assigned-chatter uploads, and message attachments.
- [server/src/middleware/authenticateWorker.ts](./server/src/middleware/authenticateWorker.ts): only if media permissions need to extend existing staff permissions.
- [server/src/models/Conversation.ts](./server/src/models/Conversation.ts) and/or [server/src/models/Subscriber.ts](./server/src/models/Subscriber.ts): only if data inspection confirms subscriber-ID normalization or new local auth fields are required.
- [server/src/models/PPVPurchase.ts](./server/src/models/PPVPurchase.ts) and [server/src/models/Transaction.ts](./server/src/models/Transaction.ts): indexes/provider idempotency metadata if required by the selected payment design.

### Android and external clients

- [ApiClient.java](./app/src/main/java/com/example/lovanta/ApiClient.java): API upload-intent/completion calls and direct presigned PUT support.
- [ModelManagementActivity.java](./app/src/main/java/com/example/lovanta/ModelManagementActivity.java): replace URL-only model media entry with upload/select/manage behavior while keeping existing data readable.
- [ChatDetailActivity.java](./app/src/main/java/com/example/lovanta/ChatDetailActivity.java): attach media to messages and indicate PPV state.
- Update the relevant layouts and message DTOs under `app/src/main/res/layout` and `app/src/main/java/com/example/lovanta`.
- The subscriber PWA must authenticate subscribers, initiate PPV purchases, and request protected media access. Its source is not in this repo, so its exact files cannot be named here.

Android can use the document picker and `ContentResolver` streams. The existing manifest already includes Internet permission, so no R2 credentials, Android R2 SDK, or broad storage permission should be required.

## Dependencies and deployment settings

- Backend: `@aws-sdk/client-s3` and `@aws-sdk/s3-request-presigner` for R2's S3-compatible API. Add the selected payment-provider SDK after provider choice.
- Render environment variables: `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET_PUBLIC`, `R2_BUCKET_PRIVATE`, and optionally `R2_PUBLIC_BASE_URL`. Keep credentials only in Render/server secrets and grant the R2 token only required bucket/actions.
- Configure browser-to-R2 CORS for the actual dashboard origin and required upload methods/headers. Never configure a public domain for the private bucket.
- Presigned direct uploads avoid routing large files through Express and its current JSON parser. Use multipart upload for large videos if needed.

## Phases and approval gates

1. **Confirm external contracts:** identify subscriber-PWA authentication and payment provider; inspect how existing conversation subscriber IDs map to `Subscriber` records. Decide whether assigned chatters may create PPV messages.
2. **Add R2 configuration and media model:** establish buckets/tokens, add the R2 service and upload-intent/completion lifecycle, and add model/gallery references while retaining legacy URLs.
3. **Add staff upload and message attachment flows:** enforce admin access and chatter model assignment; update Android model-management and chat UI.
4. **Add subscriber authentication and payment fulfillment:** integrate or implement subscriber auth, checkout, signed webhooks, idempotent purchase updates, and subscriber conversation APIs.
5. **Add protected delivery:** authorize per subscriber/message/purchase before signing private GET URLs; expose public media separately.
6. **Migrate and validate:** inventory existing URL values; migrate only assets that need moving; test role boundaries, ownership, failed/refunded payment denial, URL expiry, upload validation, and legacy API behavior.

**Approval blockers:** the repository does not establish the subscriber authentication source or payment provider, and the conversation/subscriber ID relationship needs data confirmation. The plan avoids replacing the existing staff authentication, admin, chatter, or URL-based records while those decisions are resolved.
