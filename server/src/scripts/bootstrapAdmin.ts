import bcrypt from "bcryptjs";
import crypto from "node:crypto";
import mongoose from "mongoose";
import { env } from "../config/env.ts";
import { Worker } from "../models/Worker.ts";

async function bootstrapAdmin(): Promise<void> {
  if (!env.BOOTSTRAP_ADMIN_EMAIL) {
    throw new Error(
      "Set BOOTSTRAP_ADMIN_EMAIL in the server environment before running this command.",
    );
  }

  const temporaryPassword = crypto.randomBytes(18).toString("base64url");
  await mongoose.connect(env.MONGODB_URI);
  try {
    const existingAdmin = await Worker.exists({ role: { $in: ["ADMIN", "admin"] } });
    if (existingAdmin) {
      console.log("An administrator already exists; no account was changed.");
      return;
    }

    const worker = new Worker({
      email: env.BOOTSTRAP_ADMIN_EMAIL.toLowerCase(),
      displayName: env.BOOTSTRAP_ADMIN_NAME ?? "Platform Administrator",
      passwordHash: await bcrypt.hash(temporaryPassword, 12),
      role: "ADMIN",
      active: true,
      mustChangePassword: true,
    });
    await worker.save();
    console.log(`Initial administrator created. Temporary password: ${temporaryPassword}`);
  } finally {
    await mongoose.disconnect();
  }
}

bootstrapAdmin().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : "Administrator bootstrap failed.");
  process.exitCode = 1;
});
