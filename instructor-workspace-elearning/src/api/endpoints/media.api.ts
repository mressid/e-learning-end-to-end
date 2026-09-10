import { apiClient, parseApiError } from "../client";
import type {
  CompleteMultipartUploadRequest,
  MediaObjectResponse,
  MultipartUploadTicket,
  PartRef,
  UploadTicketResponse,
} from "../types";

/**
 * Uploading a file, through whichever of the backend's two paths fits its size.
 *
 * The bytes never pass through our API: it hands back a signed URL (or, above
 * `MULTIPART_THRESHOLD_BYTES`, a plan of several signed URLs), the browser PUTs
 * straight to object storage, and a final call confirms the upload landed.
 * That is why every PUT below goes around `apiClient` — the signed URL carries
 * its own authorisation, and attaching our bearer token to a request aimed at
 * storage would at best be ignored and at worst break the signature.
 *
 * The PUT itself goes through `XMLHttpRequest`, not `fetch`: `fetch` exposes no
 * upload progress event, so there is no way to ask it how many bytes of a
 * large video have actually left the browser. `xhr.upload.onprogress` reports
 * real bytes, which is the only thing that makes `onProgress` below trustworthy
 * rather than a guess dressed up as a percentage.
 */

/**
 * Above this, a single PUT is the wrong tool even where storage would still
 * accept it: the simple path's signed URL lives for 15 minutes, which a large
 * file on a slow connection can outlast, and any blip during the transfer
 * costs the whole file rather than one part of it. 64 MB sits comfortably
 * above what a thumbnail or slide deck needs and comfortably below where a
 * flaky connection starts to matter, so it is a boundary we chose, not one the
 * backend imposes.
 */
export const MULTIPART_THRESHOLD_BYTES = 64 * 1024 * 1024;

/**
 * Parts uploaded at once. High enough to use the connection instead of
 * uploading one part, waiting, then the next; low enough that a slow or
 * retried part does not queue every remaining part behind it.
 */
const MAX_CONCURRENT_PARTS = 3;

/** Attempts for a single part before giving up on the whole upload. */
const MAX_PART_ATTEMPTS = 4;

const RETRY_BASE_DELAY_MS = 500;

export type UploadProgress = { stage: string; loaded: number; total: number };

/**
 * Enough of a multipart ticket to resume with: which upload it is, and the
 * plan the backend already committed to. A fresh `begin` call would return an
 * equivalent shape, so a caller that persisted this from `onTicket` can skip
 * `begin` entirely and go straight to listing what storage already has.
 */
export type ResumableUpload = Required<
  Pick<MultipartUploadTicket, "mediaId" | "uploadId" | "partSizeBytes" | "partCount">
>;

export interface UploadOptions {
  visibility?: "PRIVATE" | "PUBLIC";
  /** Byte-accurate progress, plus a phase label for the parts of the flow no byte count covers. */
  onProgress?: (progress: UploadProgress) => void;
  /** An existing multipart upload to resume, in place of beginning a new one. */
  resume?: ResumableUpload;
  /** Fires once a multipart upload has a ticket, so a caller can persist it and resume if the tab closes mid-upload. */
  onTicket?: (ticket: ResumableUpload) => void;
  signal?: AbortSignal;
}

/**
 * A retry cannot fix this one, so `uploadPartWithRetry` throws it straight
 * through instead of spending `MAX_PART_ATTEMPTS` on a problem that will look
 * identical on attempt four.
 */
class UnrecoverableUploadError extends Error {}

function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

/**
 * Spreads `signal` into a request's options only when one was actually
 * given. `exactOptionalPropertyTypes` treats an explicit `signal: undefined`
 * as a different type from an absent `signal` key, and both `openapi-fetch`'s
 * options and `putWithProgress`'s declare `signal` as optional rather than as
 * `AbortSignal | undefined` — so passing `options?.signal` straight through
 * fails to typecheck on every call where no signal was passed in.
 */
function withSignal(signal: AbortSignal | undefined) {
  return signal ? { signal } : {};
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener(
      "abort",
      () => {
        clearTimeout(timer);
        resolve();
      },
      { once: true },
    );
  });
}

/**
 * PUTs a blob to a signed URL and resolves with the response's `ETag` header
 * — the multipart complete call needs it, the simple path ignores it. A
 * `null` result usually means the bucket's CORS policy does not list `ETag`
 * in `Access-Control-Expose-Headers`, not that storage failed to send one; a
 * browser hides response headers a CORS policy does not expose, even on a
 * successful PUT.
 */
function putWithProgress(
  url: string,
  body: Blob,
  options: { contentType?: string; signal?: AbortSignal; onProgress?: (loaded: number) => void },
): Promise<string | null> {
  return new Promise((resolve, reject) => {
    if (options.signal?.aborted) {
      reject(new DOMException("The upload was cancelled.", "AbortError"));
      return;
    }

    const xhr = new XMLHttpRequest();
    xhr.open("PUT", url, true);
    // Signed into a simple-upload URL and absent from a part's — sending it on
    // a part changes the request enough that storage rejects the signature.
    if (options.contentType) {
      xhr.setRequestHeader("Content-Type", options.contentType);
    }

    // Every part of a multipart upload shares one signal, so a listener left
    // behind by a part that has already finished is a listener that stays for
    // the whole upload. A four-gigabyte file is hundreds of parts, and
    // `{ once: true }` does not help when the event never fires.
    const abortUpload = () => xhr.abort();
    const release = () => options.signal?.removeEventListener("abort", abortUpload);

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) options.onProgress?.(event.loaded);
    };

    xhr.onload = () => {
      release();
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.getResponseHeader("ETag"));
      } else {
        reject(
          parseApiError({
            message: `The file could not be stored (${xhr.status}). It may be too large, or the upload URL may have expired.`,
          }),
        );
      }
    };
    xhr.onerror = () => {
      release();
      reject(parseApiError({ message: "The upload failed because of a network error." }));
    };
    xhr.onabort = () => {
      release();
      reject(new DOMException("The upload was cancelled.", "AbortError"));
    };

    options.signal?.addEventListener("abort", abortUpload, { once: true });

    xhr.send(body);
  });
}

/**
 * Runs `worker` over `items` with at most `limit` in flight, stopping short
 * once one throws. Workers already running finish (or fail) on their own —
 * cancellation reaches them through the `AbortSignal` each worker was given,
 * not through this loop tearing anything down.
 */
async function runWithConcurrency<T>(
  items: T[],
  limit: number,
  worker: (item: T) => Promise<void>,
): Promise<void> {
  let cursor = 0;
  let firstError: unknown;

  async function runNext(): Promise<void> {
    for (;;) {
      if (firstError !== undefined) return;
      const index = cursor++;
      if (index >= items.length) return;
      try {
        await worker(items[index] as T);
      } catch (error) {
        firstError = error;
        return;
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, runNext));
  if (firstError !== undefined) throw firstError;
}

async function fetchStoredParts(
  mediaId: string,
  signal?: AbortSignal,
): Promise<Map<number, { etag: string; sizeBytes: number }>> {
  const { data, error } = await apiClient.GET("/api/v1/media/uploads/multipart/{mediaId}/parts", {
    params: { path: { mediaId } },
    ...withSignal(signal),
  });
  if (error) throw parseApiError(error);

  const stored = new Map<number, { etag: string; sizeBytes: number }>();
  for (const part of data ?? []) {
    if (part.partNumber != null && part.etag) {
      stored.set(part.partNumber, { etag: part.etag, sizeBytes: part.sizeBytes ?? 0 });
    }
  }
  return stored;
}

async function fetchPartUrl(
  mediaId: string,
  partNumber: number,
  signal?: AbortSignal,
): Promise<string> {
  const { data, error } = await apiClient.GET(
    "/api/v1/media/uploads/multipart/{mediaId}/parts/{partNumber}",
    { params: { path: { mediaId, partNumber } }, ...withSignal(signal) },
  );
  if (error || !data?.url) {
    throw parseApiError(error ?? { message: `No upload URL came back for part ${partNumber}.` });
  }
  return data.url;
}

async function uploadPartWithRetry(
  mediaId: string,
  partNumber: number,
  blob: Blob,
  signal: AbortSignal | undefined,
  onProgress: (loaded: number) => void,
): Promise<string> {
  let delay = RETRY_BASE_DELAY_MS;

  for (let attempt = 1; ; attempt += 1) {
    if (signal?.aborted) throw new DOMException("The upload was cancelled.", "AbortError");

    try {
      const url = await fetchPartUrl(mediaId, partNumber, signal);
      const etag = await putWithProgress(url, blob, { onProgress, ...withSignal(signal) });
      if (!etag) {
        throw new UnrecoverableUploadError(
          "Storage accepted the part but returned no ETag header. The bucket's CORS policy almost certainly omits ETag from Access-Control-Expose-Headers — without it the browser cannot read the header a completed multipart upload must send back, and no retry will change that.",
        );
      }
      return etag;
    } catch (error) {
      if (
        isAbort(error) ||
        error instanceof UnrecoverableUploadError ||
        attempt >= MAX_PART_ATTEMPTS
      ) {
        throw error;
      }
      onProgress(0);
      await sleep(delay, signal);
      delay *= 2;
    }
  }
}

/**
 * Best-effort cleanup for a cancelled upload — distinct from the public
 * `mediaApi.abortMultipartUpload` below, which a caller invokes directly and
 * expects to know if it failed. This one runs while already unwinding from a
 * cancellation, so a failure here must not replace the original reason we are
 * unwinding.
 */
async function abortMultipartUploadSilently(mediaId: string): Promise<void> {
  try {
    await apiClient.DELETE("/api/v1/media/uploads/multipart/{mediaId}", {
      params: { path: { mediaId } },
    });
  } catch {
    // Swallowed deliberately — see the doc comment above.
  }
}

async function uploadSimple(
  file: File,
  contentType: string,
  options?: UploadOptions,
): Promise<MediaObjectResponse> {
  const total = file.size;
  options?.onProgress?.({ stage: "Requesting an upload URL…", loaded: 0, total });

  const { data: ticket, error } = await apiClient.POST("/api/v1/media/uploads", {
    body: {
      contentType,
      filename: file.name,
      visibility: options?.visibility ?? "PRIVATE",
    },
    ...withSignal(options?.signal),
  });
  if (error || !ticket) throw parseApiError(error);
  const { mediaId, uploadUrl } = ticket as UploadTicketResponse;
  if (!mediaId || !uploadUrl) throw parseApiError({ message: "The upload ticket was incomplete." });

  await putWithProgress(uploadUrl, file, {
    contentType,
    onProgress: (loaded) => options?.onProgress?.({ stage: "Uploading…", loaded, total }),
    ...withSignal(options?.signal),
  });

  options?.onProgress?.({ stage: "Finishing…", loaded: total, total });
  const { data: media, error: completeError } = await apiClient.POST(
    "/api/v1/media/{mediaId}/complete",
    { params: { path: { mediaId } }, ...withSignal(options?.signal) },
  );
  if (completeError || !media) throw parseApiError(completeError);
  return media;
}

async function uploadMultipart(
  file: File,
  contentType: string,
  options?: UploadOptions,
): Promise<MediaObjectResponse> {
  const signal = options?.signal;
  const total = file.size;

  let ticket: ResumableUpload;
  if (options?.resume) {
    ticket = options.resume;
  } else {
    options?.onProgress?.({ stage: "Planning the upload…", loaded: 0, total });
    const { data, error } = await apiClient.POST("/api/v1/media/uploads/multipart", {
      body: {
        contentType,
        filename: file.name,
        sizeBytes: total,
        visibility: options?.visibility ?? "PRIVATE",
      },
      ...withSignal(signal),
    });
    if (error || !data) throw parseApiError(error);
    const { mediaId, uploadId, partSizeBytes, partCount } = data;
    if (!mediaId || !uploadId || !partSizeBytes || !partCount) {
      throw parseApiError({ message: "The multipart upload plan was incomplete." });
    }
    ticket = { mediaId, uploadId, partSizeBytes, partCount };
    options?.onTicket?.(ticket);
  }

  const { mediaId, partSizeBytes, partCount } = ticket;

  // What storage already holds. On a fresh upload this is empty; on a
  // resumed one it is the whole reason resuming costs the remainder rather
  // than the whole file.
  const stored = await fetchStoredParts(mediaId, signal);
  const completedEtags = new Map<number, string>();
  let confirmedLoaded = 0;
  for (const [partNumber, part] of stored) {
    completedEtags.set(partNumber, part.etag);
    confirmedLoaded += part.sizeBytes;
  }

  const perPartLoaded = new Map<number, number>();
  let reportedLoaded = confirmedLoaded;
  const reportProgress = (stage: string) => {
    let inFlight = 0;
    for (const loaded of perPartLoaded.values()) inFlight += loaded;
    // A retried part's own counter can dip back toward zero; the high-water
    // mark keeps the bar the caller draws from ever moving backwards because
    // of that, even though the raw sum underneath briefly could.
    reportedLoaded = Math.max(reportedLoaded, confirmedLoaded + inFlight);
    options?.onProgress?.({ stage, loaded: reportedLoaded, total });
  };
  reportProgress("Uploading…");

  const pendingParts = Array.from({ length: partCount }, (_, i) => i + 1).filter(
    (partNumber) => !completedEtags.has(partNumber),
  );

  try {
    await runWithConcurrency(pendingParts, MAX_CONCURRENT_PARTS, async (partNumber) => {
      const start = (partNumber - 1) * partSizeBytes;
      const end = Math.min(start + partSizeBytes, file.size);
      const blob = file.slice(start, end);

      const etag = await uploadPartWithRetry(mediaId, partNumber, blob, signal, (loaded) => {
        perPartLoaded.set(partNumber, loaded);
        reportProgress("Uploading…");
      });

      perPartLoaded.delete(partNumber);
      confirmedLoaded += blob.size;
      completedEtags.set(partNumber, etag);
      reportProgress("Uploading…");
    });
  } catch (uploadError) {
    // A cancelled upload should not go on being billed for parts nobody will
    // ever complete; a merely failed one should — the caller may retry with
    // `resume` and reuse everything already stored.
    if (isAbort(uploadError)) await abortMultipartUploadSilently(mediaId);
    throw uploadError;
  }

  reportProgress("Finishing…");
  const parts: PartRef[] = Array.from(completedEtags.entries())
    .sort(([a], [b]) => a - b)
    .map(([partNumber, etag]) => ({ partNumber, etag }));
  const body: CompleteMultipartUploadRequest = { parts };

  const { data: media, error: completeError } = await apiClient.POST(
    "/api/v1/media/uploads/multipart/{mediaId}/complete",
    { params: { path: { mediaId } }, body, ...withSignal(signal) },
  );
  if (completeError || !media) throw parseApiError(completeError);
  return media;
}

export const mediaApi = {
  /**
   * Uploads a file and returns the id to attach it by.
   *
   * Below `MULTIPART_THRESHOLD_BYTES` this is one signed PUT. Above it, or
   * whenever `options.resume` names an upload already in progress, it is the
   * multipart flow: parts uploaded a few at a time, retried individually on
   * failure, and resumable because storage remembers which ones already
   * landed.
   */
  async upload(file: File, options?: UploadOptions): Promise<MediaObjectResponse> {
    const contentType = file.type || "application/octet-stream";

    if (options?.resume || file.size > MULTIPART_THRESHOLD_BYTES) {
      return uploadMultipart(file, contentType, options);
    }
    return uploadSimple(file, contentType, options);
  },

  /**
   * Abandons a multipart upload and discards its parts, so storage stops
   * billing for bytes nobody will assemble. Only meaningful for the
   * multipart path — the simple path has nothing to abandon once the single
   * PUT has either landed or failed.
   */
  async abortMultipartUpload(mediaId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/media/uploads/multipart/{mediaId}", {
      params: { path: { mediaId } },
    });
    if (error) throw parseApiError(error);
  },

  /** A short-lived URL for reading a private object back. */
  async downloadUrl(mediaId: string): Promise<string> {
    const { data, error } = await apiClient.GET("/api/v1/media/{mediaId}/download-url", {
      params: { path: { mediaId } },
    });
    if (error || !data) throw parseApiError(error);
    return data.downloadUrl ?? "";
  },
};
