import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Award, Ban, ShieldCheck } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import {
  useAdminCertificatesQuery,
  useRevokeCertificateMutation,
  usePermissions,
} from "@/hooks/queries";
import { PERMISSIONS, parseApiError, type AdminCertificateResponse } from "@/api";
import { PageHeader } from "@/components/dashboard/PageHeader";
import { PermissionGate } from "@/components/dashboard/PermissionGate";
import { Pager } from "@/components/dashboard/Pager";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { formatDate } from "@/lib/format";
import { toast } from "sonner";

const title = "Certificates";
const description = "Issued certificates and completion records.";

export const Route = createFileRoute("/certificates")({
  head: () => ({
    meta: [
      { title: `${title} — Lernova` },
      { name: "description", content: description },
      { property: "og:title", content: `${title} — Lernova` },
      { property: "og:description", content: description },
    ],
  }),
  component: () => (
    <PermissionGate permission={PERMISSIONS.CERTIFICATE_READ}>
      <CertificatesPage />
    </PermissionGate>
  ),
});

const PAGE_SIZE = 20;
const ALL = "ALL";

function CertificatesPage() {
  const { t } = useI18n();
  const { has } = usePermissions();
  const canRevoke = has(PERMISSIONS.CERTIFICATE_REVOKE);

  const [validity, setValidity] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [pendingRevoke, setPendingRevoke] = useState<AdminCertificateResponse | null>(null);

  const { data, isLoading, isError, error, refetch } = useAdminCertificatesQuery({
    ...(validity !== ALL ? { revoked: validity === "REVOKED" } : {}),
    page,
    size: PAGE_SIZE,
  });
  const revoke = useRevokeCertificateMutation();

  const confirmRevoke = () => {
    const target = pendingRevoke;
    if (!target?.id) return;
    revoke.mutate(target.id, {
      onSuccess: () => toast.success(`Certificate ${target.certificateNumber} revoked`),
      onError: (err) =>
        toast.error(parseApiError(err).message || "Could not revoke that certificate."),
      onSettled: () => setPendingRevoke(null),
    });
  };

  const rows = data?.content ?? [];

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <PageHeader title={t("page.certificates.title")} description={t("page.certificates.desc")} />

      <section className="card-surface p-4 sm:p-5">
        <div className="flex flex-wrap items-center gap-2">
          <Select
            value={validity}
            onValueChange={(v) => {
              setValidity(v);
              setPage(0);
            }}
          >
            <SelectTrigger className="w-44" aria-label="Filter by validity">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All certificates</SelectItem>
              <SelectItem value="VALID">Valid only</SelectItem>
              <SelectItem value="REVOKED">Revoked only</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="mt-4 overflow-x-auto">
          {isLoading ? (
            <div className="space-y-3 py-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex items-center gap-3">
                  <Skeleton className="h-4 w-32" />
                  <Skeleton className="h-4 flex-1" />
                  <Skeleton className="h-4 w-24" />
                </div>
              ))}
            </div>
          ) : isError ? (
            <div className="grid place-items-center py-14 text-center">
              <div className="max-w-sm">
                <p className="font-semibold">Could not load certificates</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {parseApiError(error).message || "The server did not respond."}
                </p>
                <Button variant="outline" size="sm" className="mt-3" onClick={() => refetch()}>
                  Try again
                </Button>
              </div>
            </div>
          ) : rows.length === 0 ? (
            <div className="grid place-items-center py-14 text-center">
              <div className="max-w-xs">
                <div className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-muted text-muted-foreground">
                  <Award className="h-5 w-5" />
                </div>
                <p className="mt-3 font-semibold">
                  {validity === ALL ? "No certificates issued yet" : "Nothing matches that filter"}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {validity === ALL
                    ? "One is issued automatically when a learner completes a course."
                    : "Try a different validity filter."}
                </p>
              </div>
            </div>
          ) : (
            <table className="w-full min-w-[48rem] text-sm">
              <thead>
                <tr className="border-b text-left text-xs uppercase tracking-wider text-muted-foreground rtl:text-right">
                  <th className="pb-2 pr-3 font-semibold">Number</th>
                  <th className="pb-2 pr-3 font-semibold">Learner</th>
                  <th className="pb-2 pr-3 font-semibold">Course</th>
                  <th className="pb-2 pr-3 font-semibold">Issued</th>
                  <th className="pb-2 pr-3 font-semibold">Validity</th>
                  {canRevoke && <th className="pb-2 text-right font-semibold rtl:text-left" />}
                </tr>
              </thead>
              <tbody className="divide-y">
                {rows.map((cert) => (
                  <tr key={cert.id}>
                    <td className="py-2.5 pr-3">
                      <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs">
                        {cert.certificateNumber}
                      </code>
                    </td>
                    <td className="py-2.5 pr-3">
                      <p className="truncate font-medium">{cert.studentName || "—"}</p>
                      <p className="truncate text-xs text-muted-foreground">{cert.studentEmail}</p>
                    </td>
                    <td className="max-w-[16rem] truncate py-2.5 pr-3">{cert.courseTitle}</td>
                    <td className="py-2.5 pr-3 text-xs text-muted-foreground">
                      {formatDate(cert.issuedAt)}
                    </td>
                    <td className="py-2.5 pr-3">
                      {cert.valid ? (
                        <Badge
                          variant="default"
                          className="h-5 gap-1 px-1.5 text-[10px] font-semibold"
                        >
                          <ShieldCheck className="h-3 w-3" />
                          Valid
                        </Badge>
                      ) : (
                        <Badge
                          variant="destructive"
                          className="h-5 gap-1 px-1.5 text-[10px] font-semibold"
                          title={`Revoked ${formatDate(cert.revokedAt)}`}
                        >
                          <Ban className="h-3 w-3" />
                          Revoked
                        </Badge>
                      )}
                    </td>
                    {canRevoke && (
                      <td className="py-2.5 text-right rtl:text-left">
                        {cert.valid && (
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={revoke.isPending}
                            onClick={() => setPendingRevoke(cert)}
                          >
                            Revoke
                          </Button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="mt-4">
          <Pager
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 0}
            totalElements={data?.totalElements ?? 0}
            onChange={setPage}
            noun="certificate"
          />
        </div>
      </section>

      <AlertDialog
        open={pendingRevoke !== null}
        onOpenChange={(open) => !open && setPendingRevoke(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Revoke this certificate?</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingRevoke?.certificateNumber} was issued to{" "}
              {pendingRevoke?.studentName || pendingRevoke?.studentEmail} for{" "}
              {pendingRevoke?.courseTitle}. Public verification will report it as revoked from now
              on. The record is kept — revoking does not delete it — but there is no undo here.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep it</AlertDialogCancel>
            <AlertDialogAction onClick={confirmRevoke} disabled={revoke.isPending}>
              Revoke
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
