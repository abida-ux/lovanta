import { Sequence } from "../models/Sequence.ts";
import { Worker } from "../models/Worker.ts";

const STAFF_NUMBER_SEQUENCE_ID = "staff-number";

function isDuplicateKeyError(error: unknown): boolean {
  return typeof error === "object"
    && error !== null
    && "code" in error
    && error.code === 11000;
}

async function initializeStaffNumberSequence(): Promise<void> {
  if (await Sequence.exists({ _id: STAFF_NUMBER_SEQUENCE_ID })) return;

  const highestWorker = await Worker.findOne()
    .sort({ staffNumber: -1 })
    .select("staffNumber")
    .lean();
  try {
    await Sequence.create({
      _id: STAFF_NUMBER_SEQUENCE_ID,
      value: highestWorker?.staffNumber ?? 0,
    });
  } catch (error) {
    if (!isDuplicateKeyError(error)) throw error;
  }
}

export async function nextStaffNumber(): Promise<number> {
  await initializeStaffNumberSequence();
  const sequence = await Sequence.findOneAndUpdate(
    { _id: STAFF_NUMBER_SEQUENCE_ID },
    { $inc: { value: 1 } },
    { new: true },
  ).lean();
  if (!sequence) throw new Error("Staff number sequence could not be advanced");
  return sequence.value;
}

export async function ensureStaffNumber(workerId: string): Promise<number> {
  const worker = await Worker.findById(workerId).select("staffNumber").lean();
  if (!worker) throw new Error("Staff account no longer exists");
  if (typeof worker.staffNumber === "number") return worker.staffNumber;

  const staffNumber = await nextStaffNumber();
  const updated = await Worker.findOneAndUpdate(
    {
      _id: workerId,
      $or: [{ staffNumber: { $exists: false } }, { staffNumber: null }],
    },
    { $set: { staffNumber } },
    { new: true },
  ).select("staffNumber").lean();
  if (typeof updated?.staffNumber === "number") return updated.staffNumber;

  const latest = await Worker.findById(workerId).select("staffNumber").lean();
  if (typeof latest?.staffNumber === "number") return latest.staffNumber;
  throw new Error("Staff number could not be assigned");
}
