// New API route to handle file upload, parse netlist, save JSON to MongoDB, and return QR code data
import fs from "fs";
import connectDB from "../mongo/connect";
import QRCode from "qrcode";
import { NextResponse } from "next/server";

export const config = {
  api: {
    bodyParser: false,
  },
};

export async function POST(req: Request) {
  const formData = await req.formData();

  const file = formData.get("netlist") as File;
  if (!file) {
    return NextResponse.json({ error: "No file uploaded" }, { status: 400 });
  }

//   test
  console.log(file);

  const content = await file.text();

  const json = { netlist: content };

  const client = await connectDB();
  const db = client!.db();
  const result = await db.collection("netlists").insertOne(json);

  const qrData = await QRCode.toDataURL(result.insertedId.toString());
  await client!.close();

  return NextResponse.json({ qr: qrData });
}
