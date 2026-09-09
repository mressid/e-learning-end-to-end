// Captures the live OpenAPI document into openapi.json.
//
// The dashboard's entire API layer is generated from that file, so a stale copy
// does not fail loudly — it compiles against endpoints and shapes the backend no
// longer serves, and breaks at runtime. Re-run this (via `bun run api:sync`)
// whenever a controller or request/response record changes.
//
// Written pretty-printed and key-ordered (springdoc sorts keys for us) so the
// diff is readable in review rather than one 8000-column line.
import { writeFileSync } from "node:fs";

const base = process.env.API_URL ?? "http://localhost:8081";
const url = `${base}/v3/api-docs`;

const response = await fetch(url).catch((cause) => {
  throw new Error(`Could not reach ${url} — is the backend running?`, { cause });
});

if (!response.ok) {
  throw new Error(`${url} returned ${response.status} ${response.statusText}`);
}

const document = await response.json();
const paths = Object.keys(document.paths ?? {}).length;
if (paths === 0) {
  throw new Error(`${url} returned a document with no paths; refusing to overwrite openapi.json`);
}

writeFileSync("openapi.json", `${JSON.stringify(document, null, 2)}\n`);
console.log(`openapi.json ← ${url} (${paths} paths)`);
