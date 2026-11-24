import { model, Schema } from "mongoose";

const conversationSchema = new Schema(
  {
    subscriberId: { type: String, required: true, trim: true },
    modelAccountId: { type: Schema.Types.ObjectId, ref: "ModelAccount", required: true },
    status: { type: String, enum: ["active", "archived"], required: true, default: "active" },
    lastMessageText: { type: String, required: true, default: "", maxlength: 500 },
    lastMessageAt: { type: Date, required: true, default: Date.now },
  },
  { timestamps: true },
);

conversationSchema.index({ modelAccountId: 1, status: 1, lastMessageAt: -1 });

export const Conversation = model("Conversation", conversationSchema);
