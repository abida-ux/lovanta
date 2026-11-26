import "dotenv/config";
import { z } from "zod";

const envSchema = z.object({
  PORT: z.coerce.number().int().positive().default(4000),
  MONGODB_URI: z.string().min(1, "MONGODB_URI is required"),
  JWT_SECRET: z.string().min(32, "JWT_SECRET must be at least 32 characters"),
  CORS_ORIGINS: z.string().default("http://localhost:5173"),
  BOOTSTRAP_ADMIN_EMAIL: z.string().email().optional(),
  BOOTSTRAP_ADMIN_NAME: z.string().trim().min(1).max(100).optional(),
  R2_ACCOUNT_ID: z.string().trim().min(1).optional(),
  R2_ACCESS_KEY_ID: z.string().trim().min(1).optional(),
  R2_SECRET_ACCESS_KEY: z.string().trim().min(1).optional(),
  R2_ENDPOINT: z.string().trim().url().optional(),
  R2_BUCKET_PUBLIC: z.string().trim().min(1).optional(),
  R2_BUCKET_PRIVATE: z.string().trim().min(1).optional(),
  R2_PUBLIC_BASE_URL: z.string().trim().url().optional(),
});

export const env = envSchema.parse(process.env);
export const corsOrigins = env.CORS_ORIGINS.split(",")
  .map((origin) => origin.trim())
  .filter(Boolean);
