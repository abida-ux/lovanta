import type { RequestHandler } from "express";
import jwt from "jsonwebtoken";
import type { WorkerRole } from "../models/Worker.ts";
import { env } from "../config/env.ts";
import { Worker } from "../models/Worker.ts";
import { ensureStaffNumber } from "../utils/staffNumber.ts";

export function getPermissions(role: WorkerRole): string[] {
  if (role === "ADMIN" || role === "admin") {
    return ["*"];
  }
  if (role === "SUPERVISOR") {
    return [
      "dashboard.read",
      "models.read",
      "chatters.read",
      "conversations.read",
      "ppv.read",
      "transactions.read",
      "reports.read",
      "audit.read",
    ];
  }
  return ["chat.read", "chat.send", "models.assigned"];
}

export const authenticateWorker: RequestHandler = async (req, res, next) => {
  const authorization = req.header("authorization");
  if (!authorization?.startsWith("Bearer ")) {
    res.status(401).json({ error: "Authentication required" });
    return;
  }

  let payload: jwt.JwtPayload | string;
  try {
    payload = jwt.verify(authorization.slice("Bearer ".length), env.JWT_SECRET);
  } catch (error) {
    if (error instanceof jwt.JsonWebTokenError) {
      res.status(401).json({ error: "Invalid or expired token" });
      return;
    }
    next(error);
    return;
  }

  if (typeof payload === "string" || typeof payload.sub !== "string") {
    res.status(401).json({ error: "Invalid or expired token" });
    return;
  }

  const worker = await Worker.findOne({
    _id: payload.sub,
    active: true,
    suspendedAt: null,
    status: { $in: ["ACTIVE", null] },
  })
    .select("_id email displayName role tokenVersion staffNumber")
    .lean();

  if (!worker) {
    res.status(401).json({ error: "Worker account is inactive or unavailable" });
    return;
  }
  const tokenVersion = typeof payload.ver === "number" ? payload.ver : 0;
  if (tokenVersion !== (worker.tokenVersion ?? 0)) {
    res.status(401).json({ error: "Session has been revoked" });
    return;
  }

  const staffNumber = typeof worker.staffNumber === "number"
    ? worker.staffNumber
    : await ensureStaffNumber(worker._id.toString());
  req.authenticatedWorker = {
    id: worker._id.toString(),
    staffNumber,
    email: worker.email,
    displayName: worker.displayName,
    role: worker.role,
    permissions: getPermissions(worker.role),
  };
  next();
};
