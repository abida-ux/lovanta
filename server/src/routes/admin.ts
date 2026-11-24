import bcrypt from "bcryptjs";
import type { Request } from "express";
import { Router } from "express";
import { Types } from "mongoose";
import crypto from "node:crypto";
import { z } from "zod";
import { authenticateWorker } from "../middleware/authenticateWorker.ts";
import { authorize, authorizePermission } from "../middleware/authorize.ts";
import { AuditLog } from "../models/AuditLog.ts";
import { ChatMessage } from "../models/ChatMessage.ts";
import { Commission } from "../models/Commission.ts";
import { Conversation } from "../models/Conversation.ts";
import { ModelAccount } from "../models/ModelAccount.ts";
import { PPVPurchase } from "../models/PPVPurchase.ts";
import { Subscriber } from "../models/Subscriber.ts";
import { Transaction } from "../models/Transaction.ts";
import { Worker } from "../models/Worker.ts";
import { WorkerAssignment } from "../models/WorkerAssignment.ts";
import { HttpError } from "../errors/HttpError.ts";
import { parseInput } from "../utils/parseInput.ts";

const router = Router();
router.use(authenticateWorker, authorize("ADMIN", "SUPERVISOR"));

const paginationSchema = z.object({
  page: z.coerce.number().int().min(1).default(1),
  limit: z.coerce.number().int().min(1).max(100).default(25),
  search: z.string().trim().max(100).optional(),
});
const modelListSchema = paginationSchema.extend({
  status: z.enum(["active", "inactive", "suspended", "archived"]).optional(),
});
const objectIdSchema = z.string().regex(/^[a-f\d]{24}$/i);

async function audit(
  req: Request,
  action: string,
  affectedType: string,
  affectedId: string,
  before: unknown = null,
  after: unknown = null,
) {
  const worker = req.authenticatedWorker;
  if (!worker) return;
  await AuditLog.create({
    actorId: worker.id,
    actorRole: worker.role,
    action,
    affectedType,
    affectedId,
    before,
    after,
    ipAddress: req.ip ?? "",
    userAgent: req.header("user-agent") ?? "",
  });
}

router.get("/dashboard", authorizePermission("dashboard.read"), async (_req, res) => {
  const [
    subscribers,
    activeSubscribers,
    models,
    activeModels,
    chatters,
    conversations,
    activeConversations,
    ppvMessages,
    ppvPurchases,
    successfulTransactions,
    pendingTransactions,
    refunds,
  ] = await Promise.all([
    Subscriber.countDocuments(),
    Subscriber.countDocuments({ status: "active" }),
    ModelAccount.countDocuments({ status: { $ne: "archived" } }),
    ModelAccount.countDocuments({ active: true, status: { $ne: "archived" } }),
    Worker.countDocuments({ role: { $in: ["CHATTER", "worker"] } }),
    Conversation.countDocuments(),
    Conversation.countDocuments({ status: "active" }),
    ChatMessage.countDocuments({ senderType: "chatter" }),
    PPVPurchase.countDocuments(),
    Transaction.countDocuments({ status: "successful" }),
    Transaction.countDocuments({ status: "pending" }),
    Transaction.countDocuments({ status: "refunded" }),
  ]);
  const startOfDay = new Date();
  startOfDay.setHours(0, 0, 0, 0);
  const startOfWeek = new Date(startOfDay);
  startOfWeek.setDate(startOfWeek.getDate() - startOfWeek.getDay());
  const startOfMonth = new Date(startOfDay);
  startOfMonth.setDate(1);
  const sumByStatusAndDate = async (status: string, from: Date) =>
    (await Transaction.aggregate([
      { $match: { status, createdAt: { $gte: from } } },
      { $group: { _id: null, total: { $sum: "$amount" } } },
    ]))[0]?.total ?? 0;

  res.json({
    subscribers,
    activeSubscribers,
    models,
    activeModels,
    chatters,
    conversations,
    activeConversations,
    ppvMessages,
    ppvPurchases,
    totalRevenue: await sumByStatusAndDate("successful", new Date(0)),
    revenueToday: await sumByStatusAndDate("successful", startOfDay),
    revenueWeek: await sumByStatusAndDate("successful", startOfWeek),
    revenueMonth: await sumByStatusAndDate("successful", startOfMonth),
    pendingTransactions,
    successfulTransactions,
    refunds,
  });
});

router.get("/models", authorizePermission("models.read"), async (req, res) => {
  const { page, limit, search, status } = parseInput(modelListSchema, req.query);
  const conditions: Record<string, unknown>[] = [];
  if (search) {
    const escapedSearch = search.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const searchConditions: Record<string, unknown>[] = [
      { displayName: { $regex: escapedSearch, $options: "i" } },
      { username: { $regex: escapedSearch, $options: "i" } },
    ];
    if (/^[a-f\d]{24}$/i.test(search)) {
      searchConditions.push({ _id: new Types.ObjectId(search) });
    }
    conditions.push({
      $or: searchConditions,
    });
  }
  if (status === "active") {
    conditions.push({
      $or: [{ status: "active" }, { status: { $exists: false }, active: true }],
    });
  } else if (status === "inactive") {
    conditions.push({
      $or: [{ status: "inactive" }, { status: { $exists: false }, active: false }],
    });
  } else if (status) {
    conditions.push({ status });
  }
  const filter = conditions.length > 0 ? { $and: conditions } : {};
  const [items, total] = await Promise.all([
    ModelAccount.find(filter).sort({ createdAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
    ModelAccount.countDocuments(filter),
  ]);
  res.json({
    items: items.map((item) => ({ ...item, modelId: item._id.toString() })),
    total,
    page,
    limit,
  });
});

router.get("/models/:id", authorizePermission("models.read"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const modelAccount = await ModelAccount.findById(id).lean();
  if (!modelAccount) throw new HttpError(404, "Model not found");
  const assignments = await WorkerAssignment.find({ modelAccountId: id, active: true })
    .populate("workerId", "displayName username email role active")
    .sort({ assignedAt: -1 })
    .lean();
  res.json({
    model: { ...modelAccount, modelId: modelAccount._id.toString() },
    assignments,
  });
});

router.post("/models", authorize("ADMIN"), async (req, res) => {
  const input = parseInput(
    z.object({
      displayName: z.string().trim().min(1).max(100),
      username: z.string().trim().min(1).max(100).optional(),
      avatarUrl: z.string().trim().max(1000).optional(),
      description: z.string().trim().max(2000).optional(),
      galleryUrls: z.array(z.string().trim().max(1000)).max(100).optional(),
      availability: z.enum(["available", "busy", "offline"]).optional(),
      verified: z.boolean().optional(),
      ageVerified: z.boolean().optional(),
      consentRecorded: z.boolean().optional(),
      internalNotes: z.string().trim().max(5000).optional(),
    }),
    req.body,
  );
  const modelAccount = new ModelAccount({
    displayName: input.displayName,
    ...(input.username !== undefined ? { username: input.username } : {}),
    ...(input.avatarUrl !== undefined ? { avatarUrl: input.avatarUrl } : {}),
    ...(input.description !== undefined ? { description: input.description } : {}),
    ...(input.galleryUrls !== undefined ? { galleryUrls: input.galleryUrls } : {}),
    ...(input.availability !== undefined ? { availability: input.availability } : {}),
    ...(input.verified !== undefined ? { verified: input.verified } : {}),
    ...(input.ageVerified !== undefined ? { ageVerified: input.ageVerified } : {}),
    ...(input.consentRecorded !== undefined ? { consentRecorded: input.consentRecorded } : {}),
    ...(input.internalNotes !== undefined ? { internalNotes: input.internalNotes } : {}),
  });
  await modelAccount.save();
  await audit(req, "model.create", "ModelAccount", modelAccount._id.toString(), null, input);
  res.status(201).json({ model: modelAccount, modelId: modelAccount._id.toString() });
});

router.patch("/models/:id", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const before = await ModelAccount.findById(id).lean();
  if (!before) throw new HttpError(404, "Model not found");
  const input = parseInput(
    z.object({
      displayName: z.string().trim().min(1).max(100).optional(),
      username: z.string().trim().min(1).max(100).optional(),
      avatarUrl: z.string().trim().max(1000).optional(),
      description: z.string().trim().max(2000).optional(),
      galleryUrls: z.array(z.string().trim().max(1000)).max(100).optional(),
      status: z.enum(["active", "inactive", "suspended", "archived"]).optional(),
      active: z.boolean().optional(),
      availability: z.enum(["available", "busy", "offline"]).optional(),
      verified: z.boolean().optional(),
      ageVerified: z.boolean().optional(),
      consentRecorded: z.boolean().optional(),
      internalNotes: z.string().trim().max(5000).optional(),
    }),
    req.body,
  );
  const update: Record<string, unknown> = { ...input };
  if (input.status !== undefined) {
    update.active = input.status === "active";
    update.archivedAt = input.status === "archived" ? new Date() : null;
  } else if (input.active !== undefined) {
    update.status = input.active ? "active" : "inactive";
    update.archivedAt = null;
  }
  const modelAccount = await ModelAccount.findByIdAndUpdate(id, update, { new: true, runValidators: true });
  if (!modelAccount) throw new HttpError(404, "Model not found");
  await audit(req, "model.update", "ModelAccount", id, before, modelAccount);
  res.json({ model: modelAccount, modelId: modelAccount._id.toString() });
});

router.get("/chatters", authorizePermission("chatters.read"), async (req, res) => {
  const { page, limit, search } = parseInput(paginationSchema, req.query);
  const filter: Record<string, unknown> = { role: { $in: ["CHATTER", "SUPERVISOR", "worker"] } };
  if (search) {
    filter.$or = [
      { displayName: { $regex: search, $options: "i" } },
      { email: { $regex: search, $options: "i" } },
      { username: { $regex: search, $options: "i" } },
    ];
  }
  const [items, total] = await Promise.all([
    Worker.find(filter).select("-passwordHash").sort({ createdAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
    Worker.countDocuments(filter),
  ]);
  res.json({ items, total, page, limit });
});

async function nextGeneratedChatterEmail(): Promise<string> {
  const accounts = await Worker.find({ email: /^chatter\d+@gmail\.com$/i })
    .select("email")
    .lean();
  const highestNumber = accounts.reduce((highest, account) => {
    const match = /^chatter(\d+)@gmail\.com$/i.exec(account.email);
    return match ? Math.max(highest, Number(match[1])) : highest;
  }, 0);
  return `chatter${highestNumber + 1}@gmail.com`;
}

function isDuplicateEmailError(error: unknown): boolean {
  if (
    typeof error !== "object"
    || error === null
    || !("code" in error)
    || error.code !== 11000
  ) {
    return false;
  }
  const duplicateKey = "keyPattern" in error ? error.keyPattern : undefined;
  return typeof duplicateKey === "object"
    && duplicateKey !== null
    && "email" in duplicateKey;
}

router.post("/chatters", authorize("ADMIN"), async (req, res) => {
  const input = parseInput(
    z.object({
      displayName: z.string().trim().min(1).max(100),
      username: z.string().trim().min(1).max(100).optional(),
      email: z.string().trim().email().max(254).optional(),
      role: z.enum(["CHATTER", "SUPERVISOR"]).default("CHATTER"),
      commissionRate: z.number().min(0).max(100).optional(),
      temporaryPassword: z.string().min(12).max(128).optional(),
    }),
    req.body,
  );
  const temporaryPassword = input.temporaryPassword ?? crypto.randomBytes(9).toString("base64url");
  const passwordHash = await bcrypt.hash(temporaryPassword, 12);
  const createWorker = (email: string) => new Worker({
    displayName: input.displayName,
    email,
    role: input.role,
    ...(input.username !== undefined ? { username: input.username } : {}),
    ...(input.commissionRate !== undefined ? { commissionRate: input.commissionRate } : {}),
    passwordHash,
    mustChangePassword: true,
  });
  let worker = createWorker(
    input.email?.toLowerCase() ?? await nextGeneratedChatterEmail(),
  );
  for (let attempt = 0; ; attempt++) {
    try {
      await worker.save();
      break;
    } catch (error) {
      if (input.email !== undefined || !isDuplicateEmailError(error) || attempt >= 4) {
        throw error;
      }
      worker = createWorker(await nextGeneratedChatterEmail());
    }
  }
  await audit(req, "staff.create", "Worker", worker._id.toString(), null, {
    displayName: worker.displayName,
    email: worker.email,
    role: worker.role,
  });
  res.status(201).json({
    chatter: { id: worker._id, displayName: worker.displayName, email: worker.email, role: worker.role },
    temporaryPassword,
  });
});

router.post("/chatters/:id/password-reset", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const worker = await Worker.findById(id);
  if (!worker) throw new HttpError(404, "Staff account not found");
  const temporaryPassword = crypto.randomBytes(9).toString("base64url");
  worker.passwordHash = await bcrypt.hash(temporaryPassword, 12);
  worker.mustChangePassword = true;
  worker.tokenVersion += 1;
  await worker.save();
  await audit(req, "staff.password_reset", "Worker", id);
  res.json({ temporaryPassword, mustChangePassword: true });
});

router.patch("/chatters/:id/status", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const input = parseInput(z.object({ suspended: z.boolean() }), req.body);
  const before = await Worker.findById(id).select("-passwordHash").lean();
  if (!before) throw new HttpError(404, "Staff account not found");
  const worker = await Worker.findByIdAndUpdate(
    id,
    {
      active: !input.suspended,
      suspendedAt: input.suspended ? new Date() : null,
      $inc: { tokenVersion: 1 },
    },
    { new: true, runValidators: true },
  ).select("-passwordHash");
  await audit(req, input.suspended ? "staff.suspend" : "staff.reactivate", "Worker", id, before, worker);
  res.json({ chatter: worker });
});

router.post("/assignments", authorize("ADMIN"), async (req, res) => {
  const input = parseInput(
    z.object({
      modelAccountId: objectIdSchema,
      workerIds: z.array(objectIdSchema).min(1).max(100),
    }),
    req.body,
  );
  const modelAccount = await ModelAccount.findOne({
    _id: input.modelAccountId,
    active: true,
    $or: [{ status: "active" }, { status: { $exists: false } }],
  }).select("_id");
  if (!modelAccount) throw new HttpError(404, "Active model not found");
  const workerIds = [...new Set(input.workerIds)];
  const eligibleWorkers = await Worker.find({
    _id: { $in: workerIds },
    role: { $in: ["CHATTER", "worker"] },
    active: true,
    suspendedAt: null,
  }).select("_id").lean();
  if (eligibleWorkers.length !== workerIds.length) {
    throw new HttpError(400, "Only active chatters can be assigned to a model");
  }
  const actorId = req.authenticatedWorker?.id;
  const assignments: unknown[] = [];
  for (const workerId of workerIds) {
    let existing = await WorkerAssignment.findOne({
      workerId,
      modelAccountId: input.modelAccountId,
    });
    if (existing?.active) continue;
    const assignedAt = new Date();
    if (existing) {
      existing.active = true;
      existing.status = "active";
      existing.assignedBy = actorId ? new Types.ObjectId(actorId) : null;
      existing.assignedAt = assignedAt;
      existing.removedAt = null;
      existing.history.push({ action: "assigned", actorId: actorId ? new Types.ObjectId(actorId) : null, at: assignedAt });
    } else {
      existing = new WorkerAssignment({
        workerId,
        modelAccountId: input.modelAccountId,
        active: true,
        status: "active",
        assignedBy: actorId ?? null,
        assignedAt,
        history: [{ action: "assigned", actorId: actorId ?? null, at: assignedAt }],
      });
    }
    await existing.save();
    await audit(req, "assignment.create", "WorkerAssignment", existing._id.toString(), null, {
      workerId,
      modelAccountId: input.modelAccountId,
    });
    assignments.push(existing);
  }
  res.status(201).json({ assignments });
});

router.delete("/assignments/:id", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const before = await WorkerAssignment.findById(id).lean();
  if (!before) throw new HttpError(404, "Assignment not found");
  const assignment = await WorkerAssignment.findByIdAndUpdate(
    id,
    {
      active: false,
      status: "removed",
      removedAt: new Date(),
      $push: {
        history: {
          action: "removed",
          actorId: req.authenticatedWorker?.id,
          at: new Date(),
        },
      },
    },
    { new: true },
  );
  await audit(req, "assignment.remove", "WorkerAssignment", id, before, assignment);
  res.json({ assignment });
});

router.get("/assignments", authorize("ADMIN"), async (_req, res) => {
  const assignments = await WorkerAssignment.find()
    .populate("workerId", "displayName email role")
    .populate("modelAccountId", "displayName username")
    .sort({ assignedAt: -1 })
    .lean();
  res.json({ assignments });
});

router.get("/subscribers", authorize("ADMIN"), async (req, res) => {
  const { page, limit, search } = parseInput(paginationSchema, req.query);
  const filter = search
    ? { $or: [{ username: { $regex: search, $options: "i" } }, { email: { $regex: search, $options: "i" } }] }
    : {};
  const [items, total] = await Promise.all([
    Subscriber.find(filter).sort({ createdAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
    Subscriber.countDocuments(filter),
  ]);
  res.json({ items, total, page, limit });
});

router.patch("/subscribers/:id/status", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const input = parseInput(z.object({ status: z.enum(["active", "suspended", "blocked"]) }), req.body);
  const before = await Subscriber.findById(id).lean();
  if (!before) throw new HttpError(404, "Subscriber not found");
  const subscriber = await Subscriber.findByIdAndUpdate(
    id,
    {
      status: input.status,
      suspendedAt: input.status === "active" ? null : new Date(),
    },
    { new: true, runValidators: true },
  );
  await audit(req, "subscriber.status_change", "Subscriber", id, before, subscriber);
  res.json({ subscriber });
});

router.get("/conversations", authorizePermission("conversations.read"), async (req, res) => {
  const { page, limit, search } = parseInput(paginationSchema, req.query);
  const filter: Record<string, unknown> = {};
  if (search) filter.subscriberId = { $regex: search, $options: "i" };
  const [items, total] = await Promise.all([
    Conversation.find(filter).sort({ lastMessageAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
    Conversation.countDocuments(filter),
  ]);
  res.json({ items, total, page, limit });
});

router.get("/transactions", authorizePermission("transactions.read"), async (req, res) => {
  const { page, limit } = parseInput(paginationSchema, req.query);
  const [items, total] = await Promise.all([
    Transaction.find().sort({ createdAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
    Transaction.countDocuments(),
  ]);
  res.json({ items, total, page, limit });
});

router.get("/ppv", authorizePermission("ppv.read"), async (req, res) => {
  const { page, limit } = parseInput(paginationSchema, req.query);
  const [items, total] = await Promise.all([
    PPVPurchase.find().sort({ createdAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
    PPVPurchase.countDocuments(),
  ]);
  res.json({ items, total, page, limit });
});

router.get("/commissions", authorize("ADMIN"), async (req, res) => {
  const { page, limit } = parseInput(paginationSchema, req.query);
  const [items, total] = await Promise.all([
    Commission.find().populate("chatterId", "displayName email").sort({ createdAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
    Commission.countDocuments(),
  ]);
  res.json({ items, total, page, limit });
});

router.get("/audit", authorizePermission("audit.read"), async (req, res) => {
  const { page, limit } = parseInput(paginationSchema, req.query);
  const [items, total] = await Promise.all([
    AuditLog.find().sort({ createdAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
    AuditLog.countDocuments(),
  ]);
  res.json({ items, total, page, limit });
});

router.get("/reports/:type.csv", authorize("ADMIN"), async (req, res) => {
  const type = req.params.type;
  let items: Array<Record<string, unknown>> = [];
  if (type === "transactions") items = await Transaction.find().lean();
  else if (type === "ppv") items = await PPVPurchase.find().lean();
  else if (type === "subscribers") items = await Subscriber.find().lean();
  else if (type === "commissions") items = await Commission.find().lean();
  else throw new HttpError(400, "Unsupported report type");

  const fields = Array.from(new Set(items.flatMap((item) => Object.keys(item))));
  const escape = (value: unknown) => `"${String(value ?? "").replace(/"/g, '""')}"`;
  const csv = [
    fields.map(escape).join(","),
    ...items.map((item) => fields.map((field) => escape(item[field])).join(",")),
  ].join("\n");
  res.setHeader("Content-Type", "text/csv");
  res.setHeader("Content-Disposition", `attachment; filename="${type}.csv"`);
  res.send(csv);
});

export default router;
