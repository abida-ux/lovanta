import { Router } from "express";
import { rateLimit } from "express-rate-limit";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { z } from "zod";
import { env } from "../config/env.ts";
import { HttpError } from "../errors/HttpError.ts";
import { authenticateWorker } from "../middleware/authenticateWorker.ts";
import { getPermissions } from "../middleware/authenticateWorker.ts";
import { Worker } from "../models/Worker.ts";
import { parseInput } from "../utils/parseInput.ts";
import { ensureStaffNumber } from "../utils/staffNumber.ts";

const router = Router();
const loginRateLimit = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: "draft-8",
  legacyHeaders: false,
});

router.post("/login", loginRateLimit, async (req, res) => {
  const input = parseInput(
    z.object({
      email: z.string().trim().min(1).max(254),
      password: z.string().min(1).max(128),
    }),
    req.body,
  );
  const identifier = input.email.toLowerCase();
  const worker = await Worker.findOne({ email: identifier }).select("+passwordHash")
    ?? await Worker.findOne({ username: identifier }).select("+passwordHash");
  const accountStatus = worker?.status
    ?? (worker?.active && !worker.suspendedAt ? "ACTIVE" : "DISABLED");
  if (!worker || !worker.active || worker.suspendedAt || accountStatus !== "ACTIVE") {
    throw new HttpError(401, "Email or password is incorrect");
  }
  if (worker.lockedUntil && worker.lockedUntil > new Date()) {
    throw new HttpError(423, "Account is temporarily locked");
  }

  if (!(await bcrypt.compare(input.password, worker.passwordHash))) {
    worker.failedLoginAttempts += 1;
    if (worker.failedLoginAttempts >= 10) {
      worker.lockedUntil = new Date(Date.now() + 15 * 60 * 1000);
    }
    await worker.save();
    throw new HttpError(401, "Email or password is incorrect");
  }

  worker.failedLoginAttempts = 0;
  worker.lockedUntil = null;
  worker.lastLoginAt = new Date();
  worker.staffNumber = await ensureStaffNumber(worker._id.toString());
  await worker.save();

  const role =
    worker.role === "admin"
      ? "ADMIN"
      : worker.role === "worker"
        ? "CHATTER"
        : worker.role;
  const redirect =
    role === "ADMIN"
      ? "/admin/dashboard"
      : role === "SUPERVISOR"
        ? "/admin/operations"
        : "/chatter/inbox";
  const token = jwt.sign({ role, ver: worker.tokenVersion ?? 0 }, env.JWT_SECRET, {
    subject: worker._id.toString(),
    expiresIn: "12h",
  });

  res.json({
    token,
    redirect,
    mustChangePassword: worker.mustChangePassword,
    worker: {
      id: worker._id.toString(),
      staffNumber: worker.staffNumber,
      email: worker.email,
      username: worker.username ?? null,
      displayName: worker.displayName,
      role,
      permissions: getPermissions(role),
    },
  });
});

router.post("/logout", authenticateWorker, (_req, res) => {
  res.json({ success: true });
});

router.post("/password/change", authenticateWorker, async (req, res) => {
  const input = parseInput(
    z.object({
      currentPassword: z.string().min(1).max(128),
      newPassword: z.string().min(12).max(128),
    }),
    req.body,
  );
  const worker = await Worker.findById(req.authenticatedWorker?.id).select("+passwordHash");
  if (!worker || !(await bcrypt.compare(input.currentPassword, worker.passwordHash))) {
    throw new HttpError(400, "Current password is incorrect");
  }
  worker.passwordHash = await bcrypt.hash(input.newPassword, 12);
  worker.mustChangePassword = false;
  worker.tokenVersion += 1;
  await worker.save();
  const token = jwt.sign({ role: worker.role, ver: worker.tokenVersion }, env.JWT_SECRET, {
    subject: worker._id.toString(),
    expiresIn: "12h",
  });
  res.json({ success: true, token });
});

export default router;
