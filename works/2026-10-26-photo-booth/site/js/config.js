// Worker URL: local wrangler dev while developing, deployed Worker on Pages
export const WORKER = location.hostname === "127.0.0.1" ? "http://127.0.0.1:8787" : "https://dot-lab-booth.dotconnects.workers.dev";
