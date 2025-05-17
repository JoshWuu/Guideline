import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Guideline",
  description: "AR",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <link rel="icon" href="/logo.png" />
      <body className={`antialiased`}>{children}</body>
    </html>
  );
}
