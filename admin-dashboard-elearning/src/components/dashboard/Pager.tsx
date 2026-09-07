import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Server-side paging. Every admin list is unbounded — learners, audit entries,
 * media — so none of them fetch everything and slice it in the browser.
 */
export function Pager({
  page,
  totalPages,
  totalElements,
  onChange,
  noun = "result",
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
  noun?: string;
}) {
  if (totalElements === 0) return null;

  const plural = totalElements === 1 ? noun : `${noun}s`;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-3 text-xs text-muted-foreground">
      <span>
        {totalElements.toLocaleString()} {plural}
        {totalPages > 1 ? ` · page ${page + 1} of ${totalPages}` : null}
      </span>
      {totalPages > 1 && (
        <div className="flex items-center gap-1.5">
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 0}
            onClick={() => onChange(page - 1)}
          >
            <ChevronLeft className="h-3.5 w-3.5 rtl:rotate-180" />
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => onChange(page + 1)}
          >
            Next
            <ChevronRight className="h-3.5 w-3.5 rtl:rotate-180" />
          </Button>
        </div>
      )}
    </div>
  );
}
