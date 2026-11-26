import { model, Schema } from "mongoose";

const mediaAssetSchema = new Schema(
  {
    modelAccountId: {
      type: Schema.Types.ObjectId,
      ref: "ModelAccount",
      required: true,
    },
    visibility: {
      type: String,
      enum: ["public", "private"],
      required: true,
      default: "public",
    },
    purpose: {
      type: String,
      enum: ["avatar", "gallery", "message"],
      required: true,
    },
    bucket: { type: String, required: true, trim: true },
    objectKey: { type: String, required: true, trim: true, unique: true },
    publicUrl: { type: String, trim: true, default: "" },
    contentType: { type: String, required: true, trim: true },
    sizeBytes: { type: Number, required: true, min: 1 },
    status: {
      type: String,
      enum: ["pending_upload", "ready", "deleted"],
      required: true,
      default: "pending_upload",
    },
    uploadedByWorkerId: {
      type: Schema.Types.ObjectId,
      ref: "Worker",
      required: true,
    },
  },
  { timestamps: true },
);

mediaAssetSchema.index({ modelAccountId: 1, visibility: 1, purpose: 1, status: 1 });

export const MediaAsset = model("MediaAsset", mediaAssetSchema);
