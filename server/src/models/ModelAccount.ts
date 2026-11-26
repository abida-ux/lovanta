import { model, Schema } from "mongoose";

const modelAccountSchema = new Schema(
  {
    displayName: { type: String, required: true, trim: true, maxlength: 100 },
    username: { type: String, unique: true, sparse: true, trim: true, lowercase: true, maxlength: 100 },
    avatarUrl: { type: String, trim: true, default: "" },
    avatarMediaId: { type: Schema.Types.ObjectId, ref: "MediaAsset", default: null },
    description: { type: String, trim: true, maxlength: 2000, default: "" },
    galleryUrls: [{ type: String, trim: true }],
    galleryMediaIds: [{ type: Schema.Types.ObjectId, ref: "MediaAsset" }],
    active: { type: Boolean, required: true, default: true },
    status: { type: String, enum: ["active", "inactive", "suspended", "archived"], default: "active" },
    availability: { type: String, enum: ["available", "busy", "offline"], default: "available" },
    verified: { type: Boolean, required: true, default: false },
    ageVerified: { type: Boolean, required: true, default: false },
    consentRecorded: { type: Boolean, required: true, default: false },
    internalNotes: { type: String, trim: true, maxlength: 5000, default: "" },
    archivedAt: { type: Date, default: null },
  },
  { timestamps: true },
);

modelAccountSchema.index({ status: 1, createdAt: -1 });

export const ModelAccount = model("ModelAccount", modelAccountSchema);
