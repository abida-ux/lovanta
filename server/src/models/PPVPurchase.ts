import { model, Schema } from "mongoose";

const ppvPurchaseSchema = new Schema(
  {
    reference: { type: String, required: true, unique: true, trim: true },
    subscriberId: { type: Schema.Types.ObjectId, ref: "Subscriber", required: true },
    modelAccountId: { type: Schema.Types.ObjectId, ref: "ModelAccount", required: true },
    chatterId: { type: Schema.Types.ObjectId, ref: "Worker", default: null },
    messageId: { type: Schema.Types.ObjectId, ref: "ChatMessage", required: true },
    amount: { type: Number, required: true, min: 0 },
    currency: { type: String, required: true, uppercase: true, default: "USD" },
    status: { type: String, enum: ["pending", "paid", "failed", "refunded"], required: true, default: "pending" },
    purchasedAt: { type: Date, default: null },
  },
  { timestamps: true },
);

ppvPurchaseSchema.index({ modelAccountId: 1, status: 1, purchasedAt: -1 });
ppvPurchaseSchema.index({ chatterId: 1, status: 1, purchasedAt: -1 });
export const PPVPurchase = model("PPVPurchase", ppvPurchaseSchema);
