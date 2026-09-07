import { useState } from "react";
import { Radio, LogOut } from "lucide-react";
import {
  useUserSessionsQuery,
  useAdminSessionsQuery,
  useRevokeSessionMutation,
} from "@/hooks/queries";
import { parseApiError, type SessionResponse } from "@/api";
import { Pager } from "@/components/dashboard/Pager";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatRelative, formatDateTime } from "@/lib/format";
import { toast } from "sonner";

const PAGE_SIZE = 15;

/**
 * Who is signed in, and how to stop them being.
 *
 * Ending a session revokes the refresh token, which stops it renewing. The
 * access token already issued keeps working until it expires — revocation does
 * not reach back in time, and the panel says so rather than implying an instant
 * cut-off it cannot deliver.
 */
export function SessionsPanel() {
  return (
    <section className="card-surface space-y-4 p-5 sm:p-6">
      <div className="flex items-center gap-2">
        <div className="grid h-8 w-8 place-items-center rounded-lg bg-primary/10 text-primary">
          <Radio className="h-4 w-4" />
        </div>
        <div>
          <h2 className="text-lg font-bold">Live sessions</h2>
          <p className="text-xs text-muted-foreground">
            Ending one stops it renewing. Its current access token stays valid until it expires.
          </p>
        </div>
      </div>

      <Tabs defaultValue="learners">
        <TabsList>
          <TabsTrigger value="learners">Learners</TabsTrigger>
          <TabsTrigger value="admins">Administrators</TabsTrigger>
        </TabsList>
        <TabsContent value="learners" className="mt-4">
          <SessionTable kind="user" />
        </TabsContent>
        <TabsContent value="admins" className="mt-4">
          <SessionTable kind="admin" />
        </TabsContent>
      </Tabs>
    </section>
  );
}

function SessionTable({ kind }: { kind: "user" | "admin" }) {
  const [page, setPage] = useState(0);
  const params = { page, size: PAGE_SIZE };

  const learners = useUserSessionsQuery(params, kind === "user");
  const admins = useAdminSessionsQuery(params, kind === "admin");
  const query = kind === "user" ? learners : admins;

  const revoke = useRevokeSessionMutation();

  const end = (session: SessionResponse) => {
    if (!session.sessionId) return;
    revoke.mutate(
      { sessionId: session.sessionId, kind },
      {
        onSuccess: () =>
          toast.success(`Session for ${session.subjectLabel ?? "that account"} ended`),
        onError: (err) => toast.error(parseApiError(err).message || "Could not end that session."),
      },
    );
  };

  const rows = query.data?.content ?? [];

  if (query.isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="flex items-center gap-3">
            <Skeleton className="h-4 flex-1" />
            <Skeleton className="h-4 w-24" />
          </div>
        ))}
      </div>
    );
  }

  if (query.isError) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground">
        {parseApiError(query.error).message || "Could not load sessions."}
      </p>
    );
  }

  if (rows.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground">
        No {kind === "user" ? "learner" : "administrator"} is signed in right now.
      </p>
    );
  }

  return (
    <>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[38rem] text-sm">
          <thead>
            <tr className="border-b text-left text-xs uppercase tracking-wider text-muted-foreground rtl:text-right">
              <th className="pb-2 pr-3 font-semibold">Account</th>
              <th className="pb-2 pr-3 font-semibold">Started</th>
              <th className="pb-2 pr-3 font-semibold">Last used</th>
              <th className="pb-2 pr-3 font-semibold" title="Refreshes so far">
                Activity
              </th>
              <th className="pb-2 text-right font-semibold rtl:text-left" />
            </tr>
          </thead>
          <tbody className="divide-y">
            {rows.map((session) => (
              <tr key={session.sessionId}>
                <td className="py-2.5 pr-3 font-medium">{session.subjectLabel || "—"}</td>
                <td className="py-2.5 pr-3 text-xs text-muted-foreground">
                  {formatDateTime(session.startedAt)}
                </td>
                <td className="py-2.5 pr-3 text-xs text-muted-foreground">
                  {formatRelative(session.lastUsedAt)}
                </td>
                <td className="py-2.5 pr-3 text-xs tabular-nums text-muted-foreground">
                  {session.refreshCount ?? 0} refresh
                  {session.refreshCount === 1 ? "" : "es"}
                </td>
                <td className="py-2.5 text-right rtl:text-left">
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={revoke.isPending}
                    onClick={() => end(session)}
                  >
                    <LogOut className="h-3.5 w-3.5" />
                    End
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-4">
        <Pager
          page={query.data?.page ?? 0}
          totalPages={query.data?.totalPages ?? 0}
          totalElements={query.data?.totalElements ?? 0}
          onChange={setPage}
          noun="session"
        />
      </div>
    </>
  );
}
