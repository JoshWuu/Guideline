// New API route to handle file upload, parse netlist, save JSON to MongoDB, and return QR code data
import QRCode from "qrcode";
import { NextResponse } from "next/server";
import connectDB from "../db/connect";


export const config = {
  api: {
    bodyParser: false,
  },
};

export async function POST(req: Request) {
  const formData = await req.formData();
  const file = formData.get("netlist");

  const client = await connectDB();

  const db = client!.db();
  const result = await db.collection("netlists").insertOne({ netlist: file });

  const qrData = await QRCode.toDataURL(result.insertedId.toString());
  await client!.close();

  return NextResponse.json({ id: result.insertedId.toString(), qr: qrData });
}