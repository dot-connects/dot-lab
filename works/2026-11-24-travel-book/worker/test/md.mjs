// node --test cannot import .md; wrangler does (wrangler.toml [[rules]] Text). This hook makes .md a string module.
import { registerHooks } from "node:module";
import { readFileSync } from "node:fs";

registerHooks({
  load(url, ctx, next) {
    if (!url.endsWith(".md")) return next(url, ctx);
    return { format: "module", shortCircuit: true, source: `export default ${JSON.stringify(readFileSync(new URL(url), "utf8"))};` };
  },
});
