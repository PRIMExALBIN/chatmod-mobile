export class HttpError extends Error {
  constructor(
    public readonly statusCode: number,
    message: string
  ) {
    super(message);
    this.name = "HttpError";
  }
}

export function notFound(message: string): HttpError {
  return new HttpError(404, message);
}
