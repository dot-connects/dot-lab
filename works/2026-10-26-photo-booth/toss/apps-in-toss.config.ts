import { defineConfig } from "@apps-in-toss/web-framework/config";

// appName must equal the console app's name (AIT_APP_NAME=<name> npm run build; ait.local remembers it for the wizard)
export default defineConfig({
  appName: process.env.AIT_APP_NAME ?? "ttarogachi-test",
  brand: { primaryColor: "#FF6FA5" },
  webView: { allowsInlineMediaPlayback: true, mediaPlaybackRequiresUserAction: false },
  permissions: [
    { name: "camera", access: "access" },
    { name: "clipboard", access: "write" },
  ],
  webBundleDir: "dist",
});
