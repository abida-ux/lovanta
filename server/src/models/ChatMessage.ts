import { model, Schema } from "mongoose";

const chatMessageSchema = new Schema(
  {
    conversationId: { type: Schema.Types.ObjectId, ref: "Conversation", required: true },
    modelAccountId: { type: Schema.Types.ObjectId, ref: "ModelAccount", required: true },
    senderType: {
      type: String,
      enum: ["chatter", "subscriber", "system"],
      required: true,
    },
    senderWorkerId: { type: Schema.Types.ObjectId, ref: "Worker", default: null },
    text: { type: String, required: true, trim: true, maxlength: 4000 },
    sentAt: { type: Date, required: true, default: Date.now },
  },
  { timestamps: true },
);

chatMessageSchema.index({ conversationId: 1, sentAt: -1 });

export const ChatMessage = model("ChatMessage", chatMessageSchema);
