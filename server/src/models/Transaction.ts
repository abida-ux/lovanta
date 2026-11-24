import { model, Schema } from "mongoose";

const transactionSchema = new Schema(
  {
    reference: { type: String, required: true, unique: true, trim: true },
    subscriberId: { type: Schema.Types.ObjectId, ref: "Subscriber", required: true },
    amount: { type: Number, required: true, min: 0 },
    currency: { type: String, required: true, uppercase: true, default: "USD" },
    type: { type: String, enum: ["ppv", "subscription", "wallet", "refund", "adjustment"], required: true },
    provider: { type: String, required: true, trim: true },
    status: { type: String, enum: ["pending", "successful", "failed", "refunded"], required: true, default: "pending" },
    modelAccountId: { type: Schema.Types.ObjectId, ref: "ModelAccount", default: null },
    chatterId: { type: Schema.Types.ObjectId, ref: "Worker", default: null },
    ppvPurchaseId: { type: Schema.Types.ObjectId, ref: "PPVPurchase", default: null },
  },
  { timestamps: true },
);

transactionSchema.index({ status: 1, createdAt: -1 });
export const Transaction = model("Transaction", transactionSchema);
