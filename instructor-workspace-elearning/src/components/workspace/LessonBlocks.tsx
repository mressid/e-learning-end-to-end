import { useState, type FormEvent } from "react";
import { Download, FileText, Link2, NotebookPen, Trash2, Upload, Video } from "lucide-react";
import { toast } from "sonner";

import {
  useAddResourceMutation,
  useDetachResourceMutation,
  useReorderItemResourcesMutation,
  useResourcesQuery,
  useUpdateResourceMutation,
} from "@/hooks/queries";
import {
  parseApiError,
  resourcesApi,
  type AttachedResourceResponse,
  type CreateResourceRequest,
  type LessonContentFormat,
  type ResourceType,
  type SourceType,
} from "@/api";
import { formatBytes } from "@/lib/format";
import {
  DragHandle,
  SortableList,
  SortableRow,
  type DragHandleProps,
} from "@/components/workspace/Sortable";
import { BodyEditor } from "@/components/workspace/BodyEditor";
import { VideoPreview } from "@/components/workspace/VideoPreview";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
import { cn } from "@/lib/utils";

const FORMATS: LessonContentFormat[] = ["MARKDOWN", "HTML", "PLAIN_TEXT"];

const FORMAT_LABEL: Record<LessonContentFormat, string> = {
  MARKDOWN: "Markdown",
  HTML: "HTML",
  PLAIN_TEXT: "Plain text",
};

/** What the add bar offers. Narrower than `ResourceType` × `SourceType`, on purpose — a block
 * is either written here, linked, a plain file, or specifically a video, and each of those
 * already implies both fields. */
type BlockKind = "INLINE" | "URL" | "FILE" | "VIDEO";

const ADD_KINDS: { kind: BlockKind; label: string; icon: typeof NotebookPen }[] = [
  { kind: "INLINE", label: "Writing", icon: NotebookPen },
  { kind: "URL", label: "Link", icon: Link2 },
  { kind: "FILE", label: "File", icon: Upload },
  { kind: "VIDEO", label: "Video", icon: Video },
];

/** Reported once, the same way everywhere: the server's own message first, because a refused
 * save (`RESOURCE_SHARED`, an invalid URL) already explains itself better than any fallback. */
function fail(err: unknown, fallback: string) {
  toast.error(parseApiError(err).message || fallback);
}

/** The server refuses any scheme but http/https for a URL block. Checking here means a bad
 * link is a red line under the input, not a round trip that comes back a 422. */
function isHttpUrl(value: string): boolean {
  try {
    const protocol = new URL(value).protocol;
    return protocol === "http:" || protocol === "https:";
  } catch {
    return false;
  }
}

/**
 * The ordered list of blocks a lesson is made of.
 *
 * A lesson used to be exactly one material — a video, or a page, or a link. Now it is a
 * sequence of them, read top to bottom, and this is the whole editor for that sequence:
 * reordering, editing each block in place, adding a new one of four kinds, and removing one
 * from the lesson without touching the material it points at.
 */
export function LessonBlocks({ itemId }: { itemId: string }) {
  const { data, isLoading, isError, error, refetch } = useResourcesQuery("item", itemId);
  const reorder = useReorderItemResourcesMutation(itemId);

  const rows = data ?? [];
  const ids = rows.map((row) => row.resource?.id ?? "").filter(Boolean);

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold">Lesson content</h3>

      {isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-20 w-full rounded-lg" />
          <Skeleton className="h-20 w-full rounded-lg" />
        </div>
      ) : isError ? (
        <div className="rounded-lg border border-dashed p-4 text-center">
          <p className="text-sm text-muted-foreground">
            {parseApiError(error).message || "Could not load the blocks for this lesson."}
          </p>
          <Button variant="outline" size="sm" className="mt-2" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : rows.length === 0 ? (
        <p className="rounded-lg border border-dashed p-6 text-center text-xs text-muted-foreground">
          A lesson is built from blocks, read top to bottom — a video, then some written notes, then
          a download. Add the first one below.
        </p>
      ) : (
        <SortableList
          ids={ids}
          className="space-y-2"
          onReorder={(orderedIds) =>
            reorder.mutate(orderedIds, {
              onError: (err) => fail(err, "Could not reorder those blocks."),
            })
          }
        >
          {rows.map((row, position) => {
            const resourceId = row.resource?.id ?? "";
            return (
              <SortableRow key={resourceId} id={resourceId}>
                {({ handle, isDragging }) => (
                  <BlockCard
                    itemId={itemId}
                    row={row}
                    position={position}
                    handle={handle}
                    isDragging={isDragging}
                  />
                )}
              </SortableRow>
            );
          })}
        </SortableList>
      )}

      <AddBlockBar itemId={itemId} />
    </div>
  );
}

function BlockCard({
  itemId,
  row,
  position,
  handle,
  isDragging,
}: {
  itemId: string;
  row: AttachedResourceResponse;
  position: number;
  handle: DragHandleProps;
  isDragging: boolean;
}) {
  const resource = row.resource;
  const update = useUpdateResourceMutation("item", itemId);
  const detach = useDetachResourceMutation("item", itemId);

  const sourceType = (resource?.sourceType as SourceType | undefined) ?? "INLINE";
  const resourceType = (resource?.resourceType as ResourceType | undefined) ?? "DOCUMENT";
  const isVideo = resourceType === "VIDEO" && sourceType === "FILE";

  const [title, setTitle] = useState(resource?.title ?? "");
  const [content, setContent] = useState(resource?.content ?? "");
  const [contentType, setContentType] = useState<LessonContentFormat>(
    (resource?.contentType as LessonContentFormat | null | undefined) ?? "MARKDOWN",
  );
  const [url, setUrl] = useState(resource?.url ?? "");
  const [confirmOpen, setConfirmOpen] = useState(false);

  // A row without a resource id cannot be saved, downloaded or removed — nothing below this
  // is meaningful without it, so there is nothing worth rendering either.
  if (!resource?.id) return null;
  const resourceId = resource.id;

  const savedTitle = resource.title ?? "";
  const savedContent = resource.content ?? "";
  const savedContentType = (resource.contentType as LessonContentFormat | null) ?? "MARKDOWN";
  const savedUrl = resource.url ?? "";

  const urlValid = sourceType !== "URL" || !url.trim() || isHttpUrl(url.trim());
  const isDirty =
    title !== savedTitle ||
    (sourceType === "INLINE" && (content !== savedContent || contentType !== savedContentType)) ||
    (sourceType === "URL" && url !== savedUrl);
  const canSave = isDirty && urlValid && title.trim().length > 0 && !update.isPending;

  const save = () => {
    if (!canSave) return;
    update.mutate(
      {
        resourceId,
        title: title.trim(),
        ...(sourceType === "INLINE" ? { content, contentType } : {}),
        ...(sourceType === "URL" ? { url: url.trim() } : {}),
      },
      {
        onSuccess: () => toast.success("Saved."),
        onError: (err) => fail(err, "Could not save that block."),
      },
    );
  };

  const openDownload = async () => {
    try {
      const downloadUrl = await resourcesApi.downloadUrl(resourceId);
      if (downloadUrl) window.open(downloadUrl, "_blank", "noopener,noreferrer");
    } catch (err) {
      fail(err, "Could not open that file.");
    }
  };

  const remove = () => {
    detach.mutate(resourceId, {
      onSuccess: () => toast.success("Removed from this lesson."),
      onError: (err) => fail(err, "Could not remove that block."),
    });
    setConfirmOpen(false);
  };

  const Icon = isVideo
    ? Video
    : sourceType === "URL"
      ? Link2
      : sourceType === "INLINE"
        ? NotebookPen
        : FileText;

  return (
    <div
      className={cn(
        "rounded-lg border bg-card p-3",
        isDragging && "relative border-primary/60 shadow-lg",
      )}
    >
      <div className="flex items-start gap-2.5">
        <DragHandle handle={handle} label={`Reorder ${resource.title ?? "this block"}`} />
        <span
          aria-hidden
          className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-md bg-secondary text-muted-foreground"
        >
          <Icon className="h-3.5 w-3.5" />
        </span>

        <div className="min-w-0 flex-1 space-y-2.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="w-4 shrink-0 text-center text-[11px] font-medium tabular-nums text-muted-foreground">
              {position + 1}
            </span>
            <Badge
              variant="outline"
              className="h-4 shrink-0 px-1 text-[9px] uppercase tracking-wider"
            >
              {resourceType.replace("_", " ").toLowerCase()}
            </Badge>
            <Input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Title"
              className="h-7 min-w-0 flex-1 text-sm"
            />
            <div className="ml-auto flex shrink-0 items-center gap-1">
              <Button size="sm" disabled={!canSave} onClick={save}>
                {update.isPending ? "Saving…" : "Save"}
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                title="Remove from this lesson"
                onClick={() => setConfirmOpen(true)}
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>

          {sourceType === "INLINE" && (
            <div className="space-y-2">
              <div className="flex items-center justify-end">
                <Select
                  value={contentType}
                  onValueChange={(v) => setContentType(v as LessonContentFormat)}
                >
                  <SelectTrigger className="h-7 w-32 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {FORMATS.map((f) => (
                      <SelectItem key={f} value={f}>
                        {FORMAT_LABEL[f]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* The editor the whole-page lesson used, rather than a plainer
                  one written for here: it carries the markdown toolbar, and
                  losing that to a refactor would be a quiet downgrade for
                  anyone who writes in markdown. Re-keyed on the format so a
                  switch to HTML swaps the toolbar in and out cleanly, since it
                  reads its text once at mount. */}
              <BodyEditor
                key={`${resourceId}:${contentType}`}
                initialText={content}
                contentFormat={contentType}
                onTextChange={setContent}
                minHeight="min-h-[18rem]"
              />
            </div>
          )}

          {sourceType === "URL" && (
            <div className="space-y-1">
              <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://…" />
              {url.trim() && !urlValid && (
                <p className="text-xs text-destructive">Must start with http:// or https://.</p>
              )}
            </div>
          )}

          {sourceType === "FILE" && (
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                <span className="truncate">{resource.filename ?? "Uploaded file"}</span>
                <span aria-hidden>·</span>
                <span>{formatBytes(resource.sizeBytes)}</span>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="h-6 px-1.5"
                  onClick={() => void openDownload()}
                >
                  <Download className="h-3.5 w-3.5" />
                  Download
                </Button>
              </div>
              {isVideo && <VideoPreview itemId={itemId} />}
            </div>
          )}
        </div>
      </div>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove this block?</AlertDialogTitle>
            <AlertDialogDescription>
              "{resource.title}" is removed from this lesson. The material itself is not deleted —
              it is a library resource, and it may still be attached to other lessons or courses.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className={buttonVariants({ variant: "destructive" })}
              onClick={remove}
            >
              Remove
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function AddBlockBar({ itemId }: { itemId: string }) {
  const add = useAddResourceMutation("item", itemId);
  const [open, setOpen] = useState<BlockKind | null>(null);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {ADD_KINDS.map(({ kind, label, icon: KindIcon }) => (
          <Button
            key={kind}
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setOpen(kind)}
          >
            <KindIcon className="h-3.5 w-3.5" />
            {label}
          </Button>
        ))}
      </div>
      {open && <AddBlockForm itemId={itemId} kind={open} add={add} onClose={() => setOpen(null)} />}
    </div>
  );
}

function AddBlockForm({
  itemId,
  kind,
  add,
  onClose,
}: {
  itemId: string;
  kind: BlockKind;
  add: ReturnType<typeof useAddResourceMutation>;
  onClose: () => void;
}) {
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [contentType, setContentType] = useState<LessonContentFormat>("MARKDOWN");
  const [url, setUrl] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [stage, setStage] = useState("");
  const [formError, setFormError] = useState("");

  // Each kind implies both halves of what the API actually asks for — what the material is
  // and where it lives — so the add bar only has to ask which kind, not both questions.
  const resourceType: ResourceType =
    kind === "URL" ? "LINK" : kind === "VIDEO" ? "VIDEO" : "DOCUMENT";
  const sourceType: SourceType = kind === "INLINE" ? "INLINE" : kind === "URL" ? "URL" : "FILE";

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setFormError("");
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setFormError("Give it a title.");
      return;
    }
    if (kind === "URL" && !isHttpUrl(url.trim())) {
      setFormError("Must start with http:// or https://.");
      return;
    }
    if ((kind === "FILE" || kind === "VIDEO") && !file) {
      setFormError(kind === "VIDEO" ? "Choose a video to upload." : "Choose a file to upload.");
      return;
    }

    const body: CreateResourceRequest & {
      file?: File;
      onProgress?: (stage: string) => void;
    } = {
      title: trimmedTitle,
      resourceType,
      sourceType,
      ...(sourceType === "URL" ? { url: url.trim() } : {}),
      ...(sourceType === "INLINE" ? { content, contentType } : {}),
      ...(sourceType === "FILE" && file ? { file, onProgress: setStage } : {}),
    };

    add.mutate(body, {
      onSuccess: () => {
        toast.success(`"${trimmedTitle}" added.`);
        onClose();
      },
      onError: (err) => {
        setStage("");
        setFormError(parseApiError(err).message || "Could not add that block.");
      },
    });
  };

  return (
    <form onSubmit={submit} className="space-y-3 rounded-lg border bg-muted/30 p-3">
      <div className="space-y-1.5">
        <Label htmlFor="block-title">Title</Label>
        <Input
          id="block-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          autoFocus
        />
      </div>

      {kind === "INLINE" && (
        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="flex-1 space-y-1.5">
            <Label htmlFor="block-content">Text</Label>
            <Textarea
              id="block-content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={5}
            />
          </div>
          <div className="space-y-1.5">
            <Label>Format</Label>
            <Select
              value={contentType}
              onValueChange={(v) => setContentType(v as LessonContentFormat)}
            >
              <SelectTrigger className="w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {FORMATS.map((f) => (
                  <SelectItem key={f} value={f}>
                    {FORMAT_LABEL[f]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      )}

      {kind === "URL" && (
        <div className="space-y-1.5">
          <Label htmlFor="block-url">Link</Label>
          <Input
            id="block-url"
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://…"
          />
        </div>
      )}

      {(kind === "FILE" || kind === "VIDEO") && (
        <div className="space-y-1.5">
          <Label htmlFor="block-file">{kind === "VIDEO" ? "Video file" : "File"}</Label>
          <Input
            id="block-file"
            type="file"
            accept={kind === "VIDEO" ? "video/*" : undefined}
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          {file && (
            <p className="text-xs text-muted-foreground">
              {file.name} · {formatBytes(file.size)}
            </p>
          )}
          {/* Checking the file plays, before spending a minute uploading it, is the whole
              point of previewing it here — the same reasoning VideoPreview documents for the
              lesson-level picker. */}
          {kind === "VIDEO" && file && <VideoPreview itemId={itemId} localFile={file} />}
        </div>
      )}

      {formError && <p className="text-sm text-destructive">{formError}</p>}
      {stage && !formError && <p className="text-xs text-muted-foreground">{stage}</p>}

      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={add.isPending}>
          {add.isPending ? stage || "Adding…" : "Add"}
        </Button>
        <Button type="button" size="sm" variant="ghost" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
