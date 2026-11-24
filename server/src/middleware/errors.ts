import type { ErrorRequestHandler } from "express";
import mongoose from "mongoose";
import { HttpError } from "../errors/HttpError.ts";

export const handleErrors: ErrorRequestHandler = (error, _req, res, _next) => {
  if (error instanceof HttpError) {
    res.status(error.statusCode).json({
      error: error.message,
      ...(error.details === undefined ? {} : { details: error.details }),
    });
    return;
  }

  if (error instanceof mongoose.Error.ValidationError || error instanceof mongoose.Error.CastError) {
    res.status(400).json({ error: "Invalid data" });
    return;
  }

  if (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    error.code === 11000
  ) {
    res.status(409).json({ error: "A record with that value already exists" });
    return;
  }

  console.error("Unhandled API error", error);
  res.status(500).json({ error: "Internal server error" });
};
