import { model, Schema } from "mongoose";

const commissionSchema = new Schema(
  {
    chatterId: { type: Schema.Types.ObjectId, ref: "Worker", required: true },
    transactionId: { type: Schema.Types.ObjectId, ref: "Transaction", required: true, unique: true },
    ppvPurchaseId: { type: Schema.Types.ObjectId, ref: "PPVPurchase", default: null },
    amount: { type: Number, required: true, min: 0 },
    currency: { type: String, required: true, uppercase: true, default: "USD" },
    status: { type: String, enum: ["pending", "paid"], required: true, default: "pending" },
  },
  { timestamps: true },
);

export const Commission = model("Commission", commissionSchema);
