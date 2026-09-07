import { apiClient, parseApiError } from "../client";
import type {
  AdminCertificateResponse,
  AdminCourseResponse,
  AdminMediaResponse,
  AdminSubmissionResponse,
  PageResponse,
} from "../types";
import type { PageParams } from "./admin-directory.api";

export interface AdminCourseListParams extends PageParams {
  q?: string;
  status?: string;
  owner?: string;
}

export interface CertificateListParams extends PageParams {
  courseId?: string;
  revoked?: boolean;
}

export interface MediaListParams extends PageParams {
  q?: string;
  status?: string;
}

export interface SubmissionListParams extends PageParams {
  status?: string;
}

/** Counts keyed by course status — `DRAFT`, `PUBLISHED`, `ARCHIVED`. */
export type CourseStats = Record<string, number>;

/**
 * Everything the platform holds, seen across all owners rather than one
 * instructor's own. These are the read surfaces behind the overview tiles and
 * the moderation pages.
 */
export const adminCatalogApi = {
  /** Requires `course.read`. Unlike `/courses`, this includes drafts. */
  async listCourses(params?: AdminCourseListParams): Promise<PageResponse<AdminCourseResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/courses", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<AdminCourseResponse>;
  },

  /** Requires `course.read`. */
  async courseStats(): Promise<CourseStats> {
    const { data, error } = await apiClient.GET("/api/v1/admin/courses/stats");
    if (error || !data) throw parseApiError(error);
    return data as CourseStats;
  },

  /** Requires `certificate.read`. */
  async listCertificates(
    params?: CertificateListParams,
  ): Promise<PageResponse<AdminCertificateResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/certificates", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<AdminCertificateResponse>;
  },

  /** Requires `certificate.read`. */
  async getCertificate(certificateId: string): Promise<AdminCertificateResponse> {
    const { data, error } = await apiClient.GET("/api/v1/admin/certificates/{certificateId}", {
      params: { path: { certificateId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Requires `submission.read`. */
  async listSubmissions(
    params?: SubmissionListParams,
  ): Promise<PageResponse<AdminSubmissionResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/submissions", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<AdminSubmissionResponse>;
  },

  /** Requires `media.read`. */
  async listMedia(params?: MediaListParams): Promise<PageResponse<AdminMediaResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/media", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<AdminMediaResponse>;
  },

  /**
   * Requires `media.delete`. The backend refuses while anything still points at
   * the file, so a 409 here means "still in use", not "try again".
   */
  async deleteMedia(mediaId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/admin/media/{mediaId}", {
      params: { path: { mediaId } },
    });
    if (error) throw parseApiError(error);
  },
};
