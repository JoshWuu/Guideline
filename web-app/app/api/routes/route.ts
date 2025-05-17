// New API route to handle file upload, parse netlist, save JSON to MongoDB, and return QR code data
import QRCode from "qrcode";
import { NextResponse } from "next/server";
import { ObjectId } from "mongodb";
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

export async function GET(req: Request) {
  const url = new URL(req.url);
  const id: any = url.searchParams.get("id");

  console.log(id);

  const client = await connectDB();

  const db = client!.db();
  const collection = db.collection("netlists");

  let result;
  try {
    result = await collection.findOne({
      _id: ObjectId.createFromHexString(id),
    });
  } catch (err) {
    return NextResponse.json({ error: "Invalid ID format" }, { status: 400 });
  }

  if (!result) {
    return NextResponse.json({ error: "Not found" }, { status: 404 });
  }

  return NextResponse.json(result);
}
