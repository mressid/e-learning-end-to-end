import { useState } from "react";
import { ScrollText, ChevronDown, ChevronRight } from "lucide-react";
import { useAuditQuery } from "@/hooks/queries";
import { parseApiError, type AuditEntryResponse } from "@/api";
import { Pager } from "@/components/dashboard/Pager";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDateTime } from "@/lib/format";

const PAGE_SIZE = 25;

/**
 * The audit trail.
 *
 * Read-only, and that is the point — there is no endpoint that edits or deletes
 * an entry, and no button here should ever pretend otherwise. A log with a
 * delete button is not evidence of anything.
 */
export function AuditPanel() {
  const [action, setAction] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);

  const audit = useAuditQuery({
    ...(query ? { action: query } : {}),
    page,
    size: PAGE_SIZE,
  });

  const rows = audit.data?.content ?? [];

  return (
    <section className="card-surface space-y-4 p-5 sm:p-6">
      <div className="flex items-center gap-2">
        <div className="grid h-8 w-8 place-items-center rounded-lg bg-primary/10 text-primary">
          <ScrollText className="h-4 w-4" />
        </div>
        <div>
          <h2 className="text-lg font-bold">Audit trail</h2>
          <p className="text-xs text-muted-foreground">
            Append-only. Nothing here can be edited or deleted, by anyone.
          </p>
        </div>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setQuery(action.trim());
          setPage(0);
        }}
        className="max-w-xs"
      >
        <Input
          value={action}
          onChange={(e) => setAction(e.target.value)}
          placeholder="Filter by action, e.g. role.created"
          aria-label="Filter by action"
        />
      </form>

      {audit.isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      ) : audit.isError ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          {parseApiError(audit.error).message || "Could not load the audit trail."}
        </p>
      ) : rows.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          {query ? `Nothing recorded for "${query}".` : "Nothing recorded yet."}
        </p>
      ) : (
        <ul className="divide-y">
          {rows.map((entry) => (
            <AuditRow key={entry.id} entry={entry} />
          ))}
        </ul>
      )}

      <Pager
        page={audit.data?.page ?? 0}
        totalPages={audit.data?.totalPages ?? 0}
        totalElements={audit.data?.totalElements ?? 0}
        onChange={setPage}
        noun="entry"
      />
    </section>
  );
}

function AuditRow({ entry }: { entry: AuditEntryResponse }) {
  const [open, setOpen] = useState(false);
  const details = entry.details ?? {};
  const hasDetails = Object.keys(details).length > 0;

  return (
    <li className="py-2.5">
      <div className="flex items-start gap-2">
        {hasDetails ? (
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            aria-expanded={open}
            aria-label={open ? "Hide details" : "Show details"}
            className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded text-muted-foreground hover:bg-secondary hover:text-foreground"
          >
            {open ? (
              <ChevronDown className="h-3.5 w-3.5" />
            ) : (
              <ChevronRight className="h-3.5 w-3.5 rtl:rotate-180" />
            )}
          </button>
        ) : (
          <span className="mt-0.5 h-5 w-5 shrink-0" />
        )}

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm">{entry.summary}</p>
            <Badge variant="outline" className="h-4 px-1 font-mono text-[10px]">
              {entry.action}
            </Badge>
          </div>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {/* The label is who they were at the time, not who that id resolves
                to now — which is why the record keeps it rather than a join. */}
            {entry.actorLabel || entry.actorType} · {formatDateTime(entry.occurredAt)}
            {entry.requestId ? (
              <>
                {" · "}
                <code className="font-mono text-[10px]" title="Matches the X-Request-Id header">
                  {entry.requestId.slice(0, 8)}
                </code>
              </>
            ) : null}
          </p>

          {open && hasDetails && (
            <pre className="mt-2 overflow-x-auto rounded-lg bg-muted p-2.5 text-[11px] leading-relaxed">
              {JSON.stringify(details, null, 2)}
            </pre>
          )}
        </div>
      </div>
    </li>
  );
}
