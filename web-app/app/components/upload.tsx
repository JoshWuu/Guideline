import React, { useState } from "react";

const TxtUploader = ({
  onFileSelect,
}: {
  onFileSelect?: (file: string | null) => void;
}) => {
  const [fileName, setFileName] = useState("");

  const handleFileChange = (e: any) => {
    const file = e.target.files[0];
    if (file && file.type === "text/plain") {
      setFileName(file.name);
      const reader = new FileReader();
      reader.onload = (event) => {
        const fileContent = event.target?.result as string;
        console.log("File content:", fileContent);
        onFileSelect?.(fileContent);
      };
      reader.readAsText(file);
    } else {
      alert("Only .txt files are allowed.");
      e.target.value = "";
      setFileName("");
      onFileSelect?.(null);
    }
  };

  return (
    <label className="flex justify-center shadow p-5 bg-violet-100/80 w-50 h-auto rounded-md cursor-pointer hover:bg-violet-100/75 transition-all duration-200 ease-in-out delay-100 text-center">
      <span className="block font-medium">
        {fileName === "" && "Click to upload .txt file"}
      </span>
      <input
        type="file"
        accept=".txt"
        onChange={handleFileChange}
        className="hidden"
      />
      {fileName && <p className="mt-2 text-violet-800">Selected: {fileName}</p>}
    </label>
  );
};

export default TxtUploader;
