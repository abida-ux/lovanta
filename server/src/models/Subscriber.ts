import { model, Schema } from "mongoose";

const subscriberSchema = new Schema(
  {
    username: { type: String, required: true, unique: true, trim: true, lowercase: true, maxlength: 100 },
    email: { type: String, unique: true, sparse: true, trim: true, lowercase: true, maxlength: 254 },
    status: { type: String, enum: ["active", "suspended", "blocked"], default: "active" },
    suspendedAt: { type: Date, default: null },
    walletBalance: { type: Number, min: 0, default: 0 },
  },
  { timestamps: true },
);

export const Subscriber = model("Subscriber", subscriberSchema);
