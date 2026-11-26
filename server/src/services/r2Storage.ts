import {
  DeleteObjectCommand,
  GetObjectCommand,
  HeadBucketCommand,
  HeadObjectCommand,
  PutObjectCommand,
  S3Client,
  type PutObjectCommandInput,
} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { randomUUID } from "node:crypto";
import { extname } from "node:path";
import { env } from "../config/env.ts";

export type MediaVisibility = "public" | "private";

export class R2StorageError extends Error {
  constructor(operation: string, cause?: unknown) {
    super(`${operation} failed`);
    this.name = "R2StorageError";

    if (cause instanceof Error) {
      const code = (cause as Error & { name?: string }).name;
      if (code && code !== "Error") {
        this.message = `${operation} failed: ${code}`;
      }
    }
  }
}

function requiredR2Config(): {
  accountId: string;
  accessKeyId: string;
  secretAccessKey: string;
  endpoint: string;
  publicBucket: string;
  privateBucket: string;
} {
  const values = {
    accountId: env.R2_ACCOUNT_ID,
    accessKeyId: env.R2_ACCESS_KEY_ID,
    secretAccessKey: env.R2_SECRET_ACCESS_KEY,
    endpoint: env.R2_ENDPOINT,
    publicBucket: env.R2_BUCKET_PUBLIC,
    privateBucket: env.R2_BUCKET_PRIVATE,
  };

  if (!values.accountId || !values.accessKeyId || !values.secretAccessKey
    || !values.endpoint || !values.publicBucket || !values.privateBucket) {
    throw new R2StorageError("R2 configuration");
  }

  return {
    accountId: values.accountId,
    accessKeyId: values.accessKeyId,
    secretAccessKey: values.secretAccessKey,
    endpoint: values.endpoint,
    publicBucket: values.publicBucket,
    privateBucket: values.privateBucket,
  };
}

let client: S3Client | undefined;

export function getR2Client(): S3Client {
  if (client) return client;

  const config = requiredR2Config();
  client = new S3Client({
    region: "auto",
    endpoint: config.endpoint,
    forcePathStyle: true,
    credentials: {
      accessKeyId: config.accessKeyId,
      secretAccessKey: config.secretAccessKey,
    },
  });
  return client;
}

export function getBucketName(visibility: MediaVisibility): string {
  const config = requiredR2Config();
  return visibility === "public" ? config.publicBucket : config.privateBucket;
}

export function getR2Endpoint(): string {
  return requiredR2Config().endpoint;
}

function safeExtension(extension: string): string {
  const normalized = extname(`file.${extension.replace(/^\./, "")}`)
    .toLowerCase()
    .replace(/[^a-z0-9.]/g, "");
  return normalized || ".bin";
}

function safePurpose(purpose: string): string {
  const normalized = purpose.trim().toLowerCase().replace(/[^a-z0-9_-]/g, "-");
  return normalized || "media";
}

export function generateObjectKey(
  visibility: MediaVisibility,
  purpose: string,
  extension: string,
): string {
  const safeExt = safeExtension(extension);
  return `${visibility}/${safePurpose(purpose)}/${randomUUID()}${safeExt}`;
}

export async function bucketExists(visibility: MediaVisibility): Promise<boolean> {
  try {
    await getR2Client().send(new HeadBucketCommand({
      Bucket: getBucketName(visibility),
    }));
    return true;
  } catch (error) {
    const code = (error as { name?: string }).name;
    if (code === "NotFound" || code === "NoSuchBucket") return false;
    throw new R2StorageError("Bucket access", error);
  }
}

export async function uploadObject(input: {
  visibility: MediaVisibility;
  key: string;
  body: Uint8Array | string;
  contentType: string;
}): Promise<void> {
  const command = new PutObjectCommand({
    Bucket: getBucketName(input.visibility),
    Key: input.key,
    Body: input.body,
    ContentType: input.contentType,
  });
  try {
    await getR2Client().send(command);
  } catch (error) {
    throw new R2StorageError("Upload object", error);
  }
}

export async function objectExists(
  visibility: MediaVisibility,
  key: string,
): Promise<boolean> {
  try {
    await getR2Client().send(new HeadObjectCommand({
      Bucket: getBucketName(visibility),
      Key: key,
    }));
    return true;
  } catch (error) {
    const code = (error as { name?: string }).name;
    if (code === "NotFound" || code === "NoSuchKey") return false;
    throw new R2StorageError("Check object", error);
  }
}

export async function getObjectMetadata(visibility: MediaVisibility, key: string) {
  try {
    return await getR2Client().send(new HeadObjectCommand({
      Bucket: getBucketName(visibility),
      Key: key,
    }));
  } catch (error) {
    throw new R2StorageError("Retrieve object metadata", error);
  }
}

export async function deleteObject(visibility: MediaVisibility, key: string): Promise<void> {
  try {
    await getR2Client().send(new DeleteObjectCommand({
      Bucket: getBucketName(visibility),
      Key: key,
    }));
  } catch (error) {
    throw new R2StorageError("Delete object", error);
  }
}

export async function generatePresignedUploadUrl(input: {
  visibility: MediaVisibility;
  purpose: string;
  extension: string;
  contentType: string;
  sizeBytes: number;
  expiresInSeconds?: number;
}): Promise<{ key: string; uploadUrl: string }> {
  if (!Number.isSafeInteger(input.sizeBytes) || input.sizeBytes < 1
    || input.sizeBytes > 500 * 1024 * 1024) {
    throw new R2StorageError("Presigned upload validation");
  }
  if (!/^([a-z]+\/[a-z0-9.+-]+)$/i.test(input.contentType)) {
    throw new R2StorageError("Presigned upload validation");
  }

  const key = generateObjectKey(input.visibility, input.purpose, input.extension);
  const commandInput: PutObjectCommandInput = {
    Bucket: getBucketName(input.visibility),
    Key: key,
    ContentType: input.contentType,
    ContentLength: input.sizeBytes,
  };
  const command = new PutObjectCommand(commandInput);
  const expiresIn = Math.min(Math.max(input.expiresInSeconds ?? 900, 60), 3600);

  try {
    const uploadUrl = await getSignedUrl(getR2Client(), command, { expiresIn });
    return { key, uploadUrl };
  } catch (error) {
    throw new R2StorageError("Generate presigned upload URL", error);
  }
}

export async function generatePresignedGetUrl(input: {
  visibility: MediaVisibility;
  key: string;
  expiresInSeconds?: number;
}): Promise<string> {
  if (input.visibility !== "private") {
    throw new R2StorageError("Presigned private GET validation");
  }
  const expiresIn = Math.min(Math.max(input.expiresInSeconds ?? 300, 30), 900);

  try {
    return await getSignedUrl(getR2Client(), new GetObjectCommand({
      Bucket: getBucketName(input.visibility),
      Key: input.key,
    }), { expiresIn });
  } catch (error) {
    throw new R2StorageError("Generate presigned GET URL", error);
  }
}
