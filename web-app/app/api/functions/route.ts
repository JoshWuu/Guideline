import { NextRequest, NextResponse } from "next/server";
import { deleteAll } from "../db/functions";

export async function POST(req: NextRequest) {
  await deleteAll();
  return NextResponse.json({ status: "ok" });
}
