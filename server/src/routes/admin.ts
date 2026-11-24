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
import { Sequence } from "../models/Sequence.ts";
import { HttpError } from "../errors/HttpError.ts";
import { generateStrongPassword } from "../utils/generateStrongPassword.ts";
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
const CHATTER_SEQUENCE_ID = "chatter-login";
const CHATTER_EMAIL_PATTERN = /^chatter(\d+)@gmail\.com$/i;
const CHATTER_PROFILE_FIELDS =
  "email displayName role status active suspendedAt createdAt lastLoginAt createdBy disabledAt disabledReason bannedAt banReason archivedAt";

type AuditDetails = {
  chatterId?: string;
  reason?: string;
};

async function audit(
  req: Request,
  action: string,
  affectedType: string,
  affectedId: string,
  before: unknown = null,
  after: unknown = null,
  details: AuditDetails = {},
) {
  const worker = req.authenticatedWorker;
  if (!worker) return;
  await AuditLog.create({
    actorId: worker.id,
    actorRole: worker.role,
    chatterId: details.chatterId ?? null,
    action,
    affectedType,
    affectedId,
    reason: details.reason ?? "",
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
    totalModels,
    activeModels,
    chatters,
    conversations,
    activeConversations,
    ppvMessages,
    ppvPurchases,
    paidPpvPurchases,
    successfulTransactions,
    pendingTransactions,
    refunds,
  ] = await Promise.all([
    Subscriber.countDocuments(),
    Subscriber.countDocuments({ status: "active" }),
    ModelAccount.countDocuments({ status: { $ne: "archived" } }),
    ModelAccount.countDocuments(),
    ModelAccount.countDocuments({ active: true, status: { $ne: "archived" } }),
    Worker.countDocuments({ role: { $in: ["CHATTER", "worker"] } }),
    Conversation.countDocuments(),
    Conversation.countDocuments({ status: "active" }),
    ChatMessage.countDocuments({ senderType: "chatter" }),
    PPVPurchase.countDocuments(),
    PPVPurchase.countDocuments({ status: "paid" }),
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
  const [
    activeChatters,
    disabledAccounts,
    recentRecruitment,
    recentActions,
    commissionTotals,
  ] = await Promise.all([
    Worker.countDocuments({
      role: { $in: ["CHATTER", "worker"] },
      active: true,
      suspendedAt: null,
      status: { $in: ["ACTIVE", null] },
    }),
    Worker.countDocuments({
      role: { $in: ["CHATTER", "worker"] },
      $or: [
        { status: "DISABLED" },
        { status: "ACTIVE", $or: [{ active: false }, { suspendedAt: { $ne: null } }] },
        { status: { $exists: false }, $or: [{ active: false }, { suspendedAt: { $ne: null } }] },
      ],
    }),
    Worker.find({ role: { $in: ["CHATTER", "worker"] } })
      .select("displayName email status active suspendedAt createdAt")
      .sort({ createdAt: -1 })
      .limit(5)
      .lean(),
    AuditLog.find()
      .select("actorId chatterId action affectedType affectedId reason createdAt")
      .sort({ createdAt: -1 })
      .limit(5)
      .lean(),
    Commission.aggregate([
      { $group: { _id: "$status", amount: { $sum: "$amount" }, count: { $sum: 1 } } },
    ]),
  ]);
  const commissionByStatus = new Map(commissionTotals.map((row) => [row._id, row]));

  res.json({
    subscribers,
    activeSubscribers,
    models,
    totalModels,
    activeModels,
    chatters,
    totalChatters: chatters,
    activeChatters,
    disabledAccounts,
    recentRecruitment: recentRecruitment.map((chatter) => ({
      id: chatter._id.toString(),
      displayName: chatter.displayName,
      email: chatter.email,
      status: chatterStatus(chatter),
      createdAt: chatter.createdAt,
    })),
    recentActions: recentActions.map((entry) => ({
      id: entry._id.toString(),
      adminId: entry.actorId.toString(),
      chatterId: entry.chatterId?.toString() ?? null,
      action: entry.action,
      affectedType: entry.affectedType,
      affectedId: entry.affectedId,
      reason: entry.reason,
      createdAt: entry.createdAt,
    })),
    conversations,
    activeConversations,
    ppvMessages,
    ppvPurchases,
    paidPpvPurchases,
    totalRevenue: await sumByStatusAndDate("successful", new Date(0)),
    revenueToday: await sumByStatusAndDate("successful", startOfDay),
    revenueWeek: await sumByStatusAndDate("successful", startOfWeek),
    revenueMonth: await sumByStatusAndDate("successful", startOfMonth),
    pendingTransactions,
    successfulTransactions,
    refunds,
    paidCommissions: commissionByStatus.get("paid")?.amount ?? 0,
    pendingCommissions: commissionByStatus.get("pending")?.amount ?? 0,
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

const chatterListSchema = paginationSchema.extend({
  status: z.enum(["ACTIVE", "DISABLED", "BANNED", "ARCHIVED"]).optional(),
});

function chatterStatus(worker: {
  status?: string;
  active?: boolean;
  suspendedAt?: Date | null;
}): "ACTIVE" | "DISABLED" | "BANNED" | "ARCHIVED" {
  if (worker.status === "BANNED" || worker.status === "ARCHIVED") return worker.status;
  if (worker.status === "DISABLED" || worker.active === false || worker.suspendedAt) {
    return "DISABLED";
  }
  return "ACTIVE";
}

function isDuplicateKeyError(error: unknown): boolean {
  return typeof error === "object"
    && error !== null
    && "code" in error
    && error.code === 11000;
}

async function initializeChatterSequence(): Promise<void> {
  if (await Sequence.exists({ _id: CHATTER_SEQUENCE_ID })) return;

  const accounts = await Worker.find({ email: CHATTER_EMAIL_PATTERN }).select("email").lean();
  const highestNumber = accounts.reduce((highest, account) => {
    const match = CHATTER_EMAIL_PATTERN.exec(account.email);
    return match ? Math.max(highest, Number(match[1])) : highest;
  }, 0);

  try {
    await Sequence.create({ _id: CHATTER_SEQUENCE_ID, value: highestNumber });
  } catch (error) {
    if (!isDuplicateKeyError(error)) throw error;
  }
}

async function nextGeneratedChatterLogin(): Promise<{ email: string; number: number }> {
  await initializeChatterSequence();
  for (let attempt = 0; attempt < 100; attempt++) {
    const sequence = await Sequence.findOneAndUpdate(
      { _id: CHATTER_SEQUENCE_ID },
      { $inc: { value: 1 } },
      { new: true },
    ).lean();
    if (!sequence) throw new Error("Chatter login sequence could not be advanced");

    const email = `chatter${sequence.value}@gmail.com`;
    if (await Worker.exists({ email })) continue;
    return { email, number: sequence.value };
  }
  throw new HttpError(503, "Unable to allocate a unique chatter login");
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

router.get("/chatters", authorizePermission("chatters.read"), async (req, res) => {
  const { page, limit, search, status } = parseInput(chatterListSchema, req.query);
  const conditions: Record<string, unknown>[] = [
    { role: { $in: ["CHATTER", "worker"] } },
  ];
  if (search) {
    const escapedSearch = search.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const searchConditions: Record<string, unknown>[] = [
      { displayName: { $regex: escapedSearch, $options: "i" } },
      { email: { $regex: escapedSearch, $options: "i" } },
    ];
    if (/^[a-f\d]{24}$/i.test(search)) {
      searchConditions.push({ _id: new Types.ObjectId(search) });
    }
    conditions.push({ $or: searchConditions });
  }
  if (status === "ACTIVE") {
    conditions.push({
      $or: [
        { status: "ACTIVE", active: true, suspendedAt: null },
        { status: { $exists: false }, active: true, suspendedAt: null },
      ],
    });
  } else if (status === "DISABLED") {
    conditions.push({
      $or: [
        { status: "DISABLED" },
        {
          status: "ACTIVE",
          $or: [{ active: false }, { suspendedAt: { $ne: null } }],
        },
        {
          status: { $exists: false },
          $or: [{ active: false }, { suspendedAt: { $ne: null } }],
        },
      ],
    });
  } else if (status) {
    conditions.push({ status });
  }

  const filter = { $and: conditions };
  const [items, total] = await Promise.all([
    Worker.find(filter).select(CHATTER_PROFILE_FIELDS)
      .sort({ createdAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
    Worker.countDocuments(filter),
  ]);
  const assignments = items.length > 0
    ? await WorkerAssignment.find({
      workerId: { $in: items.map((item) => item._id) },
      active: true,
    }).select("workerId modelAccountId assignedAt").lean()
    : [];
  const modelIds = [...new Set(assignments.map((assignment) => assignment.modelAccountId.toString()))];
  const models = modelIds.length > 0
    ? await ModelAccount.find({ _id: { $in: modelIds } }).select("displayName").lean()
    : [];
  const modelNames = new Map(models.map((modelAccount) => [
    modelAccount._id.toString(),
    modelAccount.displayName,
  ]));
  const assignmentsByChatter = new Map<string, Array<{ id: string; modelId: string; modelName: string }>>();
  for (const assignment of assignments) {
    const chatterId = assignment.workerId.toString();
    const modelId = assignment.modelAccountId.toString();
    const list = assignmentsByChatter.get(chatterId) ?? [];
    list.push({
      id: assignment._id.toString(),
      modelId,
      modelName: modelNames.get(modelId) ?? "Model",
    });
    assignmentsByChatter.set(chatterId, list);
  }
  res.json({
    items: items.map((item) => ({
      ...item,
      status: chatterStatus(item),
      assignedModels: assignmentsByChatter.get(item._id.toString()) ?? [],
    })),
    total,
    page,
    limit,
  });
});

router.post("/chatters", authorize("ADMIN"), async (req, res) => {
  parseInput(z.object({}), req.body);
  const adminId = req.authenticatedWorker?.id;
  if (!adminId) throw new HttpError(401, "Authentication required");

  const temporaryPassword = generateStrongPassword();
  const passwordHash = await bcrypt.hash(temporaryPassword, 12);
  let created: {
    id: string;
    email: string;
    displayName: string;
    createdAt: Date;
  } | undefined;

  for (let attempt = 0; attempt < 100; attempt++) {
    const login = await nextGeneratedChatterLogin();
    const worker = new Worker({
      displayName: `Chatter ${login.number}`,
      email: login.email,
      role: "CHATTER",
      status: "ACTIVE",
      active: true,
      createdBy: adminId,
      passwordHash,
      mustChangePassword: false,
    });
    try {
      await worker.save();
      created = {
        id: worker._id.toString(),
        email: worker.email,
        displayName: worker.displayName,
        createdAt: worker.createdAt,
      };
      break;
    } catch (error) {
      if (!isDuplicateEmailError(error)) throw error;
    }
  }
  if (!created) throw new HttpError(503, "Unable to create a unique chatter login");

  await audit(
    req,
    "chatter.created",
    "Worker",
    created.id,
    null,
    { email: created.email, role: "CHATTER", status: "ACTIVE" },
    { chatterId: created.id },
  );
  res.status(201).json({
    chatter: {
      id: created.id,
      displayName: created.displayName,
      email: created.email,
      role: "CHATTER",
      status: "ACTIVE",
      createdAt: created.createdAt,
      assignedModels: [],
    },
    temporaryPassword,
  });
});

router.get("/chatters/:id", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const chatter = await Worker.findOne({
    _id: id,
    role: { $in: ["CHATTER", "worker"] },
  }).select(CHATTER_PROFILE_FIELDS).lean();
  if (!chatter) throw new HttpError(404, "Chatter account not found");

  const [assignments, history] = await Promise.all([
    WorkerAssignment.find({ workerId: id, active: true })
      .select("modelAccountId assignedAt")
      .sort({ assignedAt: -1 })
      .lean(),
    AuditLog.find({
      $or: [
        { chatterId: id },
        { affectedType: "Worker", affectedId: id },
      ],
    })
      .select("actorId action affectedId reason createdAt")
      .sort({ createdAt: -1 })
      .limit(50)
      .lean(),
  ]);
  const modelIds = [...new Set(assignments.map((assignment) => assignment.modelAccountId.toString()))];
  const models = modelIds.length > 0
    ? await ModelAccount.find({ _id: { $in: modelIds } }).select("displayName").lean()
    : [];
  const modelNames = new Map(models.map((modelAccount) => [
    modelAccount._id.toString(),
    modelAccount.displayName,
  ]));

  res.json({
    chatter: {
      id: chatter._id.toString(),
      displayName: chatter.displayName,
      email: chatter.email,
      role: chatter.role,
      status: chatterStatus(chatter),
      createdAt: chatter.createdAt,
      lastLoginAt: chatter.lastLoginAt,
      createdBy: chatter.createdBy?.toString() ?? null,
      disabledAt: chatter.disabledAt,
      disabledReason: chatter.disabledReason,
      bannedAt: chatter.bannedAt,
      banReason: chatter.banReason,
      archivedAt: chatter.archivedAt,
    },
    assignedModels: assignments.map((assignment) => ({
      assignmentId: assignment._id.toString(),
      modelId: assignment.modelAccountId.toString(),
      modelName: modelNames.get(assignment.modelAccountId.toString()) ?? "Model",
      assignedAt: assignment.assignedAt,
    })),
    history: history.map((entry) => ({
      id: entry._id.toString(),
      adminId: entry.actorId.toString(),
      action: entry.action,
      reason: entry.reason ?? "",
      createdAt: entry.createdAt,
    })),
  });
});

router.post("/chatters/:id/assignments", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const input = parseInput(
    z.object({ modelIds: z.array(objectIdSchema).min(1).max(100) }),
    req.body,
  );
  const worker = await Worker.findOne({
    _id: id,
    role: { $in: ["CHATTER", "worker"] },
    active: true,
    suspendedAt: null,
    status: { $in: ["ACTIVE", null] },
  }).select("_id");
  if (!worker) throw new HttpError(404, "Active chatter not found");

  const modelIds = [...new Set(input.modelIds)];
  const models = await ModelAccount.find({
    _id: { $in: modelIds },
    active: true,
    $or: [{ status: "active" }, { status: { $exists: false } }],
  }).select("_id").lean();
  if (models.length !== modelIds.length) {
    throw new HttpError(400, "Only active models can be assigned");
  }

  const actorId = req.authenticatedWorker?.id ?? null;
  const savedAssignments = [];
  for (const modelId of modelIds) {
    let assignment = await WorkerAssignment.findOne({ workerId: id, modelAccountId: modelId });
    if (assignment?.active) continue;
    const assignedAt = new Date();
    if (assignment) {
      assignment.active = true;
      assignment.status = "active";
      assignment.assignedBy = actorId ? new Types.ObjectId(actorId) : null;
      assignment.assignedAt = assignedAt;
      assignment.removedAt = null;
      assignment.history.push({
        action: "assigned",
        actorId: actorId ? new Types.ObjectId(actorId) : null,
        at: assignedAt,
      });
    } else {
      assignment = new WorkerAssignment({
        workerId: id,
        modelAccountId: modelId,
        active: true,
        status: "active",
        assignedBy: actorId,
        assignedAt,
        history: [{ action: "assigned", actorId, at: assignedAt }],
      });
    }
    await assignment.save();
    await audit(
      req,
      "chatter.model_assigned",
      "WorkerAssignment",
      assignment._id.toString(),
      null,
      { chatterId: id, modelAccountId: modelId },
      { chatterId: id },
    );
    savedAssignments.push(assignment);
  }
  res.status(201).json({ assignments: savedAssignments });
});

router.post("/chatters/:id/password-reset", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const worker = await Worker.findOne({
    _id: id,
    role: { $in: ["CHATTER", "worker"] },
  });
  if (!worker) throw new HttpError(404, "Chatter account not found");
  const temporaryPassword = generateStrongPassword();
  worker.passwordHash = await bcrypt.hash(temporaryPassword, 12);
  worker.mustChangePassword = false;
  worker.tokenVersion += 1;
  await worker.save();
  await audit(
    req,
    "chatter.password_reset",
    "Worker",
    id,
    null,
    null,
    { chatterId: id },
  );
  res.json({ temporaryPassword });
});

router.patch("/chatters/:id/status", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const input = parseInput(
    z.object({
      status: z.enum(["ACTIVE", "DISABLED"]).optional(),
      suspended: z.boolean().optional(),
      reason: z.string().trim().max(500).optional(),
    }).refine((value) => value.status !== undefined || value.suspended !== undefined),
    req.body,
  );
  const before = await Worker.findOne({
    _id: id,
    role: { $in: ["CHATTER", "worker"] },
  }).select(CHATTER_PROFILE_FIELDS).lean();
  if (!before) throw new HttpError(404, "Chatter account not found");
  const currentStatus = chatterStatus(before);
  if (currentStatus === "BANNED" || currentStatus === "ARCHIVED") {
    throw new HttpError(409, "Banned or archived accounts require a separate action");
  }
  const targetStatus = input.status ?? (input.suspended ? "DISABLED" : "ACTIVE");
  const disabled = targetStatus === "DISABLED";
  const worker = await Worker.findOneAndUpdate(
    {
      _id: id,
      role: { $in: ["CHATTER", "worker"] },
      status: { $nin: ["BANNED", "ARCHIVED"] },
    },
    {
      $set: {
        status: targetStatus,
        active: !disabled,
        suspendedAt: disabled ? new Date() : null,
        disabledAt: disabled ? new Date() : null,
        disabledReason: disabled ? (input.reason ?? "") : "",
      },
      $inc: { tokenVersion: 1 },
    },
    { new: true, runValidators: true },
  ).select(CHATTER_PROFILE_FIELDS);
  if (!worker) throw new HttpError(409, "Chatter account status changed; reload and try again");
  await audit(
    req,
    disabled ? "chatter.disabled" : "chatter.reactivated",
    "Worker",
    id,
    before,
    worker,
    { chatterId: id, reason: input.reason ?? "" },
  );
  res.json({ chatter: worker });
});

router.post("/chatters/:id/ban", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const { reason } = parseInput(
    z.object({ reason: z.string().trim().min(3).max(500) }),
    req.body,
  );
  const before = await Worker.findOne({
    _id: id,
    role: { $in: ["CHATTER", "worker"] },
  }).select(CHATTER_PROFILE_FIELDS).lean();
  if (!before) throw new HttpError(404, "Chatter account not found");
  if (chatterStatus(before) === "BANNED" || chatterStatus(before) === "ARCHIVED") {
    throw new HttpError(409, "This account cannot be banned in its current state");
  }
  const worker = await Worker.findOneAndUpdate(
    {
      _id: id,
      role: { $in: ["CHATTER", "worker"] },
      status: { $nin: ["BANNED", "ARCHIVED"] },
    },
    {
      $set: {
        status: "BANNED",
        active: false,
        suspendedAt: new Date(),
        bannedAt: new Date(),
        banReason: reason,
      },
      $inc: { tokenVersion: 1 },
    },
    { new: true, runValidators: true },
  ).select(CHATTER_PROFILE_FIELDS);
  if (!worker) throw new HttpError(409, "Chatter account status changed; reload and try again");
  await audit(
    req,
    "chatter.banned",
    "Worker",
    id,
    before,
    worker,
    { chatterId: id, reason: reason ?? "" },
  );
  res.json({ chatter: worker });
});

router.post("/chatters/:id/unban", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const { reason } = parseInput(
    z.object({ reason: z.string().trim().min(3).max(500) }),
    req.body,
  );
  const before = await Worker.findOne({
    _id: id,
    role: { $in: ["CHATTER", "worker"] },
  }).select(CHATTER_PROFILE_FIELDS).lean();
  if (!before) throw new HttpError(404, "Chatter account not found");
  if (chatterStatus(before) !== "BANNED") {
    throw new HttpError(409, "Only banned accounts can be unbanned");
  }
  const worker = await Worker.findOneAndUpdate(
    { _id: id, role: { $in: ["CHATTER", "worker"] }, status: "BANNED" },
    {
      $set: {
        status: "ACTIVE",
        active: true,
        suspendedAt: null,
        disabledAt: null,
        disabledReason: "",
        bannedAt: null,
        banReason: "",
      },
      $inc: { tokenVersion: 1 },
    },
    { new: true, runValidators: true },
  ).select(CHATTER_PROFILE_FIELDS);
  if (!worker) throw new HttpError(409, "Chatter account status changed; reload and try again");
  await audit(
    req,
    "chatter.unbanned",
    "Worker",
    id,
    before,
    worker,
    { chatterId: id, reason: reason ?? "" },
  );
  res.json({ chatter: worker });
});

router.post("/chatters/:id/archive", authorize("ADMIN"), async (req, res) => {
  const id = parseInput(objectIdSchema, req.params.id);
  const { reason } = parseInput(
    z.object({ reason: z.string().trim().max(500).optional() }),
    req.body,
  );
  const before = await Worker.findOne({
    _id: id,
    role: { $in: ["CHATTER", "worker"] },
  }).select(CHATTER_PROFILE_FIELDS).lean();
  if (!before) throw new HttpError(404, "Chatter account not found");
  if (chatterStatus(before) === "ARCHIVED") {
    throw new HttpError(409, "This account is already archived");
  }
  const worker = await Worker.findOneAndUpdate(
    {
      _id: id,
      role: { $in: ["CHATTER", "worker"] },
      status: { $ne: "ARCHIVED" },
    },
    {
      $set: {
        status: "ARCHIVED",
        active: false,
        suspendedAt: new Date(),
        archivedAt: new Date(),
      },
      $inc: { tokenVersion: 1 },
    },
    { new: true, runValidators: true },
  ).select(CHATTER_PROFILE_FIELDS);
  if (!worker) throw new HttpError(409, "Chatter account status changed; reload and try again");
  await audit(
    req,
    "chatter.archived",
    "Worker",
    id,
    before,
    worker,
    { chatterId: id, reason: reason ?? "" },
  );
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
    status: { $in: ["ACTIVE", null] },
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
    await audit(
      req,
      "chatter.model_assigned",
      "WorkerAssignment",
      existing._id.toString(),
      null,
      { workerId, modelAccountId: input.modelAccountId },
      { chatterId: workerId },
    );
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
  await audit(
    req,
    "chatter.model_unassigned",
    "WorkerAssignment",
    id,
    before,
    assignment,
    { chatterId: before.workerId.toString() },
  );
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
