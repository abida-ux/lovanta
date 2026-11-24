import type { ZodType } from "zod";
import { HttpError } from "../errors/HttpError.ts";

export function parseInput<T>(schema: ZodType<T>, input: unknown): T {
  const result = schema.safeParse(input);
  if (!result.success) {
    throw new HttpError(400, "Invalid request", result.error.flatten());
  }
  return result.data;
}
