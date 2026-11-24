import type { RequestHandler } from "express";
import { HttpError } from "../errors/HttpError.ts";

export function authorize(...roles: string[]): RequestHandler {
  return (req, _res, next) => {
    const worker = req.authenticatedWorker;
    if (!worker) {
      throw new HttpError(401, "Authentication required");
    }

    const normalizedRole =
      worker.role === "admin"
        ? "ADMIN"
        : worker.role === "worker"
          ? "CHATTER"
          : worker.role;

    if (!roles.includes(normalizedRole)) {
      throw new HttpError(403, "Insufficient permissions");
    }
    next();
  };
}

export function authorizePermission(permission: string): RequestHandler {
  return (req, _res, next) => {
    const worker = req.authenticatedWorker;
    if (!worker) {
      throw new HttpError(401, "Authentication required");
    }
    if (!worker.permissions.includes("*") && !worker.permissions.includes(permission)) {
      throw new HttpError(403, "Insufficient permissions");
    }
    next();
  };
}
