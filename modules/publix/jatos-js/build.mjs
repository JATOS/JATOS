import { build } from "esbuild";
import { mkdir, readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const projectDir = dirname(fileURLToPath(import.meta.url));
const outputFile = join(projectDir, "../public/javascripts/jatos.js");
const checkOnly = process.argv.includes("--check");

const options = {
    entryPoints: [join(projectDir, "src/index.js")],
    outfile: outputFile,
    bundle: true,
    format: "iife",
    platform: "browser",
    target: "es2020",
    charset: "utf8",
    legalComments: "inline",
    banner: {
        js: "var jatos;"
    },
    logLevel: "info",
    write: !checkOnly
};

if (!checkOnly) {
    await mkdir(dirname(outputFile), { recursive: true });
    await build(options);
} else {
    const result = await build({ ...options, write: false, logLevel: "silent" });
    const generated = result.outputFiles.find(file => file.path === outputFile)?.text;
    const current = await readFile(outputFile, "utf8");

    if (generated !== current) {
        console.error("Generated jatos.js is stale. Run `npm run build`.");
        process.exitCode = 1;
    } else {
        console.log("Generated jatos.js is up to date.");
    }
}
