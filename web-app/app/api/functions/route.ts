import { NextRequest, NextResponse } from "next/server";
import connectDB from "../db/connect";

export async function deleteAll() {
  const client = await connectDB();
  const db = client!.db();
  const collection = db.collection("netlists");

  await collection.deleteMany({});
  console.log("deleted all");
}

export async function POST(req: NextRequest) {
  await deleteAll();
  return NextResponse.json({ status: "ok" });
}
