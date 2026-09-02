import { readFile, readdir, mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname);
const dist = resolve(root, "dist");
const output = resolve(root, "../wachwerk-android/app/src/main/assets/site/index.html");
let html = await readFile(resolve(dist, "index.html"), "utf8");
const files = await readdir(resolve(dist, "assets"));
const cssName = files.find((name) => name.endsWith(".css"));
const jsName = files.find((name) => name.endsWith(".js"));

if (!cssName || !jsName) throw new Error("Vite output is missing CSS or JavaScript");

const css = await readFile(resolve(dist, "assets", cssName), "utf8");
const js = (await readFile(resolve(dist, "assets", jsName), "utf8"))
  .replaceAll("</script", "<\\/script")
  .replaceAll("https://react.dev/", "about:blank#react-");

const cssTag = `<link rel="stylesheet" crossorigin href="./assets/${cssName}">`;
const jsTag = `<script type="module" crossorigin src="./assets/${jsName}"></script>`;
html = html
  .replace(cssTag, () => `<style>${css}</style>`)
  .replace(jsTag, () => `<script type="module">${js}</script>`);

if (html.includes(cssTag) || html.includes(jsTag)) {
  throw new Error("The local app still contains external asset references");
}

await mkdir(resolve(output, ".."), { recursive: true });
await writeFile(output, html, "utf8");
console.log(`Created fully self-contained local UI: ${output}`);
