import { Router } from "express";
import { Types } from "mongoose";
import { z } from "zod";
import { HttpError } from "../errors/HttpError.ts";
import { authenticateWorker } from "../middleware/authenticateWorker.ts";
import { ChatMessage } from "../models/ChatMessage.ts";
import { Conversation } from "../models/Conversation.ts";
import { ModelAccount } from "../models/ModelAccount.ts";
import { MediaAsset } from "../models/MediaAsset.ts";
import { WorkerAssignment } from "../models/WorkerAssignment.ts";
import { PPVPurchase } from "../models/PPVPurchase.ts";
import { Commission } from "../models/Commission.ts";
import { Transaction } from "../models/Transaction.ts";
import { parseInput } from "../utils/parseInput.ts";
import { formatStaffNumber } from "../utils/staffNumber.ts";
import { generatePresignedGetUrl, R2StorageError } from "../services/r2Storage.ts";

const router = Router();
const objectIdSchema = z.string().regex(/^[a-f\d]{24}$/i);
const limitSchema = z.coerce.number().int().min(1).max(100).default(50);
const sendMessageSchema = z.object({
  text: z.string().trim().min(1).max(4000),
});

router.use(authenticateWorker);

router.get("/chatter/summary", async (req, res) => {
  const worker = req.authenticatedWorker;
  if (!worker) throw new HttpError(401, "Authentication required");

  const [assignments, messagesSent, activeConversations, paidPpvPurchases, commissionTotals] =
    await Promise.all([
      WorkerAssignment.find({ workerId: worker.id, active: true }).select("modelAccountId").lean(),
      ChatMessage.countDocuments({ senderWorkerId: worker.id }),
      WorkerAssignment.find({ workerId: worker.id, active: true }).distinct("modelAccountId")
        .then((modelIds) => Conversation.countDocuments({ modelAccountId: { $in: modelIds }, status: "active" })),
      PPVPurchase.countDocuments({ chatterId: worker.id, status: "paid" }),
      Commission.aggregate([
        { $match: { chatterId: new Types.ObjectId(worker.id) } },
        { $group: { _id: "$status", amount: { $sum: "$amount" }, count: { $sum: 1 } } },
      ]),
    ]);

  const commissionByStatus = new Map(commissionTotals.map((row) => [row._id, row]));
  const paidCommission = commissionByStatus.get("paid");
  const pendingCommission = commissionByStatus.get("pending");
  const modelIds = assignments.map((assignment) => assignment.modelAccountId);
  const completedSales = await Transaction.aggregate([
    {
      $match: {
        chatterId: new Types.ObjectId(worker.id),
        status: "successful",
      },
    },
    { $group: { _id: null, amount: { $sum: "$amount" }, count: { $sum: 1 } } },
  ]);

  res.json({
    staffNumber: formatStaffNumber(worker.staffNumber),
    assignedModels: modelIds.length,
    activeConversations,
    messagesSent,
    paidPpvPurchases,
    salesAmount: completedSales[0]?.amount ?? 0,
    successfulSales: completedSales[0]?.count ?? 0,
    pendingCommission: pendingCommission?.amount ?? 0,
    paidCommission: paidCommission?.amount ?? 0,
  });
});

router.get("/models", async (req, res) => {
  const worker = req.authenticatedWorker;
  if (!worker) {
    throw new HttpError(401, "Authentication required");
  }

  const assignments = await WorkerAssignment.find({ workerId: worker.id, active: true })
    .select("modelAccountId")
    .lean();
  const modelIds = assignments.map((assignment) => assignment.modelAccountId);
  const models = await ModelAccount.find({ _id: { $in: modelIds }, active: true })
    .select("displayName avatarUrl description availability")
    .lean();

  res.json({
    models: models.map((modelAccount) => ({
      id: modelAccount._id.toString(),
      displayName: modelAccount.displayName,
      avatarUrl: modelAccount.avatarUrl,
      description: modelAccount.description,
      availability: modelAccount.availability,
    })),
  });
});

router.get("/models/:modelAccountId/conversations", async (req, res) => {
  const worker = req.authenticatedWorker;
  if (!worker) {
    throw new HttpError(401, "Authentication required");
  }

  const modelAccountId = parseInput(objectIdSchema, req.params.modelAccountId);
  const isAssigned = await WorkerAssignment.exists({
    workerId: worker.id,
    modelAccountId,
    active: true,
  });
  if (!isAssigned) {
    throw new HttpError(404, "Model account not found");
  }

  const limit = parseInput(limitSchema, req.query.limit ?? 50);
  const conversations = await Conversation.find({
    modelAccountId,
    status: "active",
  })
    .sort({ lastMessageAt: -1 })
    .limit(limit)
    .select("subscriberId lastMessageText lastMessageAt")
    .lean();

  res.json({
    conversations: conversations.map((conversation) => ({
      id: conversation._id.toString(),
      subscriberId: conversation.subscriberId,
      lastMessageText: conversation.lastMessageText,
      lastMessageAt: conversation.lastMessageAt,
    })),
  });
});

router.get("/models/:modelAccountId/media", async (req, res) => {
  const worker = req.authenticatedWorker;
  if (!worker) throw new HttpError(401, "Authentication required");

  const modelAccountId = parseInput(objectIdSchema, req.params.modelAccountId);
  const isAssigned = await WorkerAssignment.exists({
    workerId: worker.id,
    modelAccountId,
    active: true,
  });
  if (!isAssigned) throw new HttpError(404, "Model account not found");

  const assets = await MediaAsset.find({
    modelAccountId,
    purpose: "gallery",
    status: "ready",
  }).sort({ createdAt: -1 }).lean();

  const responseAssets = await Promise.all(assets.map(async (asset) => {
    let previewUrl = asset.publicUrl;
    if (asset.visibility === "private") {
      try {
        previewUrl = await generatePresignedGetUrl({
          visibility: "private",
          key: asset.objectKey,
          expiresInSeconds: 300,
        });
      } catch (error) {
        if (error instanceof R2StorageError) {
          throw new HttpError(503, "Private media preview unavailable");
        }
        throw error;
      }
    }

    return {
      id: asset._id.toString(),
      visibility: asset.visibility,
      previewUrl,
      contentType: asset.contentType,
      sizeBytes: asset.sizeBytes,
      createdAt: asset.createdAt,
    };
  }));

  res.json({ assets: responseAssets });
});

router.get("/conversations/:conversationId/messages", async (req, res) => {
  const worker = req.authenticatedWorker;
  if (!worker) {
    throw new HttpError(401, "Authentication required");
  }

  const conversationId = parseInput(objectIdSchema, req.params.conversationId);
  const conversation = await Conversation.findOne({ _id: conversationId, status: "active" })
    .select("modelAccountId")
    .lean();
  if (!conversation) {
    throw new HttpError(404, "Conversation not found");
  }

  const isAssigned = await WorkerAssignment.exists({
    workerId: worker.id,
    modelAccountId: conversation.modelAccountId,
    active: true,
  });
  if (!isAssigned) {
    throw new HttpError(404, "Conversation not found");
  }

  const limit = parseInput(limitSchema, req.query.limit ?? 50);
  const messages = await ChatMessage.find({ conversationId })
    .sort({ sentAt: -1 })
    .limit(limit)
    .lean();

  res.json({
    messages: messages.reverse().map((message) => ({
      id: message._id.toString(),
      senderType: message.senderType,
      senderWorkerId: message.senderWorkerId?.toString() ?? null,
      text: message.text,
      sentAt: message.sentAt,
    })),
  });
});

router.post("/conversations/:conversationId/messages", async (req, res) => {
  const worker = req.authenticatedWorker;
  if (!worker) {
    throw new HttpError(401, "Authentication required");
  }

  const conversationId = parseInput(objectIdSchema, req.params.conversationId);
  const { text } = parseInput(sendMessageSchema, req.body);
  const session = await Types.ObjectId.createFromHexString(conversationId);
  const mongoSession = await Conversation.startSession();
  let createdMessage:
    | { id: string; senderWorkerId: string; text: string; sentAt: Date }
    | undefined;

  try {
    await mongoSession.withTransaction(async () => {
      const conversation = await Conversation.findOne({
        _id: session,
        status: "active",
      }).session(mongoSession);
      if (!conversation) {
        throw new HttpError(404, "Conversation not found");
      }

      const isAssigned = await WorkerAssignment.exists({
        workerId: worker.id,
        modelAccountId: conversation.modelAccountId,
        active: true,
      }).session(mongoSession);
      if (!isAssigned) {
        throw new HttpError(404, "Conversation not found");
      }

      const sentAt = new Date();
      const message = new ChatMessage({
        conversationId: conversation._id,
        modelAccountId: conversation.modelAccountId,
        senderType: "chatter",
        senderWorkerId: worker.id,
        text,
        sentAt,
      });
      await message.save({ session: mongoSession });

      conversation.lastMessageText = text.slice(0, 500);
      conversation.lastMessageAt = sentAt;
      await conversation.save({ session: mongoSession });

      createdMessage = {
        id: message._id.toString(),
        senderWorkerId: worker.id,
        text,
        sentAt,
      };
    });
  } finally {
    await mongoSession.endSession();
  }

  if (!createdMessage) {
    throw new Error("Message transaction completed without creating a message");
  }

  res.status(201).json({ message: createdMessage });
});

export default router;
