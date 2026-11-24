import mongoose from "mongoose";
import app from "./app.ts";
import { env } from "./config/env.ts";

async function startServer(): Promise<void> {
  await mongoose.connect(env.MONGODB_URI);
  app.listen(env.PORT, () => {
    console.log(`Lovanta API listening on port ${env.PORT}`);
  });
}

startServer().catch((error: unknown) => {
  console.error("Failed to start Lovanta API", error);
  process.exitCode = 1;
});
