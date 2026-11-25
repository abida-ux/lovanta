import type { WorkerRole } from "../models/Worker.ts";

declare global {
  namespace Express {
    interface Request {
      authenticatedWorker?: {
        id: string;
        staffNumber: number;
        email: string;
        displayName: string;
        role: WorkerRole;
        permissions: string[];
      };
    }
  }
}

export {};
