import { useState, type FormEvent } from "react";
import {
  FileText,
  Link2,
  Upload,
  NotebookPen,
  Paperclip,
  Trash2,
  ExternalLink,
  Download,
} from "lucide-react";
import { toast } from "sonner";

import {
  useResourcesQuery,
  useAddResourceMutation,
  useDetachCourseResourceMutation,
} from "@/hooks/queries";
import {
  parseApiError,
  resourcesApi,
  type AttachedResourceResponse,
  type CreateResourceRequest,
  type RelationshipType,
  type ResourceScope,
  type ResourceType,
  type SourceType,
} from "@/api";
import { FloatingDetailSheet } from "@/components/workspace/FloatingDetailSheet";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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

const RESOURCE_TYPES: ResourceType[] = [
  "DOCUMENT",
  "SOURCE_CODE",
  "VIDEO",
  "AUDIO",
  "IMAGE",
  "LINK",
  "OTHER",
];

const RELATIONSHIPS: RelationshipType[] = [
  "RESOURCE",
  "READING",
  "REFERENCE",
  "SUPPLEMENTARY",
  "REQUIRED",
  "EXAMPLE",
  "SOLUTION",
  "ATTACHMENT",
];

const FORM_ID = "add-resource-form";

/** What the reader is looking at, before they click anything. */
function sourceIcon(source: string | undefined) {
  if (source === "URL") return Link2;
  if (source === "INLINE") return NotebookPen;
  return FileText;
}

/**
 * Documents, links and notes attached to one course, section or item.
 *
 * The same component at all three scopes, because a resource does not know
 * which it is hanging off — attachment is a separate row, and the same file can
 * be attached in several places at once without being copied.
 */
export function ResourcePanel({
  scope,
  ownerId,
  /** Detaching exists at course scope only; the API has no delete for the others. */
  canDetach = false,
  emptyHint,
}: {
  scope: ResourceScope;
  ownerId: string;
  canDetach?: boolean;
  emptyHint?: string;
}) {
  const { data, isLoading, isError, error, refetch } = useResourcesQuery(scope, ownerId);
  const detach = useDetachCourseResourceMutation(ownerId);
  const [adding, setAdding] = useState(false);

  const rows = data ?? [];

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 text-sm font-semibold">
          <Paperclip className="h-4 w-4" />
          Documents &amp; links
          {rows.length > 0 && (
            <span className="text-xs font-normal text-muted-foreground">({rows.length})</span>
          )}
        </h3>
        <Button size="sm" variant="outline" onClick={() => setAdding(true)}>
          <Upload className="h-3.5 w-3.5" />
          Add
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-12 w-full rounded-lg" />
          <Skeleton className="h-12 w-full rounded-lg" />
        </div>
      ) : isError ? (
        <div className="rounded-lg border border-dashed p-4 text-center">
          <p className="text-sm text-muted-foreground">
            {parseApiError(error).message || "Could not load these."}
          </p>
          <Button variant="outline" size="sm" className="mt-2" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : rows.length === 0 ? (
        <p className="rounded-lg border border-dashed p-4 text-center text-xs text-muted-foreground">
          {emptyHint ?? "Nothing attached yet. Upload a file, paste a link, or write a note."}
        </p>
      ) : (
        <ul className="space-y-1.5">
          {rows.map((row) => (
            <ResourceRow
              key={row.resource?.id}
              row={row}
              canDetach={canDetach}
              busy={detach.isPending}
              onDetach={() =>
                detach.mutate(row.resource!.id!, {
                  onSuccess: () => toast.success("Detached."),
                  onError: (err) =>
                    toast.error(parseApiError(err).message || "Could not detach that."),
                })
              }
            />
          ))}
        </ul>
      )}

      <AddResourceSheet
        scope={scope}
        ownerId={ownerId}
        open={adding}
        onClose={() => setAdding(false)}
      />
    </div>
  );
}

function ResourceRow({
  row,
  canDetach,
  busy,
  onDetach,
}: {
  row: AttachedResourceResponse;
  canDetach: boolean;
  busy: boolean;
  onDetach: () => void;
}) {
  const resource = row.resource;
  if (!resource) return null;
  const Icon = sourceIcon(resource.sourceType);
  const isLink = resource.sourceType === "URL";
  const isFile = resource.sourceType === "FILE";

  // A private file lives behind a signed URL that expires, so it is fetched when
  // asked for rather than rendered into the page and left to go stale.
  const openFile = async () => {
    try {
      const url = await resourcesApi.downloadUrl(resource.id!);
      if (url) window.open(url, "_blank", "noopener,noreferrer");
    } catch (err) {
      toast.error(parseApiError(err).message || "Could not open that file.");
    }
  };

  return (
    <li className="flex items-start gap-2.5 rounded-lg border p-2.5">
      <span
        aria-hidden
        className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-md bg-secondary text-muted-foreground"
      >
        <Icon className="h-3.5 w-3.5" />
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-1.5">
          <p className="truncate text-sm font-medium">{resource.title}</p>
          <Badge variant="outline" className="h-4 px-1 text-[9px] uppercase tracking-wider">
            {row.relationshipType}
          </Badge>
        </div>
        {resource.description && (
          <p className="mt-0.5 line-clamp-2 text-xs text-muted-foreground">
            {resource.description}
          </p>
        )}
        {isLink && resource.url && (
          <p className="mt-0.5 truncate text-[11px] text-muted-foreground">{resource.url}</p>
        )}
        {isFile && resource.filename && (
          <p className="mt-0.5 truncate text-[11px] text-muted-foreground">{resource.filename}</p>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-1">
        {isLink && resource.url && (
          <Button variant="ghost" size="sm" asChild>
            <a href={resource.url} target="_blank" rel="noopener noreferrer" title="Open link">
              <ExternalLink className="h-3.5 w-3.5" />
            </a>
          </Button>
        )}
        {isFile && (
          <Button variant="ghost" size="sm" onClick={() => void openFile()} title="Download">
            <Download className="h-3.5 w-3.5" />
          </Button>
        )}
        {canDetach && (
          <Button
            variant="ghost"
            size="sm"
            disabled={busy}
            onClick={onDetach}
            title="Detach. The resource itself is kept — it may be attached elsewhere."
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        )}
      </div>
    </li>
  );
}

type Source = Extract<SourceType, "FILE" | "URL" | "INLINE">;

function AddResourceSheet({
  scope,
  ownerId,
  open,
  onClose,
}: {
  scope: ResourceScope;
  ownerId: string;
  open: boolean;
  onClose: () => void;
}) {
  const add = useAddResourceMutation(scope, ownerId);
  const [source, setSource] = useState<Source>("URL");
  const [resourceType, setResourceType] = useState<ResourceType>("LINK");
  const [relationship, setRelationship] = useState<RelationshipType>("RESOURCE");
  const [file, setFile] = useState<File | null>(null);
  const [stage, setStage] = useState("");
  const [error, setError] = useState("");

  const reset = () => {
    setSource("URL");
    setResourceType("LINK");
    setRelationship("RESOURCE");
    setFile(null);
    setStage("");
    setError("");
  };

  const close = () => {
    reset();
    onClose();
  };

  // Choosing a source implies the usual kind, without locking it: a PDF and a
  // link to a PDF are both DOCUMENT, held two different ways.
  const chooseSource = (next: Source) => {
    setSource(next);
    setResourceType(next === "URL" ? "LINK" : "DOCUMENT");
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const form = new FormData(e.target as HTMLFormElement);
    const title = String(form.get("title") ?? "").trim();
    const description = String(form.get("description") ?? "").trim();
    const url = String(form.get("url") ?? "").trim();
    const content = String(form.get("content") ?? "").trim();

    if (source === "FILE" && !file) {
      setError("Choose a file to upload.");
      return;
    }
    if (source === "URL" && !url) {
      setError("Paste the link.");
      return;
    }
    if (source === "INLINE" && !content) {
      setError("Write something, or pick a different kind.");
      return;
    }

    const body: CreateResourceRequest & {
      file?: File;
      onProgress?: (s: string) => void;
      relationshipType?: RelationshipType;
    } = {
      title,
      resourceType,
      sourceType: source,
      relationshipType: relationship,
      ...(description ? { description } : {}),
      ...(source === "URL" ? { url } : {}),
      ...(source === "INLINE" ? { content, contentType: "MARKDOWN" as const } : {}),
      ...(source === "FILE" && file ? { file, onProgress: setStage } : {}),
    };

    add.mutate(body, {
      onSuccess: () => {
        toast.success(`"${title}" attached.`);
        close();
      },
      onError: (err) => {
        setStage("");
        setError(parseApiError(err).message || "Could not attach that.");
      },
    });
  };

  return (
    <FloatingDetailSheet
      open={open}
      onOpenChange={(next) => !next && close()}
      title="Attach a document or link"
      description="Created once and attached here. The same resource can hang off a course, a section and a lesson at the same time."
      footerActions={
        <>
          <Button type="button" variant="outline" onClick={close}>
            Cancel
          </Button>
          <Button type="submit" form={FORM_ID} disabled={add.isPending}>
            {add.isPending ? stage || "Attaching…" : "Attach"}
          </Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={submit} className="space-y-4">
        <div className="space-y-1.5">
          <Label>Kind</Label>
          <div className="grid grid-cols-3 gap-2">
            {(
              [
                ["URL", "Link", Link2],
                ["FILE", "File", Upload],
                ["INLINE", "Note", NotebookPen],
              ] as const
            ).map(([value, label, Icon]) => (
              <button
                key={value}
                type="button"
                onClick={() => chooseSource(value)}
                className={
                  source === value
                    ? "flex flex-col items-center gap-1 rounded-lg border-2 border-primary bg-primary/5 p-2.5 text-xs font-semibold"
                    : "flex flex-col items-center gap-1 rounded-lg border p-2.5 text-xs font-medium text-muted-foreground hover:bg-secondary"
                }
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="r-title">Title</Label>
          <Input id="r-title" name="title" required minLength={1} autoFocus />
        </div>

        {source === "URL" && (
          <div className="space-y-1.5">
            <Label htmlFor="r-url">Link</Label>
            <Input id="r-url" name="url" type="url" placeholder="https://…" />
            <p className="text-xs text-muted-foreground">
              Must start with http:// or https://. Nothing checks that it resolves.
            </p>
          </div>
        )}

        {source === "FILE" && (
          <div className="space-y-1.5">
            <Label htmlFor="r-file">File</Label>
            <Input id="r-file" type="file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            {file && (
              <p className="text-xs text-muted-foreground">
                {file.name} · {(file.size / 1024).toFixed(0)} KB
              </p>
            )}
          </div>
        )}

        {source === "INLINE" && (
          <div className="space-y-1.5">
            <Label htmlFor="r-content">Note</Label>
            <Textarea id="r-content" name="content" rows={6} placeholder="Markdown is fine." />
          </div>
        )}

        <div className="space-y-1.5">
          <Label htmlFor="r-description">Description</Label>
          <Textarea id="r-description" name="description" rows={2} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label>Type</Label>
            <Select value={resourceType} onValueChange={(v) => setResourceType(v as ResourceType)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {RESOURCE_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>
                    {t.replace("_", " ").toLowerCase()}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>Role</Label>
            <Select
              value={relationship}
              onValueChange={(v) => setRelationship(v as RelationshipType)}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {RELATIONSHIPS.map((t) => (
                  <SelectItem key={t} value={t}>
                    {t.toLowerCase()}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        {stage && !error && <p className="text-xs text-muted-foreground">{stage}</p>}
        {error && <p className="text-sm text-destructive">{error}</p>}
      </form>
    </FloatingDetailSheet>
  );
}
