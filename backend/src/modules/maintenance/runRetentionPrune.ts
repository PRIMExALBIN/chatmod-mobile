import "dotenv/config";
import { PrismaClient } from "@prisma/client";
import {
  parseRetentionCliOptions,
  pruneRetentionData,
  type RetentionSummary
} from "./retentionPolicy.js";

async function main(): Promise<void> {
  const options = parseRetentionCliOptions(process.argv.slice(2));

  if (!process.env.DATABASE_URL) {
    throw new Error("DATABASE_URL is required to run retention pruning.");
  }

  const prisma = new PrismaClient();
  try {
    const summary = await pruneRetentionData(prisma, options);
    printSummary(summary, options.json);
  } finally {
    await prisma.$disconnect();
  }
}

function printSummary(summary: RetentionSummary, json: boolean): void {
  if (json) {
    console.log(JSON.stringify(summary, null, 2));
    return;
  }

  const verb = summary.mode === "apply" ? "deleted" : "would delete";
  console.log(`Retention mode: ${summary.mode}`);
  console.log(`Support events before: ${summary.cutoffs.supportEventsBefore}`);
  console.log(`API errors before: ${summary.cutoffs.apiErrorsBefore}`);
  console.log(`Ended stream logs before: ${summary.cutoffs.streamLogsBefore}`);
  console.log(`Ended sessions past stream-log retention: ${summary.scanned.endedSessionsPastRetention}`);
  console.log(`Backup rows scanned: ${summary.scanned.backupRows}`);
  console.log(`Support events ${verb}: ${summary.pruned.supportEvents}`);
  console.log(`API errors ${verb}: ${summary.pruned.apiErrors}`);
  console.log(`Chat message logs ${verb}: ${summary.pruned.chatMessages}`);
  console.log(`Moderation action logs ${verb}: ${summary.pruned.moderationActions}`);
  console.log(`Runtime event logs ${verb}: ${summary.pruned.runtimeEvents}`);
  console.log(`Old backup versions ${verb}: ${summary.pruned.backups}`);

  if (summary.mode === "dry-run") {
    console.log("Dry run only. Re-run with --apply to delete matching rows.");
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
