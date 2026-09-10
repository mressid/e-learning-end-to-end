import { useEffect, useState } from "react";
import { Download, ExternalLink, FileText, Link2, NotebookPen, Video } from "lucide-react";

import { useLessonQuery, useResourcesQuery } from "@/hooks/queries";
import { resourcesApi, type AttachedResourceResponse } from "@/api";
import { renderLessonBody, type LessonContentFormat } from "@/lib/render-lesson-body";
import { VideoPreview } from "@/components/workspace/VideoPreview";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatBytes } from "@/lib/format";

/** Roughly how long a lesson claims to take, in the words a student would use. */
function duration(seconds: number | null | undefined): string | null {
  if (!seconds) return null;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours} hr ${rest} min` : `${hours} hr`;
}

/**
 * A lesson as the person taking the course meets it.
 *
 * Read top to bottom, in the order the blocks were put in, with none of the
 * scaffolding that surrounds them in the editor. What is deliberately absent is
 * anything about progress: marking a lesson done needs an enrolment, and an
 * author has none, so this shows the course fully open rather than inventing a
 * completion state that would not match what a student sees.
 */
export function LessonPreview({ courseId, itemId }: { courseId: string; itemId: string }) {
  const lesson = useLessonQuery(courseId, itemId);
  const blocks = useResourcesQuery("item", itemId);

  if (lesson.isLoading || blocks.isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  // A 404 on the lesson is an item nobody has written yet, which a student
  // would meet as an empty page. Saying so is more use than an error.
  const rows = blocks.data ?? [];
  const details = lesson.data;
  const takes = duration(details?.durationSeconds);

  if (!details && rows.length === 0) {
    return (
      <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
        This lesson is empty. A student opening it now would find nothing here.
      </p>
    );
  }

  return (
    <article className="space-y-6">
      {(details?.description || takes) && (
        <header className="space-y-2">
          {takes && <p className="text-xs text-muted-foreground">About {takes}</p>}
          {details?.description && (
            <p className="text-sm leading-relaxed text-muted-foreground">{details.description}</p>
          )}
        </header>
      )}

      {rows.length === 0 ? (
        <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
          Described, but nothing to read or watch yet.
        </p>
      ) : (
        <div className="space-y-8">
          {rows.map((row) => (
            <BlockView key={row.resource?.id ?? row.position} itemId={itemId} row={row} />
          ))}
        </div>
      )}
    </article>
  );
}

function BlockView({ itemId, row }: { itemId: string; row: AttachedResourceResponse }) {
  const resource = row.resource;
  if (!resource?.id) return null;

  const sourceType = resource.sourceType ?? "INLINE";
  const resourceType = resource.resourceType ?? "DOCUMENT";
  const isVideo = resourceType === "VIDEO" && sourceType === "FILE";

  return (
    <section className="space-y-2">
      {resource.title && <h3 className="text-base font-semibold">{resource.title}</h3>}
      {resource.description && (
        <p className="text-sm text-muted-foreground">{resource.description}</p>
      )}

      {isVideo ? (
        <VideoPreview itemId={itemId} className="overflow-hidden rounded-xl border" />
      ) : sourceType === "INLINE" ? (
        <InlineBody
          content={resource.content ?? ""}
          format={(resource.contentType as LessonContentFormat | null) ?? "MARKDOWN"}
        />
      ) : sourceType === "URL" ? (
        <a
          href={resource.url ?? "#"}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 rounded-lg border px-3 py-2 text-sm hover:bg-secondary"
        >
          <Link2 className="h-4 w-4 shrink-0 text-muted-foreground" />
          <span className="min-w-0 truncate">{resource.url}</span>
          <ExternalLink className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        </a>
      ) : (
        <FileBlock resourceId={resource.id} name={resource.filename} size={resource.sizeBytes} />
      )}
    </section>
  );
}

/**
 * Written blocks go through the same sanitiser the editor's preview uses, so
 * what a student reads here is what they would read in the learner app.
 */
function InlineBody({ content, format }: { content: string; format: LessonContentFormat }) {
  if (!content.trim()) return null;
  const rendered = renderLessonBody(content, format);

  // Plain text comes back as a verdict rather than as markup, deliberately:
  // the whole point of the format is that nothing is interpreted, so it is
  // rendered as the text it is instead of being handed back through HTML.
  return rendered.kind === "html" ? (
    <div
      className="prose prose-sm dark:prose-invert max-w-none"
      dangerouslySetInnerHTML={{ __html: rendered.html }}
    />
  ) : (
    <p className="whitespace-pre-wrap text-sm leading-relaxed">{content}</p>
  );
}

/**
 * A file a student would download.
 *
 * The URL is fetched on click rather than at render: it is short-lived and
 * signed, so one drawn when the page loaded would be dead by the time anybody
 * reading a long lesson reached the bottom of it.
 */
function FileBlock({
  resourceId,
  name,
  size,
}: {
  resourceId: string;
  name: string | null | undefined;
  size: number | null | undefined;
}) {
  const [busy, setBusy] = useState(false);

  const open = async () => {
    setBusy(true);
    try {
      const url = await resourcesApi.downloadUrl(resourceId);
      if (url) window.open(url, "_blank", "noopener,noreferrer");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex items-center gap-3 rounded-lg border p-3">
      <FileText className="h-5 w-5 shrink-0 text-muted-foreground" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm">{name || "Attachment"}</p>
        {size ? <p className="text-xs text-muted-foreground">{formatBytes(size)}</p> : null}
      </div>
      <Button variant="outline" size="sm" disabled={busy} onClick={open}>
        <Download className="mr-1.5 h-3.5 w-3.5" />
        Download
      </Button>
    </div>
  );
}
