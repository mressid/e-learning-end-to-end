import { apiClient, parseApiError } from "../client";
import type { MediaObjectResponse, UploadTicketResponse } from "../types";

/**
 * Uploading a file, in the three steps the backend expects.
 *
 * The bytes never pass through the API: it hands back a signed URL, the browser
 * PUTs straight to object storage, and a third call confirms the upload landed.
 * That is why the middle step is a bare `fetch` and not `apiClient` — the signed
 * URL carries its own authorisation, and attaching our bearer token to a request
 * aimed at storage would at best be ignored and at worst break the signature.
 */
export const mediaApi = {
  /**
   * Uploads a file and returns the id to attach it by.
   *
   * `onProgress` is a coarse two-step signal rather than a byte count: `fetch`
   * cannot report upload progress, and faking a smooth bar would be a lie about
   * how far along a large video actually is.
   */
  async upload(
    file: File,
    options?: { visibility?: "PRIVATE" | "PUBLIC"; onProgress?: (stage: string) => void },
  ): Promise<MediaObjectResponse> {
    const contentType = file.type || "application/octet-stream";

    options?.onProgress?.("Requesting an upload URL…");
    const { data: ticket, error } = await apiClient.POST("/api/v1/media/uploads", {
      body: {
        contentType,
        filename: file.name,
        visibility: options?.visibility ?? "PRIVATE",
      },
    });
    if (error || !ticket) throw parseApiError(error);
    const { mediaId, uploadUrl } = ticket as UploadTicketResponse;
    if (!mediaId || !uploadUrl)
      throw parseApiError({ message: "The upload ticket was incomplete." });

    options?.onProgress?.("Uploading…");
    // The exact content type is signed into the URL; sending a different one
    // makes storage reject the request with a signature mismatch.
    const put = await fetch(uploadUrl, {
      method: "PUT",
      headers: { "Content-Type": contentType },
      body: file,
    });
    if (!put.ok) {
      throw parseApiError({
        message: `The file could not be stored (${put.status}). It may be too large, or the upload URL may have expired.`,
      });
    }

    options?.onProgress?.("Finishing…");
    const { data: media, error: completeError } = await apiClient.POST(
      "/api/v1/media/{mediaId}/complete",
      { params: { path: { mediaId } } },
    );
    if (completeError || !media) throw parseApiError(completeError);
    return media;
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
