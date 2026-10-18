import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    env: {
      DROPGATE_WEB_MOCK: "false",
      DROPGATE_WEB_AUTH_BASE_URL: "http://auth.test",
      DROPGATE_WEB_ORDER_BASE_URL: "http://order.test",
      DROPGATE_WEB_NOTIFICATION_BASE_URL: "http://notification.test",
    },
  },
});
