import { model, Schema } from "mongoose";

const auditLogSchema = new Schema(
  {
    actorId: { type: Schema.Types.ObjectId, ref: "Worker", required: true },
    actorRole: { type: String, required: true },
    chatterId: { type: Schema.Types.ObjectId, ref: "Worker", default: null },
    action: { type: String, required: true, trim: true, maxlength: 100 },
    affectedType: { type: String, required: true, trim: true, maxlength: 100 },
    affectedId: { type: String, required: true, trim: true, maxlength: 100 },
    reason: { type: String, trim: true, maxlength: 500, default: "" },
    before: { type: Schema.Types.Mixed, default: null },
    after: { type: Schema.Types.Mixed, default: null },
    ipAddress: { type: String, default: "" },
    userAgent: { type: String, default: "" },
  },
  { timestamps: true, immutable: true },
);

auditLogSchema.index({ action: 1, createdAt: -1 });
auditLogSchema.index({ actorId: 1, createdAt: -1 });
auditLogSchema.index({ chatterId: 1, createdAt: -1 });

export const AuditLog = model("AuditLog", auditLogSchema);
