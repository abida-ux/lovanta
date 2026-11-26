import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { randomUUID } from "node:crypto";
import {
  bucketExists,
  deleteObject,
  generatePresignedGetUrl,
  generatePresignedUploadUrl,
  getObjectMetadata,
  objectExists,
  uploadObject,
  type MediaVisibility,
} from "../services/r2Storage.ts";

type TestResult = {
  passed: boolean;
  detail: string;
};

const testResults: Array<{ name: string; passed: boolean; detail: string }> = [];
const visibilities: MediaVisibility[] = ["public", "private"];

function report(name: string, result: TestResult): void {
  testResults.push({ name, passed: result.passed, detail: result.detail });
  console.log(`${result.passed ? "PASS" : "FAIL"} | ${name} | ${result.detail}`);
}

async function safeRun(name: string, operation: () => Promise<TestResult>): Promise<void> {
  try {
    report(name, await operation());
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    report(name, { passed: false, detail: message });
  }
}

async function testBucketConnection(visibility: MediaVisibility): Promise<void> {
  await safeRun(`${visibility} bucket exists`, async () => ({
    passed: await bucketExists(visibility),
    detail: await bucketExists(visibility)
      ? "Bucket is accessible"
      : "Bucket was not found or is inaccessible",
  }));
}

async function testConnectionUpload(visibility: MediaVisibility): Promise<void> {
  const key = `lovanta-system-tests/connection-test-${randomUUID()}.txt`;
  await safeRun(`${visibility} connection upload`, async () => {
    await uploadObject({
      visibility,
      key,
      body: `Lovanta R2 ${visibility} connection test\n`,
      contentType: "text/plain",
    });
    return { passed: true, detail: `Uploaded ${key}` };
  });

  await safeRun(`${visibility} connection metadata`, async () => {
    const metadata = await getObjectMetadata(visibility, key);
    const size = metadata.ContentLength ?? -1;
    const contentType = metadata.ContentType ?? "unknown";
    return {
      passed: size >= 0,
      detail: `size=${size}, contentType=${contentType}`,
    };
  });

  await safeRun(`${visibility} connection object exists`, async () => {
    const exists = await objectExists(visibility, key);
    return { passed: exists, detail: exists ? "Object exists" : "Object was not found" };
  });

  await safeRun(`${visibility} connection cleanup`, async () => {
    await deleteObject(visibility, key);
    const exists = await objectExists(visibility, key);
    return {
      passed: !exists,
      detail: exists ? "Object still exists after delete" : "Test object deleted",
    };
  });
}

async function presignedUpload(
  visibility: MediaVisibility,
  fileName: string,
  contentType: string,
  body: Uint8Array,
): Promise<void> {
  await safeRun(`${visibility} presigned ${fileName} URL`, async () => {
    const presigned = await generatePresignedUploadUrl({
      visibility,
      purpose: "system-test",
      extension: fileName.endsWith(".mp4") ? "mp4" : "png",
      contentType,
      sizeBytes: body.byteLength,
      expiresInSeconds: 900,
    });
    const response = await fetch(presigned.uploadUrl, {
      method: "PUT",
      body: Buffer.from(body),
      headers: {
        "content-type": contentType,
        "content-length": String(body.byteLength),
      },
    });
    if (!response.ok) {
      return {
        passed: false,
        detail: `HTTP ${response.status} while uploading ${fileName}`,
      };
    }

    const exists = await objectExists(visibility, presigned.key);
    if (!exists) {
      return { passed: false, detail: "Upload completed but object was not found" };
    }

    const metadata = await getObjectMetadata(visibility, presigned.key);
    const metadataType = metadata.ContentType ?? "";
    const metadataSize = metadata.ContentLength ?? -1;
    if (metadataType !== contentType || metadataSize !== body.byteLength) {
      await deleteObject(visibility, presigned.key);
      return {
        passed: false,
        detail: `Unexpected metadata: size=${metadataSize}, contentType=${metadataType}`,
      };
    }

    if (visibility === "private") {
      const getUrl = await generatePresignedGetUrl({
        visibility,
        key: presigned.key,
        expiresInSeconds: 300,
      });
      const getResponse = await fetch(getUrl);
      if (!getResponse.ok) {
        await deleteObject(visibility, presigned.key);
        return {
          passed: false,
          detail: `Private presigned GET returned HTTP ${getResponse.status}`,
        };
      }
    }

    await deleteObject(visibility, presigned.key);
    return {
      passed: true,
      detail: `Uploaded and verified ${fileName}: size=${metadataSize}, contentType=${metadataType}`,
    };
  });
}

const onePixelPng = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
  "base64",
);

console.log("Lovanta Cloudflare R2 local test");

for (const visibility of visibilities) {
  await testBucketConnection(visibility);
  await testConnectionUpload(visibility);
  await presignedUpload(visibility, "test-image.png", "image/png", onePixelPng);
}

const videoArg = process.argv.find((argument) => argument.startsWith("--video="));
if (videoArg) {
  const videoPath = resolve(videoArg.slice("--video=".length));
  try {
    const video = await readFile(videoPath);
    for (const visibility of visibilities) {
      await presignedUpload(visibility, "test-video.mp4", "video/mp4", video);
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    report("MP4 test file", { passed: false, detail: `Could not read file: ${message}` });
  }
} else {
  report("MP4 test", {
    passed: true,
    detail: "Skipped because no --video=<path> argument was supplied",
  });
}

const failures = testResults.filter((result) => !result.passed).length;
console.log(`\nR2 test summary: ${testResults.length - failures}/${testResults.length} checks passed`);
if (failures > 0) process.exitCode = 1;
