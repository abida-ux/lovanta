import { Router } from "express";
import { authenticateWorker } from "../middleware/authenticateWorker.ts";

const router = Router();

router.get("/me", authenticateWorker, (req, res) => {
  res.json({ worker: req.authenticatedWorker });
});

export default router;
