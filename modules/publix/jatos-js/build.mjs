import { build } from "esbuild";
import { mkdir, readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const projectDir = dirname(fileURLToPath(import.meta.url));
const outputDirectory = join(projectDir, "../public/javascripts");
const outputs = [
    { filename: "jatos.js", minify: false },
    { filename: "jatos.min.js", minify: true }
];
const checkOnly = process.argv.includes("--check");

const commonOptions = {
    entryPoints: [join(projectDir, "src/index.js")],
    bundle: true,
    format: "iife",
    platform: "browser",
    target: "es2020",
    charset: "utf8",
    legalComments: "inline",
    banner: {
        js: "var jatos;"
    },
    logLevel: "info"
};

if (!checkOnly) {
    await mkdir(outputDirectory, { recursive: true });
    for (const output of outputs) {
        await build({
            ...commonOptions,
            outfile: join(outputDirectory, output.filename),
            minify: output.minify
        });
    }
} else {
    for (const output of outputs) {
        const outputFile = join(outputDirectory, output.filename);
        const result = await build({
            ...commonOptions,
            outfile: outputFile,
            minify: output.minify,
            write: false,
            logLevel: "silent"
        });
        const generated = result.outputFiles[0].text;
        const current = await readFile(outputFile, "utf8");

        if (generated !== current) {
            console.error(`Generated ${output.filename} is stale. Run \`npm run build\`.`);
            process.exitCode = 1;
        } else {
            console.log(`Generated ${output.filename} is up to date.`);
        }
    }
}
