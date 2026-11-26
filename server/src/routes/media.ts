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
  generatePresignedGetUrl,
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
  visibility: z.enum(["public", "private"]).default("public"),
  contentType: z.enum(["image/jpeg", "image/png", "image/webp"]),
  sizeBytes: z.number().int().positive().max(10 * 1024 * 1024),
}).superRefine((input, context) => {
  if (input.purpose === "avatar" && input.visibility !== "public") {
    context.addIssue({
      code: "custom",
      message: "Profile images must be public",
      path: ["visibility"],
    });
  }
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
      visibility: input.visibility,
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
    visibility: input.visibility,
    purpose: input.purpose,
    bucket: getBucketName(input.visibility),
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

  const exists = await objectExists(asset.visibility, asset.objectKey);
  if (!exists) throw new HttpError(409, "Uploaded object was not found in R2");

  const metadata = await getObjectMetadata(asset.visibility, asset.objectKey);
  const actualType = metadata.ContentType ?? "";
  const actualSize = metadata.ContentLength ?? -1;
  if (actualType !== asset.contentType || actualSize !== asset.sizeBytes) {
    throw new HttpError(400, "Uploaded object metadata does not match the upload request");
  }

  const publicUrl = asset.visibility === "public" ? publicUrlForKey(asset.objectKey) : "";
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
    const update: Record<string, unknown> = {
      $addToSet: { galleryMediaIds: asset._id },
    };
    if (asset.visibility === "public") {
      update.$addToSet = { galleryMediaIds: asset._id, galleryUrls: publicUrl };
    }
    await ModelAccount.findByIdAndUpdate(asset.modelAccountId, update);
  }

  res.json({
    asset: {
      id: asset._id.toString(),
      modelAccountId: asset.modelAccountId.toString(),
      purpose: asset.purpose,
      visibility: asset.visibility,
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
            ...(asset.publicUrl ? { galleryUrls: asset.publicUrl } : {}),
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
  const responseAssets = await Promise.all(assets.map(async (asset) => {
    let previewUrl = asset.publicUrl;
    if (asset.visibility === "private") {
      try {
        previewUrl = await generatePresignedGetUrl({
          visibility: "private",
          key: asset.objectKey,
          expiresInSeconds: 300,
        });
      } catch (error) {
        if (error instanceof R2StorageError) {
          throw new HttpError(503, "Private media preview unavailable");
        }
        throw error;
      }
    }
    return {
      id: asset._id.toString(),
      purpose: asset.purpose,
      visibility: asset.visibility,
      publicUrl: asset.publicUrl,
      previewUrl,
      contentType: asset.contentType,
      sizeBytes: asset.sizeBytes,
      createdAt: asset.createdAt,
    };
  }));
  res.json({ assets: responseAssets });
});

export default router;
