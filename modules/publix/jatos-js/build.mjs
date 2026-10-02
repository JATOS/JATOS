import { build } from "esbuild";
import { mkdir, readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const projectDir = dirname(fileURLToPath(import.meta.url));
const outputDirectory = join(projectDir, "../public/javascripts");
const outputs = [
    {
        entryPoint: "src/workers/heartbeat.js",
        filename: "heartbeat.js",
        minify: false
    },
    {
        entryPoint: "src/workers/heartbeat.js",
        filename: "heartbeat.min.js",
        minify: true
    },
    {
        entryPoint: "src/with-jquery.js",
        filename: "jatos.js",
        minify: false,
        banner: "var jatos;"
    },
    {
        entryPoint: "src/with-jquery.js",
        filename: "jatos.min.js",
        minify: true,
        banner: "var jatos;"
    },
    {
        entryPoint: "src/index.js",
        filename: "jatos-slim.js",
        minify: false,
        banner: "var jatos;"
    },
    {
        entryPoint: "src/index.js",
        filename: "jatos-slim.min.js",
        minify: true,
        banner: "var jatos;"
    },
    {
        entryPoint: "src/workers/http-loop-worker.js",
        filename: "http-loop-worker.js",
        minify: false
    }
];
const checkOnly = process.argv.includes("--check");

const commonOptions = {
    bundle: true,
    format: "iife",
    platform: "browser",
    target: "es2020",
    charset: "utf8",
    legalComments: "inline",
    logLevel: "info"
};

if (!checkOnly) {
    await mkdir(outputDirectory, { recursive: true });
    for (const output of outputs) {
        await build({
            ...commonOptions,
            entryPoints: [join(projectDir, output.entryPoint)],
            outfile: join(outputDirectory, output.filename),
            minify: output.minify,
            banner: output.banner ? { js: output.banner } : undefined
        });
    }
} else {
    for (const output of outputs) {
        const outputFile = join(outputDirectory, output.filename);
        const result = await build({
            ...commonOptions,
            entryPoints: [join(projectDir, output.entryPoint)],
            outfile: outputFile,
            minify: output.minify,
            banner: output.banner ? { js: output.banner } : undefined,
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
