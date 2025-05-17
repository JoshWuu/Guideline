"use client";

import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";

type Props = {
  params: { id: string };
};

export default function qrRoute({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const [json, setJSON] = useState<any>("");
  const router = useRouter();
  const { slug } = use(params);

  useEffect(() => {
    const data = fetchMongo();
    setJSON(data);
  }, []);

  async function fetchMongo() {
    try {
      const res = await fetch(`/api/routes?id=${slug}`);
      const data = await res.json();
      setJSON(data.netlist);
    } catch (err) {
      console.error("Error fetching data:", err);
    }
  }

  return (
    <div className="absolute inset-0 min-h-screen w-screen overflow-auto bg-stone-100/50">
      <div>
        <button
          onClick={() => {
            router.push("/");
          }}
          className="p-2 m-5 cursor-pointer"
        >
          home{" "}
        </button>
      </div>
      {json !== "" && (
        <div className="w-screen overflow-x-auto text-black p-4">
          <pre className="whitespace-pre-wrap break-words">
            {JSON.stringify(json, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
}
