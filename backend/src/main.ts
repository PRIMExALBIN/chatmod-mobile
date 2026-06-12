import { buildApp } from "./app.js";
import { env } from "./config/env.js";

const app = await buildApp({
  logger: env.NODE_ENV !== "test"
});

try {
  await app.listen({
    host: env.HOST,
    port: env.PORT
  });
} catch (error) {
  app.log.error(error);
  process.exit(1);
}
