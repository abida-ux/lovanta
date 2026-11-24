import { model, Schema } from "mongoose";

const workerSchema = new Schema(
  {
    username: { type: String, unique: true, sparse: true, trim: true, lowercase: true, maxlength: 100 },
    email: {
      type: String,
      required: true,
      unique: true,
      lowercase: true,
      trim: true,
      maxlength: 254,
    },
    displayName: { type: String, required: true, trim: true, maxlength: 100 },
    passwordHash: { type: String, required: true, select: false },
    role: {
      type: String,
      enum: ["ADMIN", "SUPERVISOR", "CHATTER", "worker", "admin"],
      required: true,
      default: "CHATTER",
    },
    active: { type: Boolean, required: true, default: true },
    suspendedAt: { type: Date, default: null },
    mustChangePassword: { type: Boolean, required: true, default: false },
    lastLoginAt: { type: Date, default: null },
    failedLoginAttempts: { type: Number, required: true, default: 0 },
    lockedUntil: { type: Date, default: null },
    tokenVersion: { type: Number, required: true, default: 0 },
    commissionRate: { type: Number, min: 0, max: 100, default: 0 },
  },
  { timestamps: true },
);

export const Worker = model("Worker", workerSchema);
export type WorkerRole = "ADMIN" | "SUPERVISOR" | "CHATTER" | "worker" | "admin";
