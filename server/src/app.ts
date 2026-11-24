import cors from "cors";
import express from "express";
import helmet from "helmet";
import { corsOrigins } from "./config/env.ts";
import { handleErrors } from "./middleware/errors.ts";
import authRouter from "./routes/auth.ts";
import workspaceRouter from "./routes/workspace.ts";
import staffAuthRouter from "./routes/staffAuth.ts";
import adminRouter from "./routes/admin.ts";

const app = express();

app.disable("x-powered-by");
app.use(helmet());
app.use(
  cors({
    origin(origin, callback) {
      if (!origin || corsOrigins.includes(origin)) {
        callback(null, true);
        return;
      }
      callback(null, false);
    },
  }),
);
app.use(express.json({ limit: "32kb" }));

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});
app.use("/api/v1/auth", authRouter);
app.use("/api/staff/auth", staffAuthRouter);
app.use("/api/v1/admin", adminRouter);
app.use("/api/v1", workspaceRouter);
app.use(handleErrors);

export default app;
