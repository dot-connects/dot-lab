import { defineConfig } from "vite";
import aitDevtools from "@apps-in-toss/devtools/unplugin";

// devtools injects a mock SDK: dev server only, never in the .ait (a mock would make every Toss call 'pass')
export default defineConfig(({ command }) => ({
  base: "./",
  plugins: command === "serve" ? [aitDevtools.vite()] : [],
  worker: { format: "es" },
  define: { "import.meta.env.VITE_AIT_APP_NAME": JSON.stringify(process.env.AIT_APP_NAME ?? "photo-booth-spike") },
  server: { fs: { allow: [".", "../site/js/vendor"] } },   // only mp4-muxer is shared with the site
}));
