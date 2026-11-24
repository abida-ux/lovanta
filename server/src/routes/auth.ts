import { Router } from "express";
import { rateLimit } from "express-rate-limit";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { z } from "zod";
import { env } from "../config/env.ts";
import { HttpError } from "../errors/HttpError.ts";
import { authenticateWorker, getPermissions } from "../middleware/authenticateWorker.ts";
import { Worker } from "../models/Worker.ts";
import { parseInput } from "../utils/parseInput.ts";

const router = Router();
const loginSchema = z.object({
  email: z.string().trim().email().max(254),
  password: z.string().min(1).max(128),
});

const loginRateLimit = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: "draft-8",
  legacyHeaders: false,
});

async function login(email: string, password: string) {
  const worker = await Worker.findOne({
    email: email.toLowerCase(),
    active: true,
    suspendedAt: null,
  }).select("+passwordHash");

  if (!worker) {
    throw new HttpError(401, "Email or password is incorrect");
  }
  if (worker.lockedUntil && worker.lockedUntil > new Date()) {
    throw new HttpError(423, "Account is temporarily locked");
  }

  if (!(await bcrypt.compare(password, worker.passwordHash))) {
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

  return {
    token,
    redirect,
    mustChangePassword: worker.mustChangePassword,
    worker: {
      id: worker._id.toString(),
      email: worker.email,
      username: worker.username ?? null,
      displayName: worker.displayName,
      role,
      permissions: getPermissions(role),
    },
  };
}

router.post("/login", loginRateLimit, async (req, res) => {
  const input = parseInput(loginSchema, req.body);
  res.json(await login(input.email, input.password));
});

router.get("/me", authenticateWorker, (req, res) => {
  res.json({ worker: req.authenticatedWorker });
});

export default router;
