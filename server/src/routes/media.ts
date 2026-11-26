import { Router } from "express";
import { Types } from "mongoose";
import { z } from "zod";
import { env } from "../config/env.ts";
import { HttpError } from "../errors/HttpError.ts";
import { authenticateWorker } from "../middleware/authenticateWorker.ts";
import { authorize } from "../middleware/authorize.ts";
import { MediaAsset } from "../models/MediaAsset.ts";
import { ModelAccount } from "../models/ModelAccount.ts";
import {
  deleteObject,
  generatePresignedUploadUrl,
  getBucketName,
  getObjectMetadata,
  objectExists,
  R2StorageError,
} from "../services/r2Storage.ts";
import { parseInput } from "../utils/parseInput.ts";

const router = Router();
router.use(authenticateWorker, authorize("ADMIN"));

const objectIdSchema = z.string().regex(/^[a-f\d]{24}$/i);
const uploadIntentSchema = z.object({
  modelId: z.string().regex(/^[a-f\d]{24}$/i),
  purpose: z.enum(["avatar", "gallery"]),
  contentType: z.enum(["image/jpeg", "image/png", "image/webp"]),
  sizeBytes: z.number().int().positive().max(10 * 1024 * 1024),
});
const completeSchema = z.object({
  assetId: z.string().regex(/^[a-f\d]{24}$/i),
});

function extensionForContentType(contentType: string): string {
  if (contentType === "image/png") return "png";
  if (contentType === "image/webp") return "webp";
  return "jpg";
}

function publicUrlForKey(key: string): string {
  if (!env.R2_PUBLIC_BASE_URL) {
    throw new HttpError(
      409,
      "Public R2 media delivery is not configured. Set R2_PUBLIC_BASE_URL.",
    );
  }
  const encodedKey = key.split("/").map(encodeURIComponent).join("/");
  return `${env.R2_PUBLIC_BASE_URL.replace(/\/+$/, "")}/${encodedKey}`;
}

router.post("/upload-intent", async (req, res) => {
  const worker = req.authenticatedWorker;
  if (!worker) throw new HttpError(401, "Authentication required");

  const input = parseInput(uploadIntentSchema, req.body);
  const model = await ModelAccount.findById(input.modelId).select("_id").lean();
  if (!model) throw new HttpError(404, "Model not found");

  let presigned: { key: string; uploadUrl: string };
  try {
    presigned = await generatePresignedUploadUrl({
      visibility: "public",
      purpose: `model-${input.purpose}`,
      extension: extensionForContentType(input.contentType),
      contentType: input.contentType,
      sizeBytes: input.sizeBytes,
      expiresInSeconds: 900,
    });
  } catch (error) {
    if (error instanceof R2StorageError) throw new HttpError(503, "Media storage unavailable");
    throw error;
  }

  const asset = await MediaAsset.create({
    modelAccountId: model._id,
    visibility: "public",
    purpose: input.purpose,
    bucket: getBucketName("public"),
    objectKey: presigned.key,
    contentType: input.contentType,
    sizeBytes: input.sizeBytes,
    status: "pending_upload",
    uploadedByWorkerId: worker.id,
  });

  res.status(201).json({
    assetId: asset._id.toString(),
    key: asset.objectKey,
    uploadUrl: presigned.uploadUrl,
  });
});

router.post("/:assetId/complete", async (req, res) => {
  const input = parseInput(completeSchema, { assetId: req.params.assetId });
  const asset = await MediaAsset.findOne({
    _id: input.assetId,
    status: "pending_upload",
  });
  if (!asset) throw new HttpError(404, "Pending media not found");

  const exists = await objectExists("public", asset.objectKey);
  if (!exists) throw new HttpError(409, "Uploaded object was not found in R2");

  const metadata = await getObjectMetadata("public", asset.objectKey);
  const actualType = metadata.ContentType ?? "";
  const actualSize = metadata.ContentLength ?? -1;
  if (actualType !== asset.contentType || actualSize !== asset.sizeBytes) {
    throw new HttpError(400, "Uploaded object metadata does not match the upload request");
  }

  const publicUrl = publicUrlForKey(asset.objectKey);
  asset.publicUrl = publicUrl;
  asset.status = "ready";
  await asset.save();

  if (asset.purpose === "avatar") {
    await ModelAccount.findByIdAndUpdate(asset.modelAccountId, {
      $set: {
        avatarMediaId: asset._id,
        avatarUrl: publicUrl,
      },
    });
  } else if (asset.purpose === "gallery") {
    await ModelAccount.findByIdAndUpdate(asset.modelAccountId, {
      $addToSet: {
        galleryMediaIds: asset._id,
        galleryUrls: publicUrl,
      },
    });
  }

  res.json({
    asset: {
      id: asset._id.toString(),
      modelAccountId: asset.modelAccountId.toString(),
      purpose: asset.purpose,
      publicUrl,
      contentType: asset.contentType,
      sizeBytes: asset.sizeBytes,
      status: asset.status,
    },
  });
});

router.delete("/:assetId", async (req, res) => {
  const assetId = parseInput(objectIdSchema, req.params.assetId);
  const asset = await MediaAsset.findOne({ _id: assetId, status: "ready" });
  if (!asset) throw new HttpError(404, "Media not found");

  await deleteObject(asset.visibility, asset.objectKey);
  await ModelAccount.findByIdAndUpdate(asset.modelAccountId, {
    ...(asset.purpose === "avatar"
      ? { $unset: { avatarMediaId: 1 }, $set: { avatarUrl: "" } }
      : {}),
    ...(asset.purpose === "gallery"
      ? {
          $pull: {
            galleryMediaIds: asset._id,
            galleryUrls: asset.publicUrl,
          },
        }
      : {}),
  });

  asset.status = "deleted";
  await asset.save();
  res.json({ success: true });
});

router.get("/models/:modelId", async (req, res) => {
  const modelId = parseInput(objectIdSchema, req.params.modelId);
  const assets = await MediaAsset.find({
    modelAccountId: modelId,
    status: "ready",
  }).sort({ createdAt: -1 }).lean();
  res.json({
    assets: assets.map((asset) => ({
      id: asset._id.toString(),
      purpose: asset.purpose,
      publicUrl: asset.publicUrl,
      contentType: asset.contentType,
      sizeBytes: asset.sizeBytes,
      createdAt: asset.createdAt,
    })),
  });
});

export default router;
