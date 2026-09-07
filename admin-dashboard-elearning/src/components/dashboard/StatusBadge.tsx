import { Badge } from "@/components/ui/badge";

const TONE: Record<string, "default" | "secondary" | "outline" | "destructive"> = {
  ACTIVE: "default",
  PUBLISHED: "default",
  READY: "default",
  SUSPENDED: "destructive",
  DISABLED: "destructive",
  FAILED: "destructive",
  DRAFT: "secondary",
  PENDING: "secondary",
  ARCHIVED: "outline",
};

/** One consistent reading of the status strings the API returns. */
export function StatusBadge({ status }: { status: string | undefined }) {
  if (!status) return <span className="text-xs text-muted-foreground">—</span>;
  return (
    <Badge
      variant={TONE[status] ?? "outline"}
      className="h-5 px-1.5 text-[10px] font-semibold uppercase tracking-wider"
    >
      {status.toLowerCase()}
    </Badge>
  );
}
