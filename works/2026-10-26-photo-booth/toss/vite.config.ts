import { defineConfig } from "vite";

// Only the platform seam is bundled (it needs the npm SDK); everything else is site/ copied as is by scripts/assemble.mjs.
export default defineConfig({
  publicDir: false,
  define: { "import.meta.env.VITE_AIT_APP_NAME": JSON.stringify(process.env.AIT_APP_NAME ?? "ttarogachi-test") },
  build: {
    outDir: "dist/js", emptyOutDir: false, minify: false, target: "es2022",
    lib: { entry: "src/platform-toss.js", formats: ["es"], fileName: () => "platform.js" },
  },
});
