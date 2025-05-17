import { NextResponse } from "next/server";
import { Formatter } from "../lib/prettier";
import connectDB from "../api/db/connect";
import { ObjectId } from "mongodb";

export async function GET(
  req: Request,
  { params }: { params: { slug: string } }
) {

  const slug = params.slug;

  console.log("[slug]/route.ts GET", slug);

  const client = await connectDB();

  const db = client!.db();
  const collection = db.collection("netlists");

  let result;
  try {
    result = await collection.findOne({
      _id: ObjectId.createFromHexString(slug),
    });
  } catch (err) {
    return NextResponse.json({ error: "Invalid ID format" }, { status: 400 });
  }

  if (!result) {
    return NextResponse.json({ error: "Not found" }, { status: 404 });
  }

  const raw = result.netlist as string;
  const parsed = Formatter(raw);

  console.log(parsed);

  return NextResponse.json({ id: slug, data: parsed });
}
