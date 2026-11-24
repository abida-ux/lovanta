import { model, Schema } from "mongoose";

const sequenceSchema = new Schema(
  {
    _id: { type: String, required: true },
    value: { type: Number, required: true, min: 0 },
  },
  { versionKey: false },
);

export const Sequence = model("Sequence", sequenceSchema, "sequences");
