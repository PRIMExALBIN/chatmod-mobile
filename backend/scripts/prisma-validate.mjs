import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

process.env.DATABASE_URL ??= "postgresql://chatmod:chatmod@localhost:5432/chatmod?schema=public";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const binaryName = process.platform === "win32" ? "prisma.cmd" : "prisma";
const cliCandidates = [
  resolve(scriptDir, "../node_modules/prisma/build/index.js"),
  resolve(scriptDir, "../../node_modules/prisma/build/index.js")
];
const candidateBinaries = [
  resolve(scriptDir, "../node_modules/.bin", binaryName),
  resolve(scriptDir, "../../node_modules/.bin", binaryName),
  binaryName
];
const cliPath = cliCandidates.find((candidate) => existsSync(candidate));
const command = cliPath ? process.execPath : candidateBinaries.find((candidate) => candidate === binaryName || existsSync(candidate)) ?? binaryName;
const args = cliPath
  ? [cliPath, "validate", "--schema", "prisma/schema.prisma"]
  : ["validate", "--schema", "prisma/schema.prisma"];
const result = spawnSync(command, args, {
  env: process.env,
  stdio: "inherit"
});

if (result.error) {
  console.error(result.error.message);
}

process.exit(result.status ?? 1);
