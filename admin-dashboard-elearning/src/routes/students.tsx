import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Search, ShieldOff, ShieldCheck, LogOut, Users, X } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import {
  useAdminUsersQuery,
  useSetUserStatusMutation,
  useRevokeAllUserSessionsMutation,
  usePermissions,
  type AccountStatus,
} from "@/hooks/queries";
import { PERMISSIONS, parseApiError, type DirectoryUserResponse } from "@/api";
import { PageHeader } from "@/components/dashboard/PageHeader";
import { PermissionGate } from "@/components/dashboard/PermissionGate";
import { Pager } from "@/components/dashboard/Pager";
import { StatusBadge } from "@/components/dashboard/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { formatDate, formatRelative } from "@/lib/format";
import { toast } from "sonner";

const title = "Students";
const description = "Manage learners, cohorts and enrollment status.";

export const Route = createFileRoute("/students")({
  head: () => ({
    meta: [
      { title: `${title} — Lernova` },
      { name: "description", content: description },
      { property: "og:title", content: `${title} — Lernova` },
      { property: "og:description", content: description },
    ],
  }),
  component: () => (
    <PermissionGate permission={PERMISSIONS.USER_READ}>
      <StudentsPage />
    </PermissionGate>
  ),
});

const PAGE_SIZE = 20;
const STATUSES: AccountStatus[] = ["ACTIVE", "SUSPENDED", "DISABLED"];
/** The filter's "no filter" value. Radix Select cannot hold an empty string. */
const ANY = "ANY";

function StudentsPage() {
  const { t } = useI18n();
  const { has } = usePermissions();
  const canSuspend = has(PERMISSIONS.USER_SUSPEND);
  const canManageSessions = has(PERMISSIONS.SETTINGS_MANAGE);

  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<string>(ANY);
  const [page, setPage] = useState(0);

  const params = {
    ...(query ? { q: query } : {}),
    ...(status !== ANY ? { status } : {}),
    page,
    size: PAGE_SIZE,
  };
  const { data, isLoading, isError, error, refetch } = useAdminUsersQuery(params);

  const setStatusMutation = useSetUserStatusMutation();
  const revokeSessions = useRevokeAllUserSessionsMutation();

  // Search is submitted rather than typed-through: each keystroke would be a
  // new server query and a new cache entry.
  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setQuery(search.trim());
    setPage(0);
  };

  const clearSearch = () => {
    setSearch("");
    setQuery("");
    setPage(0);
  };

  const changeStatus = (user: DirectoryUserResponse, next: AccountStatus) => {
    if (!user.id) return;
    setStatusMutation.mutate(
      { userId: user.id, status: next },
      {
        onSuccess: () =>
          toast.success(
            next === "ACTIVE"
              ? `${user.username} reinstated`
              : `${user.username} ${next.toLowerCase()}`,
          ),
        onError: (err) => toast.error(parseApiError(err).message || "That did not work."),
      },
    );
  };

  const signOutEverywhere = (user: DirectoryUserResponse) => {
    if (!user.id) return;
    revokeSessions.mutate(user.id, {
      onSuccess: () => toast.success(`Signed ${user.username} out of every device`),
      onError: (err) => toast.error(parseApiError(err).message || "That did not work."),
    });
  };

  const rows = data?.content ?? [];
  const filtered = Boolean(query) || status !== ANY;

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <PageHeader title={t("page.students.title")} description={t("page.students.desc")} />

      <section className="card-surface p-4 sm:p-5">
        <div className="flex flex-wrap items-center gap-2">
          <form onSubmit={submitSearch} className="relative min-w-0 flex-1 sm:max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground rtl:left-auto rtl:right-3" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search name or email…"
              className="pl-9 rtl:pl-3 rtl:pr-9"
              aria-label="Search learners"
            />
            {search && (
              <button
                type="button"
                onClick={clearSearch}
                aria-label="Clear search"
                className="absolute right-2 top-1/2 grid h-6 w-6 -translate-y-1/2 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground rtl:right-auto rtl:left-2"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </form>

          <Select
            value={status}
            onValueChange={(v) => {
              setStatus(v);
              setPage(0);
            }}
          >
            <SelectTrigger className="w-40" aria-label="Filter by status">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Any status</SelectItem>
              {STATUSES.map((s) => (
                <SelectItem key={s} value={s}>
                  {s.charAt(0) + s.slice(1).toLowerCase()}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="mt-4 overflow-x-auto">
          {isLoading ? (
            <TableSkeleton />
          ) : isError ? (
            <ErrorPanel error={error} onRetry={() => refetch()} />
          ) : rows.length === 0 ? (
            <EmptyRow filtered={filtered} onClear={clearSearch} />
          ) : (
            <table className="w-full min-w-[44rem] text-sm">
              <thead>
                <tr className="border-b text-left text-xs uppercase tracking-wider text-muted-foreground rtl:text-right">
                  <th className="pb-2 pr-3 font-semibold">Learner</th>
                  <th className="pb-2 pr-3 font-semibold">Status</th>
                  <th className="pb-2 pr-3 font-semibold">Joined</th>
                  <th className="pb-2 pr-3 font-semibold">Last seen</th>
                  <th className="pb-2 text-right font-semibold rtl:text-left">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {rows.map((user) => (
                  <UserRow
                    key={user.id}
                    user={user}
                    canSuspend={canSuspend}
                    canManageSessions={canManageSessions}
                    busy={setStatusMutation.isPending || revokeSessions.isPending}
                    onChangeStatus={changeStatus}
                    onSignOut={signOutEverywhere}
                  />
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
            noun="learner"
          />
        </div>
      </section>
    </div>
  );
}

function UserRow({
  user,
  canSuspend,
  canManageSessions,
  busy,
  onChangeStatus,
  onSignOut,
}: {
  user: DirectoryUserResponse;
  canSuspend: boolean;
  canManageSessions: boolean;
  busy: boolean;
  onChangeStatus: (user: DirectoryUserResponse, next: AccountStatus) => void;
  onSignOut: (user: DirectoryUserResponse) => void;
}) {
  const suspended = user.status !== "ACTIVE";

  return (
    <tr className="align-middle">
      <td className="py-2.5 pr-3">
        <div className="flex items-center gap-2.5">
          <span
            aria-hidden
            className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-primary/10 text-[11px] font-bold text-primary"
          >
            {(user.displayName || user.username || "?").slice(0, 2).toUpperCase()}
          </span>
          <div className="min-w-0">
            <p className="truncate font-medium">{user.displayName || user.username}</p>
            <p className="truncate text-xs text-muted-foreground">{user.email}</p>
          </div>
        </div>
      </td>
      <td className="py-2.5 pr-3">
        <StatusBadge status={user.status} />
      </td>
      <td className="py-2.5 pr-3 text-xs text-muted-foreground">{formatDate(user.createdAt)}</td>
      <td className="py-2.5 pr-3 text-xs text-muted-foreground">
        {formatRelative(user.lastLoginAt)}
      </td>
      <td className="py-2.5 text-right rtl:text-left">
        <div className="flex justify-end gap-1.5 rtl:justify-start">
          {canManageSessions && (
            <Button
              variant="ghost"
              size="sm"
              disabled={busy}
              onClick={() => onSignOut(user)}
              title="End every session for this learner"
            >
              <LogOut className="h-3.5 w-3.5" />
              <span className="sr-only sm:not-sr-only">Sign out</span>
            </Button>
          )}
          {canSuspend && (
            <Button
              variant={suspended ? "outline" : "ghost"}
              size="sm"
              disabled={busy}
              onClick={() => onChangeStatus(user, suspended ? "ACTIVE" : "SUSPENDED")}
              title={
                suspended
                  ? "Restore this account"
                  : "Suspend this account. Existing sessions keep working until they expire — end them too if that matters."
              }
            >
              {suspended ? (
                <ShieldCheck className="h-3.5 w-3.5" />
              ) : (
                <ShieldOff className="h-3.5 w-3.5" />
              )}
              <span className="sr-only sm:not-sr-only">{suspended ? "Reinstate" : "Suspend"}</span>
            </Button>
          )}
        </div>
      </td>
    </tr>
  );
}

function TableSkeleton() {
  return (
    <div className="space-y-3 py-2">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton className="h-8 w-8 shrink-0 rounded-full" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-24" />
        </div>
      ))}
    </div>
  );
}

function EmptyRow({ filtered, onClear }: { filtered: boolean; onClear: () => void }) {
  return (
    <div className="grid place-items-center py-14 text-center">
      <div className="max-w-xs">
        <div className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-muted text-muted-foreground">
          <Users className="h-5 w-5" />
        </div>
        <p className="mt-3 font-semibold">
          {filtered ? "No learners match that" : "No learners yet"}
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          {filtered
            ? "Try a different search or clear the filters."
            : "Accounts appear here as soon as people register."}
        </p>
        {filtered && (
          <Button variant="outline" size="sm" className="mt-3" onClick={onClear}>
            Clear filters
          </Button>
        )}
      </div>
    </div>
  );
}

function ErrorPanel({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  // `message` is optional on ApiError, so a network failure with no body still
  // needs something to show.
  const message = parseApiError(error).message || "The server did not respond.";
  return (
    <div className="grid place-items-center py-14 text-center">
      <div className="max-w-sm">
        <p className="font-semibold">Could not load learners</p>
        <p className="mt-1 text-sm text-muted-foreground">{message}</p>
        <Button variant="outline" size="sm" className="mt-3" onClick={onRetry}>
          Try again
        </Button>
      </div>
    </div>
  );
}
