import { defineConfig } from "vite";
import aitDevtools from "@apps-in-toss/devtools/unplugin";

export default defineConfig({
  base: "./",
  plugins: [aitDevtools.vite()],
  worker: { format: "es" },
  define: { "import.meta.env.VITE_AIT_APP_NAME": JSON.stringify(process.env.AIT_APP_NAME ?? "photo-booth-spike") },
  server: { fs: { allow: [".", "../site/js/vendor"] } },   // only mp4-muxer is shared with the site
});
