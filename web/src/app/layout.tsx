import type { Metadata } from "next";
import { Asta_Sans, Geist_Mono } from "next/font/google";
import type { ReactNode } from "react";
import { SiteHeader } from "@/components/SiteHeader";
import "./globals.css";

const bodyFont = Asta_Sans({
  subsets: ["latin"],
  weight: "variable",
  variable: "--font-body",
  preload: false,
  adjustFontFallback: false,
  fallback: ["Apple SD Gothic Neo", "Noto Sans KR", "sans-serif"],
});
const monoFont = Geist_Mono({ subsets: ["latin"], weight: "variable", variable: "--font-mono" });

export const metadata: Metadata = {
  title: { default: "dropgate", template: "%s | dropgate" },
  description: "한정판 드롭 보드",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko" className={`${bodyFont.variable} ${monoFont.variable}`}>
      <body>
        <SiteHeader />
        {children}
      </body>
    </html>
  );
}
