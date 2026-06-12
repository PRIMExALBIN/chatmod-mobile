import "dotenv/config";
import {
  checkProductionEnv,
  defaultProductionEnvPreflightOptions,
  type ProductionEnvPreflightOptions,
  type ProductionEnvPreflightResult
} from "./productionEnvPreflight.js";

const { options, json } = parseArgs(process.argv.slice(2));
const result = checkProductionEnv(process.env, options);

if (json) {
  console.log(JSON.stringify(result, null, 2));
} else {
  printResult(result);
}

if (!result.passed) {
  process.exit(1);
}

function parseArgs(args: string[]): { options: ProductionEnvPreflightOptions; json: boolean } {
  const options = { ...defaultProductionEnvPreflightOptions };
  let json = false;

  for (const arg of args) {
    if (arg === "--json") {
      json = true;
      continue;
    }
    if (arg === "--allow-missing-google-oauth") {
      options.requireGoogleOAuth = false;
      continue;
    }
    if (arg === "--allow-missing-google-play") {
      options.requireGooglePlay = false;
      continue;
    }
    if (arg === "--allow-local-origins") {
      options.allowLocalOrigins = true;
      continue;
    }

    throw new Error(`Unknown env preflight option: ${arg}`);
  }

  return { options, json };
}

function printResult(result: ProductionEnvPreflightResult): void {
  for (const check of result.checks) {
    console.log(`PASS ${check}`);
  }
  for (const warning of result.warnings) {
    console.warn(`WARN ${warning}`);
  }
  for (const failure of result.failures) {
    console.error(`FAIL ${failure}`);
  }

  console.log(`\nProduction env preflight: ${result.checks.length} passed, ${result.warnings.length} warning(s), ${result.failures.length} failure(s).`);
}
