"use client";

import { useEffect, useState } from "react";
import Image from "next/image";

export default function Home() {
  const [QR, setQR] = useState<string>("");
  const [ID, setID] = useState<string>("");
  const [netFile, setNetFile] = useState<any>(null);

  // dummy
  useEffect(() => {
    fetchSP();
  });

  // const handleFileChange = (event) => {
  //   setSelectedFile(event.target.files[0]);
  // };

  const fetchSP = async () => {
    const res = await fetch("/dummy.txt");
    const text = await res.text();
    setNetFile(text);
  };

  const handleUpload = async (file: File) => {
    const formData = new FormData();
    formData.append("netlist", file);

    const res = await fetch("/api/routes", {
      method: "POST",
      body: formData,
    });

    const data = await res.json();
    console.log("DATA", data);

    // sets and shows variables
    setQR(data.qr);
    setID(data.id);

    console.log(QR, ID);
  };

  return (
    <div className="relative min-h-screen w-screen tracking-wider">
      <div className="absolute inset-0 bg-violet-600/40 z-10 pointer-events-none" />
      <div className="bg-[url('/o1.jpg')] bg-cover bg-center bg-no-repeat h-screen overflow-hidden p-5">
        <div className="flex flex-row">
          <Image src="/logo.png" width={50} height={50} alt="logo"></Image>
          <h1 className="text-5xl text-white">Guideline</h1>
        </div>
        <div className="w-full flex items-center justify-center">
          <div className="h-full flex mt-[20vh] w-[80%] flex-col text-violet-200 backdrop-blur-sm p-10 rounded-4xl items-center">
            <h1 className="text-5xl tracking-wider">
              AR-powered real-time circuit assembly
            </h1>
            <div className="w-[50%]">
              <h1 className="text-violet-100 text-xl mt-5 text-center">
                Guides users through breadboard circuit assembly by overlaying
                step-by-step instructions directly on their hardware.
              </h1>
            </div>
          </div>
        </div>

        <div className="w-full flex items-center justify-center mt-10 flex-col">
          <button
            onClick={() => handleUpload(netFile)}
            className="p-5 bg-violet-100/80 w-40 rounded-md cursor-pointer hover:bg-violet-100/75 transition-all duration-200 ease-in-out delay-100"
          >
            upload netfile
          </button>

          {QR !== "" && (
            <div className="mt-10 relative">
              <img src={QR} alt="qr code" />
              <button className="absolute top-0 right-0 cursor-pointer"></button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
