import { model, Schema, Types } from "mongoose";

const workerAssignmentSchema = new Schema(
  {
    workerId: { type: Schema.Types.ObjectId, ref: "Worker", required: true },
    modelAccountId: { type: Schema.Types.ObjectId, ref: "ModelAccount", required: true },
    active: { type: Boolean, required: true, default: true },
    status: { type: String, enum: ["active", "removed"], required: true, default: "active" },
    assignedBy: { type: Schema.Types.ObjectId, ref: "Worker", default: null },
    assignedAt: { type: Date, required: true, default: Date.now },
    removedAt: { type: Date, default: null },
    history: [{
      action: { type: String, enum: ["assigned", "removed"], required: true },
      actorId: { type: Schema.Types.ObjectId, ref: "Worker", default: null },
      at: { type: Date, required: true, default: Date.now },
    }],
  },
  { timestamps: true },
);

workerAssignmentSchema.index({ workerId: 1, modelAccountId: 1 }, { unique: true });

export const WorkerAssignment = model("WorkerAssignment", workerAssignmentSchema);
export type WorkerAssignmentIds = {
  workerId: Types.ObjectId;
  modelAccountId: Types.ObjectId;
};
