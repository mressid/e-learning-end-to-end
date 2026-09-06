import { apiClient, parseApiError } from "../client";
import type {
  CertificateResponse,
  CertificateVerificationResponse,
  CertificateDownloadUrlResponse,
} from "../types";

export const certificatesApi = {
  async getMyCertificates(): Promise<CertificateResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/me/certificates");
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async verifyCertificate(code: string): Promise<CertificateVerificationResponse> {
    const { data, error } = await apiClient.GET("/api/v1/certificates/verify/{code}", {
      params: { path: { code } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async getCertificateDownloadUrl(certificateId: string): Promise<CertificateDownloadUrlResponse> {
    const { data, error } = await apiClient.GET(
      "/api/v1/certificates/{certificateId}/download-url",
      {
        params: { path: { certificateId } },
      },
    );
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async revokeCertificate(certificateId: string): Promise<void> {
    const { error } = await apiClient.POST("/api/v1/certificates/{certificateId}/revoke", {
      params: { path: { certificateId } },
    });
    if (error) throw parseApiError(error);
  },
};
