import { MongoClient } from "mongodb";

const mongo_key: string | undefined = process.env.MONGO_KEY;
console.log("MONGOKEY", mongo_key);

async function connectDB() {
  if (!mongo_key) {
    throw new Error("mongo key not defined");
  }
  
  try {
    return MongoClient.connect(mongo_key);
  } catch (e) {
    console.log("could not connect", e);
  }
}

export default connectDB;