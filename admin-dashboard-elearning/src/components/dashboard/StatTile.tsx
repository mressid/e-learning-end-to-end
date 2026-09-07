import type { LucideIcon } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import type { Permission } from "@/api";

/**
 * One number the platform actually knows.
 *
 * `permitted: false` says the tile exists but this administrator may not see
 * it — which is different from a zero, and reads differently too.
 */
export function StatTile({
  label,
  value,
  hint,
  icon: Icon,
  loading = false,
  permitted = true,
  permission,
}: {
  label: string;
  value: number | undefined;
  hint?: string;
  icon: LucideIcon;
  loading?: boolean;
  permitted?: boolean;
  permission?: Permission;
}) {
  return (
    <div className="card-surface p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {label}
          </p>
          {!permitted ? (
            <p className="mt-2 text-sm text-muted-foreground">
              Needs <code className="font-mono text-xs">{permission}</code>
            </p>
          ) : loading ? (
            <Skeleton className="mt-2 h-8 w-20" />
          ) : (
            <p className="mt-1 text-3xl font-extrabold tabular-nums tracking-tight">
              {(value ?? 0).toLocaleString()}
            </p>
          )}
        </div>
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
          <Icon className="h-4.5 w-4.5" />
        </span>
      </div>
      {permitted && hint ? (
        <p className="mt-2 truncate text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}
