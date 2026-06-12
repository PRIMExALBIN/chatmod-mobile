import { z } from "zod";

const controlCharacterPattern = /[\u0000-\u001F\u007F]/;

export const liveChatTextSchema = z.string()
  .transform((value) => value.trim())
  .pipe(
    z.string()
      .min(1, "Message text cannot be blank.")
      .max(200, "Message text must be 200 characters or fewer.")
      .refine((value) => !controlCharacterPattern.test(value), {
        message: "Message text cannot contain control characters."
      })
  );
