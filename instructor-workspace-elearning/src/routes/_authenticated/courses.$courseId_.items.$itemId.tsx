import {
  memo,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from "react";
import { createFileRoute, Link, useBlocker } from "@tanstack/react-router";
import {
  ArrowLeft,
  Bold,
  ChevronDown,
  ChevronRight,
  Code,
  Download,
  FileUp,
  Heading2,
  Link2,
  List,
  Save,
  Table2,
  Upload,
} from "lucide-react";
import { toast } from "sonner";

import { useItemQuery, useLessonQuery, useSaveLessonMutation } from "@/hooks/queries";
import {
  curriculumApi,
  mediaApi,
  parseApiError,
  type LessonCompletionRule,
  type LessonContentFormat,
  type LessonResponse,
  type ResourceType,
  type SaveLessonRequest,
  type SourceType,
} from "@/api";
import { renderLessonBody } from "@/lib/render-lesson-body";
import { ResourcePanel } from "@/components/workspace/ResourcePanel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Progress } from "@/components/ui/progress";
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

const FORM_ID = "lesson-page-form";

/**
 * What the material is, and where it lives — two questions, two controls.
 *
 * They used to be one list, which is why it had entries that could not be
 * saved: "written" is a kind of thing and "external" is a place a thing lives,
 * and neither answer told the platform the other half. Audio lessons and links
 * work now because the question they needed answered is finally being asked.
 */
const RESOURCE_TYPES: ResourceType[] = [
  "DOCUMENT",
  "VIDEO",
  "AUDIO",
  "IMAGE",
  "SOURCE_CODE",
  "LINK",
  "OTHER",
];

const KIND: Record<string, string> = {
  DOCUMENT: "Document",
  VIDEO: "Video",
  AUDIO: "Audio",
  IMAGE: "Image",
  SOURCE_CODE: "Source code",
  LINK: "Link",
  OTHER: "Something else",
};

const SOURCE_TYPES: SourceType[] = ["INLINE", "FILE", "URL"];

const WHERE: Record<string, { label: string; hint: string }> = {
  INLINE: { label: "Written here", hint: "Typed below and stored as text." },
  FILE: {
    label: "A file you upload",
    hint: "Uploaded straight to storage; the bytes never pass through the API.",
  },
  URL: {
    label: "A link",
    hint: "Somewhere else entirely. The platform stores the reference, not the content.",
  },
};

const FORMATS: LessonContentFormat[] = ["MARKDOWN", "HTML", "PLAIN_TEXT"];

const FORMAT_LABEL: Record<string, string> = {
  MARKDOWN: "Markdown",
  HTML: "HTML",
  PLAIN_TEXT: "Plain text",
};

/** Narrows the file picker where the kind makes it obvious. */
function acceptFor(resourceType: ResourceType): string | undefined {
  if (resourceType === "VIDEO") return "video/*";
  if (resourceType === "AUDIO") return "audio/*";
  if (resourceType === "IMAGE") return "image/*";
  return undefined;
}

const COMPLETION_RULES: LessonCompletionRule[] = ["MANUAL", "VIEW", "DURATION"];

const COMPLETION: Record<string, { label: string; hint: string }> = {
  MANUAL: { label: "The student marks it done", hint: "They decide when they have finished." },
  VIEW: { label: "Opening it is enough", hint: "Counted as done the moment it is opened." },
  DURATION: {
    label: "Reaching the end",
    hint: "Claiming to have finished before the duration above is recorded as still in progress.",
  },
};

const WORDS_PER_MINUTE = 200;

// How long the word count and the dirty indicator are allowed to lag behind
// the keyboard. Nothing that must be correct — what gets submitted, the
// empty-body check, the unsaved-changes guard — reads this; those read the
// live ref instead.
const BODY_SNAPSHOT_DEBOUNCE_MS = 400;

/**
 * What a lesson body may be loaded from, and how much of it.
 *
 * The extensions are a hint to the file picker, not a rule — the browser lets
 * anyone pick anything, so the handler checks what it actually read. Two
 * megabytes of prose is about a novel; past that it is not a lesson.
 */
const MARKDOWN_ACCEPT = ".md,.markdown,.mdx,.txt,text/markdown,text/plain";
const MAX_MARKDOWN_BYTES = 2 * 1024 * 1024;

/**
 * Writing one lesson, with the whole page to do it in.
 *
 * This was a sheet over the curriculum, which meant the editor competed for
 * width with a list nobody was reading at the time. Writing a lesson is not a
 * quick edit you do beside something else, so it gets its own URL — which also
 * makes it linkable, refreshable, and reachable from the sidebar tree.
 *
 * The item lives under its course in the path rather than at `/items/{id}`, so
 * the sidebar stays in course context and "back" has somewhere to go.
 *
 * The underscore in the filename is what keeps it a page. Without it the
 * router makes this a child of `courses.$courseId`, which renders no
 * `<Outlet />` — so opening a lesson drew the course page instead, landing on
 * the overview. The URL is unchanged; only the nesting is.
 */
export const Route = createFileRoute("/_authenticated/courses/$courseId_/items/$itemId")({
  head: () => ({ meta: [{ title: "Lesson — Lernova for Instructors" }] }),
  component: LessonPage,
});

function LessonPage() {
  const { courseId, itemId } = Route.useParams();

  const item = useItemQuery(courseId, itemId);
  const lesson = useLessonQuery(courseId, itemId);

  /*
   * Both queries settle before the editor exists.
   *
   * The editor reads its text once, when it mounts. Rendering it while the
   * lesson was still arriving mounted it on an empty body, and the text that
   * turned up a moment later went into React state that the editor was no
   * longer listening to — so a lesson with content came up blank, and moving
   * between two lessons showed the previous one's writing.
   */
  if (item.isLoading || lesson.isLoading) {
    return (
      <div className="space-y-4 p-4 sm:p-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-[60vh] w-full rounded-xl" />
      </div>
    );
  }

  if (item.isError || !item.data) {
    return (
      <div className="p-4 sm:p-6">
        <div className="card-surface p-8 text-center">
          <h2 className="text-lg font-bold tracking-tight">Item not found</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            {parseApiError(item.error).message ||
              "It may have been deleted, or it may not be yours."}
          </p>
          <Button variant="outline" size="sm" className="mt-3" asChild>
            <Link to="/courses/$courseId" params={{ courseId }} search={{ tab: "curriculum" }}>
              Back to the curriculum
            </Link>
          </Button>
        </div>
      </div>
    );
  }

  // Only lessons have a body. A quiz or an assignment has its own editor,
  // which does not exist yet — say so rather than showing a lesson form that
  // would save the wrong thing.
  if (item.data.type !== "LESSON") {
    return (
      <div className="space-y-6 p-4 sm:p-6">
        <BackLink courseId={courseId} />
        <div className="card-surface p-8 text-center">
          <h2 className="text-lg font-bold tracking-tight">{item.data.title}</h2>
          <Badge variant="outline" className="mt-2 text-[10px] uppercase tracking-wider">
            {item.data.type}
          </Badge>
          <p className="mx-auto mt-3 max-w-md text-sm text-muted-foreground">
            {item.data.type === "QUIZ"
              ? "Quiz authoring is not built yet — questions and options have endpoints, but no screen."
              : "Assignment authoring is not built yet — the endpoints exist, but no screen does."}
          </p>
        </div>
      </div>
    );
  }

  return (
    <LessonEditor
      // A different lesson is a different editor, not the same one told to
      // forget: every field below is seeded from the lesson it was mounted
      // with, and a key is the honest way to say so.
      key={itemId}
      courseId={courseId}
      itemId={itemId}
      title={item.data.title ?? "Lesson"}
      lesson={lesson.data ?? null}
    />
  );
}

/**
 * The two shapes most of the toolbar's edits take, factored out so bold,
 * heading, inline code, and the list button do not each reimplement the same
 * substring arithmetic.
 *
 * `wrapSelection` covers bold and inline code, where the selection becomes
 * the inside of something — an empty selection falls back to `placeholder`
 * so the button still produces valid markdown to type over. `linePrefix`
 * covers headings and list items, where the unit that matters is the line,
 * not the character range, so it walks outward to the surrounding line
 * boundaries before prefixing every line the selection touches. Both return
 * the caret placed somewhere sensible to keep typing, not just the new
 * string.
 */
function wrapSelection(
  value: string,
  start: number,
  end: number,
  before: string,
  after: string,
  placeholder: string,
) {
  const selected = value.slice(start, end) || placeholder;
  const next = value.slice(0, start) + before + selected + after + value.slice(end);
  const selStart = start + before.length;
  return { next, selStart, selEnd: selStart + selected.length };
}

function linePrefix(value: string, start: number, end: number, prefix: string) {
  const lineStart = value.lastIndexOf("\n", start - 1) + 1;
  const lineEndIndex = value.indexOf("\n", end);
  const lineEnd = lineEndIndex === -1 ? value.length : lineEndIndex;
  const block = value.slice(lineStart, lineEnd);
  const prefixed = block
    .split("\n")
    .map((line) => prefix + line)
    .join("\n");
  const next = value.slice(0, lineStart) + prefixed + value.slice(lineEnd);
  return { next, selStart: lineStart, selEnd: lineStart + prefixed.length };
}

function insertSnippet(value: string, start: number, end: number, snippet: string) {
  const next = value.slice(0, start) + snippet + value.slice(end);
  const selStart = start + snippet.length;
  return { next, selStart, selEnd: selStart };
}

// A link is the one button that is neither a clean wrap nor a clean insert:
// the selection becomes the label, but the part worth landing the caret on
// is the URL, so the placeholder scheme the other buttons use does not fit.
function insertLink(value: string, start: number, end: number) {
  const label = value.slice(start, end) || "link text";
  const url = "https://";
  const before = value.slice(0, start);
  const next = `${before}[${label}](${url})${value.slice(end)}`;
  const urlStart = before.length + label.length + 3;
  return { next, selStart: urlStart, selEnd: urlStart + url.length };
}

const TABLE_SNIPPET = "\n| Header | Header |\n| --- | --- |\n| Cell | Cell |\n";

/**
 * The markdown syntax toolbar, shown above the textarea only for the
 * MARKDOWN format — HTML and plain text have no syntax for these buttons to
 * insert. Each button only describes the edit it wants (`onEdit` is
 * `BodyEditor`'s `handleToolbarEdit`, closed over its textarea ref); the
 * selection itself is read from the DOM node at click time rather than
 * tracked in React state, since it changes on every click and arrow key and
 * has no reason to live any higher than the element that already tracks it
 * for free.
 */
function MarkdownToolbar({
  onEdit,
}: {
  onEdit: (
    transform: (
      value: string,
      start: number,
      end: number,
    ) => {
      next: string;
      selStart: number;
      selEnd: number;
    },
  ) => void;
}) {
  return (
    <div className="flex items-center gap-0.5">
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="h-7 w-7 px-0"
        title="Bold"
        onClick={() => onEdit((v, s, e) => wrapSelection(v, s, e, "**", "**", "bold text"))}
      >
        <Bold className="h-3.5 w-3.5" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="h-7 w-7 px-0"
        title="Heading"
        onClick={() => onEdit((v, s, e) => linePrefix(v, s, e, "## "))}
      >
        <Heading2 className="h-3.5 w-3.5" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="h-7 w-7 px-0"
        title="Link"
        onClick={() => onEdit((v, s, e) => insertLink(v, s, e))}
      >
        <Link2 className="h-3.5 w-3.5" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="h-7 w-7 px-0"
        title="Inline code"
        onClick={() => onEdit((v, s, e) => wrapSelection(v, s, e, "`", "`", "code"))}
      >
        <Code className="h-3.5 w-3.5" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="h-7 w-7 px-0"
        title="Bulleted list"
        onClick={() => onEdit((v, s, e) => linePrefix(v, s, e, "- "))}
      >
        <List className="h-3.5 w-3.5" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="h-7 w-7 px-0"
        title="Table"
        onClick={() => onEdit((v, s, e) => insertSnippet(v, s, e, TABLE_SNIPPET))}
      >
        <Table2 className="h-3.5 w-3.5" />
      </Button>
    </div>
  );
}

/**
 * The one part of the page that changes on every keystroke.
 *
 * Everything else in `LessonEditor` — the format picker, the resource panel,
 * the word count — used to re-render on every character because the body
 * lived in that component's own state. It doesn't need to: nothing downstream
 * of a keystroke needs to react to it faster than the debounce in the parent,
 * so the live text is kept here instead, and `memo` means this is the only
 * thing that re-renders while someone types. `onTextChange` has to be a
 * stable callback for that to hold — a new closure every render would defeat
 * the memoisation as surely as not having it.
 *
 * There is exactly one copy of the text, held in this component's own state,
 * and two ways to look at it: Preview runs it through `renderLessonBody` and
 * Write shows the textarea it actually lives in. Toggling either way just
 * changes which of those is on screen — there is no longer a reason to make
 * that one-way, the way opening the old rich editor used to be, because a
 * single textarea can never disagree with itself about what was typed. A
 * lesson opens on Preview so that opening it shows the lesson rather than an
 * empty box; an empty body opens on Write instead, since there is nothing to
 * preview and the obvious next move is to start typing.
 */
const BodyEditor = memo(function BodyEditor({
  initialText,
  contentFormat,
  onTextChange,
}: {
  initialText: string;
  contentFormat: LessonContentFormat;
  onTextChange: (next: string) => void;
}) {
  const [text, setText] = useState(initialText);
  const [view, setView] = useState<"preview" | "write">(initialText.trim() ? "preview" : "write");
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  // Set by a toolbar button just before the new text lands in state, and
  // consumed by the layout effect below once the textarea's DOM value has
  // caught up — setting the selection any earlier would be applied to the
  // text that is about to be replaced.
  const pendingSelection = useRef<{ start: number; end: number } | null>(null);

  const handleChange = useCallback(
    (next: string) => {
      setText(next);
      onTextChange(next);
    },
    [onTextChange],
  );

  useLayoutEffect(() => {
    const pending = pendingSelection.current;
    if (!pending || !textareaRef.current) return;
    textareaRef.current.setSelectionRange(pending.start, pending.end);
    pendingSelection.current = null;
  }, [text]);

  const handleToolbarEdit = useCallback(
    (
      transform: (
        value: string,
        start: number,
        end: number,
      ) => { next: string; selStart: number; selEnd: number },
    ) => {
      const el = textareaRef.current;
      if (!el) return;
      const { next, selStart, selEnd } = transform(text, el.selectionStart, el.selectionEnd);
      pendingSelection.current = { start: selStart, end: selEnd };
      el.focus();
      handleChange(next);
    },
    [text, handleChange],
  );

  // Parsed only while Preview is the view on screen. Markdown costs
  // milliseconds where MDXEditor's ProseMirror pass cost seconds, so this is
  // no longer the thing that decides whether a body is too big to touch — but
  // a keystroke in Write still has no reason to build markup nobody is
  // looking at, and on a long lesson that is the difference someone would
  // feel under their fingers.
  const rendered = useMemo(
    () => (view === "preview" ? renderLessonBody(text, contentFormat) : null),
    [view, text, contentFormat],
  );

  return (
    <div className="space-y-2">
      <div className="inline-flex items-center rounded-md border p-0.5 text-xs">
        <button
          type="button"
          onClick={() => setView("preview")}
          className={cn(
            "rounded-sm px-2.5 py-1 font-medium transition-colors",
            view === "preview"
              ? "bg-secondary text-secondary-foreground"
              : "text-muted-foreground hover:text-foreground",
          )}
        >
          Preview
        </button>
        <button
          type="button"
          onClick={() => setView("write")}
          className={cn(
            "rounded-sm px-2.5 py-1 font-medium transition-colors",
            view === "write"
              ? "bg-secondary text-secondary-foreground"
              : "text-muted-foreground hover:text-foreground",
          )}
        >
          Write
        </button>
      </div>

      {view === "preview" ? (
        rendered && rendered.kind === "html" ? (
          <div
            className="prose prose-sm dark:prose-invert max-w-none min-h-[65vh] rounded-lg border bg-background p-4"
            dangerouslySetInnerHTML={{ __html: rendered.html }}
          />
        ) : (
          <pre className="min-h-[65vh] whitespace-pre-wrap rounded-lg border bg-background p-4 font-mono text-xs">
            {text}
          </pre>
        )
      ) : (
        <div className="space-y-1.5">
          {contentFormat === "MARKDOWN" && <MarkdownToolbar onEdit={handleToolbarEdit} />}
          <Textarea
            ref={textareaRef}
            value={text}
            onChange={(e) => handleChange(e.target.value)}
            className="min-h-[65vh] font-mono text-xs"
            spellCheck={contentFormat !== "HTML"}
          />
        </div>
      )}
    </div>
  );
});

/**
 * The attachments list at the foot of the page.
 *
 * It runs its own queries and holds its own local state and has nothing to do
 * with the lesson body — `itemId` is the only prop it needs, and that never
 * changes while someone is typing. `memo` makes that fact pay off: without
 * it, this would re-render — and re-run its own effects — on every keystroke
 * upstream, for no reason connected to anything it shows.
 */
const LessonResources = memo(function LessonResources({ itemId }: { itemId: string }) {
  return (
    <div className="border-t p-4 sm:p-6">
      <ResourcePanel
        scope="item"
        ownerId={itemId}
        emptyHint="Nothing attached to this lesson yet. Slides, a worksheet, a link to read first."
      />
    </div>
  );
});

/**
 * The lesson itself, mounted once the lesson it edits is known.
 *
 * `lesson` is null for an item nobody has written yet, which is the ordinary
 * state of a course being built rather than a failure. It stays live after
 * that: it is what the page compares against to know whether anything here is
 * unsaved.
 */
function LessonEditor({
  courseId,
  itemId,
  title,
  lesson,
}: {
  courseId: string;
  itemId: string;
  title: string;
  lesson: LessonResponse | null;
}) {
  const save = useSaveLessonMutation(courseId);

  const [resourceType, setResourceType] = useState<ResourceType>(
    (lesson?.resourceType as ResourceType | undefined) ?? "DOCUMENT",
  );
  const [sourceType, setSourceType] = useState<SourceType>(
    (lesson?.sourceType as SourceType | undefined) ?? "INLINE",
  );
  const [contentFormat, setContentFormat] = useState<LessonContentFormat>(
    (lesson?.contentFormat as LessonContentFormat | undefined) ?? "MARKDOWN",
  );
  const [url, setUrl] = useState(lesson?.url ?? "");
  const [description, setDescription] = useState(lesson?.description ?? "");
  // The body itself lives outside React state entirely. `bodyRef` is always
  // the current text — everything that must be correct right now (the submit
  // handler, the empty-body check, the "replace what's written here" confirm)
  // reads it. `bodySnapshot` is a debounced copy for the things that only
  // ever display a derived value — the word count, the dirty label — and
  // `dirtyRef` mirrors "has this changed since it was saved" synchronously,
  // for the unsaved-changes guard, which cannot afford the same lag: a
  // navigation half a debounce-interval after a keystroke must still block.
  const bodyRef = useRef(lesson?.content ?? "");
  const dirtyRef = useRef(false);
  const [bodySnapshot, setBodySnapshot] = useState(lesson?.content ?? "");
  const bodyDebounce = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [minutes, setMinutes] = useState(
    lesson?.durationSeconds ? String(Math.floor(lesson.durationSeconds / 60)) : "",
  );
  const [seconds, setSeconds] = useState(
    lesson?.durationSeconds ? String(lesson.durationSeconds % 60) : "",
  );
  const [completionRule, setCompletionRule] = useState<LessonCompletionRule>(
    (lesson?.completionRule as LessonCompletionRule | undefined) ?? "MANUAL",
  );
  const [file, setFile] = useState<File | null>(null);
  const [stage, setStage] = useState("");
  // Null except while bytes are actually moving. A video is the one thing here
  // big enough for "Uploading…" on its own to look like the page has hung.
  const [uploadPercent, setUploadPercent] = useState<number | null>(null);
  const [error, setError] = useState("");

  // Bumped to re-seed the editor from `body` — it reads its text at mount and
  // never again, so replacing what is in it means giving it a new identity.
  const [bodySeed, setBodySeed] = useState(0);
  const markdownInput = useRef<HTMLInputElement | null>(null);

  // Whether the body editor and its format row are folded out of the way.
  // This has to stay a visual toggle rather than a decision about what to
  // render: `BodyEditor` keeps the live text, the Preview/Write choice, the
  // caret and the textarea's scroll offset in state that only survives while
  // it is mounted, and unmounting it on fold would throw all of that away
  // mid-edit and hand back an empty rewind to `bodyRef` on unfold. So folding
  // only puts `hidden` on the wrapper below — the editor keeps running
  // underneath, same as when it's showing. Starts expanded; a body's length
  // is not a reason to decide this for someone, that's the threshold this
  // file just stopped doing.
  const [bodyCollapsed, setBodyCollapsed] = useState(false);

  // Fires on every keystroke, so it must not allocate anything that scales
  // with document size and must not itself change identity — it is a prop of
  // the memoised `BodyEditor`, and a fresh closure every render would defeat
  // that memoisation. `dirtyRef` is set before the debounce timer is even
  // started, so the unsaved-changes guard sees a change immediately; the
  // snapshot that drives the visible word count and dirty label can lag.
  const onTextChange = useCallback((next: string) => {
    bodyRef.current = next;
    dirtyRef.current = true;
    if (bodyDebounce.current) clearTimeout(bodyDebounce.current);
    bodyDebounce.current = setTimeout(() => {
      setBodySnapshot(next);
    }, BODY_SNAPSHOT_DEBOUNCE_MS);
  }, []);

  useEffect(() => {
    return () => {
      if (bodyDebounce.current) clearTimeout(bodyDebounce.current);
    };
  }, []);

  // Rebuilt only when the lesson itself changes, not on every render — it was
  // a fresh object literal on every keystroke before the body moved out of
  // this component's state, which made `isDirty` below allocate three times
  // over just to compare against it.
  const saved = useMemo(
    () => ({
      resourceType: (lesson?.resourceType as ResourceType | undefined) ?? "DOCUMENT",
      sourceType: (lesson?.sourceType as SourceType | undefined) ?? "INLINE",
      contentFormat: (lesson?.contentFormat as LessonContentFormat | undefined) ?? "MARKDOWN",
      url: lesson?.url ?? "",
      description: lesson?.description ?? "",
      content: lesson?.content ?? "",
      durationSeconds: lesson?.durationSeconds ?? null,
      completionRule: (lesson?.completionRule as LessonCompletionRule | undefined) ?? "MANUAL",
    }),
    [lesson],
  );

  const hasFileAlready = Boolean(lesson?.hasFile) && lesson?.sourceType === sourceType;
  const busy = save.isPending || Boolean(stage);

  const durationSeconds = (() => {
    const total = Math.round(Number(minutes || 0) * 60 + Number(seconds || 0));
    return Number.isFinite(total) && total > 0 ? total : null;
  })();

  // The displayed "Unsaved changes" label — it reads the debounced snapshot,
  // so it can lag a keystroke behind by design. The navigation guard below
  // does not use this alone; see `shouldBlockFn`.
  const isDirty = useMemo(
    () =>
      resourceType !== saved.resourceType ||
      sourceType !== saved.sourceType ||
      (sourceType === "INLINE" &&
        (bodySnapshot !== saved.content || contentFormat !== saved.contentFormat)) ||
      (sourceType === "URL" && url !== saved.url) ||
      description !== saved.description ||
      durationSeconds !== saved.durationSeconds ||
      completionRule !== saved.completionRule ||
      file !== null,
    [
      resourceType,
      sourceType,
      bodySnapshot,
      contentFormat,
      url,
      description,
      durationSeconds,
      completionRule,
      file,
      saved,
    ],
  );

  /**
   * Leaving with unsaved work asks first.
   *
   * The sidebar is one click from every other lesson in the course, so the way
   * to lose an afternoon's writing is an ordinary navigation rather than
   * anything careless. `enableBeforeUnload` covers closing the tab, which the
   * router cannot intercept.
   *
   * `isDirty` alone is not enough here: it reads the debounced body snapshot,
   * so a keystroke followed by an immediate navigation could land in the
   * window before the snapshot catches up. `dirtyRef` is set synchronously on
   * every keystroke and never lags, so the guard ORs it in.
   */
  const blocker = useBlocker({
    shouldBlockFn: () => isDirty || dirtyRef.current,
    enableBeforeUnload: () => isDirty || dirtyRef.current,
    withResolver: true,
  });

  /**
   * Loading a body from a markdown file on disk.
   *
   * For prose written somewhere else, which is most of it — an existing README,
   * notes kept in a repository, a draft from another editor. What is stored is
   * markdown either way, so the file goes in as it is and the editor renders
   * it; nothing is converted and nothing is lost.
   */
  const loadMarkdownFile = async (chosen: File | null) => {
    if (!chosen) return;

    if (chosen.size > MAX_MARKDOWN_BYTES) {
      toast.error("That file is bigger than 2 MB. It is probably not a lesson.");
      return;
    }
    if (
      bodyRef.current.trim() &&
      !window.confirm(`Replace what is written here with ${chosen.name}?`)
    ) {
      return;
    }

    try {
      const text = await chosen.text();
      // The picker's accept list is a suggestion the browser does not enforce,
      // so this is where a PDF gets turned away rather than pasted in as
      // mojibake.
      if (text.includes("\u0000")) {
        toast.error("That does not look like a text file.");
        return;
      }
      // An import is a change worth showing immediately, not on the usual
      // debounce — nothing about "you just replaced the whole body" should
      // wait 400ms to be reflected in the word count.
      if (bodyDebounce.current) clearTimeout(bodyDebounce.current);
      bodyRef.current = text;
      dirtyRef.current = true;
      setBodySnapshot(text);
      setBodySeed((seed) => seed + 1);
      setError("");
      toast.success(`Loaded ${chosen.name}. Nothing is saved until you save the lesson.`);
    } catch {
      toast.error("That file could not be read.");
    }
  };

  // Keyed on the debounced snapshot, not the live ref — this is the one place
  // in the render path that used to run `.trim()` and `.split(/\s+/)` on the
  // whole body on every keystroke, which is what made a long lesson freeze
  // the page while typing.
  const words = useMemo(() => {
    const trimmed = bodySnapshot.trim();
    return trimmed ? trimmed.split(/\s+/).length : 0;
  }, [bodySnapshot]);
  const readingMinutes = useMemo(() => Math.max(1, Math.round(words / WORDS_PER_MINUTE)), [words]);

  const openCurrentFile = async () => {
    try {
      const signed = await curriculumApi.contentUrl(itemId);
      window.open(signed, "_blank", "noopener,noreferrer");
    } catch (err) {
      toast.error(parseApiError(err).message || "Could not open that file.");
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");

    if (sourceType === "INLINE" && !bodyRef.current.trim()) {
      setError("A written lesson needs a body. The server refuses an empty one.");
      return;
    }
    if (sourceType === "URL" && !/^https?:\/\//i.test(url.trim())) {
      setError("A link lesson needs an http or https address.");
      return;
    }
    if (sourceType === "FILE" && !file && !hasFileAlready) {
      setError("Choose a file to upload.");
      return;
    }

    try {
      let mediaId: string | undefined;
      if (file) {
        setStage("Uploading…");
        const media = await mediaApi.upload(file, {
          onProgress: ({ stage: phase, loaded, total }) => {
            setStage(phase);
            setUploadPercent(total > 0 ? Math.round((loaded / total) * 100) : null);
          },
        });
        mediaId = media.id ?? undefined;
      }

      const payload: SaveLessonRequest & { itemId: string } = {
        itemId,
        // The material is named after the item it teaches. It is a library
        // resource like any other and could be named separately, but a second
        // name for the same thing is a second thing to keep in step.
        title,
        resourceType,
        sourceType,
        completionRule,
        description: description.trim() || null,
        durationSeconds,
        // The whole lesson is replaced on every save, so a field left out is a
        // field cleared. The file is the exception: omitting it keeps the one
        // already attached, which is the only way to edit a video lesson at all
        // — its media id is never given back to us to re-send.
        ...(sourceType === "INLINE" ? { content: bodyRef.current.trim(), contentFormat } : {}),
        ...(sourceType === "URL" ? { url: url.trim() } : {}),
        ...(mediaId ? { mediaId } : {}),
      };

      const result = await save.mutateAsync(payload);
      setStage("");
      setUploadPercent(null);
      setFile(null);
      // Take the server's version of what was stored, so anything it trimmed or
      // defaulted does not leave the page looking unsaved.
      setDescription(result.description ?? "");
      if (bodyDebounce.current) clearTimeout(bodyDebounce.current);
      const savedContent = result.content ?? "";
      bodyRef.current = savedContent;
      setBodySnapshot(savedContent);
      dirtyRef.current = false;
      setUrl(result.url ?? "");
      setCompletionRule((result.completionRule as LessonCompletionRule | undefined) ?? "MANUAL");
      toast.success("Lesson saved.");
    } catch (err) {
      setStage("");
      setUploadPercent(null);
      setError(parseApiError(err).message || "Could not save that lesson.");
    }
  };

  return (
    <div className="flex min-h-full flex-col">
      {/* Sticky, because the editor is taller than the viewport and Save should
          not be something you scroll back up to find. */}
      <header className="sticky top-0 z-20 border-b bg-background/85 px-4 py-3 backdrop-blur sm:px-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <BackLink courseId={courseId} />
            <h1 className="mt-1 truncate text-lg font-bold tracking-tight">{title}</h1>
          </div>
          <div className="flex items-center gap-3">
            <span
              className={cn(
                "text-xs",
                isDirty
                  ? "font-medium text-amber-600 dark:text-amber-500"
                  : "text-muted-foreground",
              )}
            >
              {isDirty ? "Unsaved changes" : lesson ? "Everything saved" : "Not written yet"}
            </span>
            <Button type="submit" form={FORM_ID} size="sm" disabled={busy || !isDirty}>
              <Save className="h-4 w-4" />
              {busy ? stage || "Saving…" : "Save lesson"}
            </Button>
          </div>
        </div>
      </header>

      <form id={FORM_ID} onSubmit={submit} className="flex-1 space-y-5 p-4 sm:p-6">
        <section className="card-surface space-y-4 p-4 sm:p-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>What it is</Label>
              <Select
                value={resourceType}
                onValueChange={(value) => setResourceType(value as ResourceType)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RESOURCE_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {KIND[type]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                How it is listed and what a student expects to open.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label>Where it lives</Label>
              <Select
                value={sourceType}
                onValueChange={(value) => {
                  setSourceType(value as SourceType);
                  setFile(null);
                }}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SOURCE_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {WHERE[type]?.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">{WHERE[sourceType]?.hint}</p>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="l-minutes">How long it takes</Label>
              <div className="flex items-center gap-1.5">
                <Input
                  id="l-minutes"
                  type="number"
                  min={0}
                  value={minutes}
                  onChange={(e) => setMinutes(e.target.value)}
                  className="w-20"
                  placeholder="0"
                />
                <span className="text-xs text-muted-foreground">min</span>
                <Input
                  id="l-seconds"
                  type="number"
                  min={0}
                  max={59}
                  value={seconds}
                  onChange={(e) => setSeconds(e.target.value)}
                  className="w-20"
                  placeholder="0"
                />
                <span className="text-xs text-muted-foreground">sec</span>
              </div>
              <p className="text-xs text-muted-foreground">
                Optional. Shown to students before they start.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label>Counts as done when</Label>
              <Select
                value={completionRule}
                onValueChange={(value) => setCompletionRule(value as LessonCompletionRule)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {COMPLETION_RULES.map((rule) => (
                    <SelectItem key={rule} value={rule}>
                      {COMPLETION[rule]?.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                {COMPLETION[completionRule]?.hint}
                {completionRule === "DURATION" && !durationSeconds
                  ? " With no duration set there is nothing to reach, so the student is taken at their word."
                  : ""}
              </p>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="l-description">Description</Label>
            <Textarea
              id="l-description"
              name="description"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              A line about the lesson, read before it is opened.
            </p>
          </div>
        </section>

        {sourceType === "INLINE" && (
          <section className="space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="h-6 w-6 px-0"
                  aria-expanded={!bodyCollapsed}
                  aria-controls="l-body-fields"
                  aria-label={bodyCollapsed ? "Expand body" : "Collapse body"}
                  onClick={() => setBodyCollapsed((collapsed) => !collapsed)}
                >
                  {bodyCollapsed ? (
                    <ChevronRight className="h-3 w-3" />
                  ) : (
                    <ChevronDown className="h-3 w-3" />
                  )}
                </Button>
                <Label>Body</Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="h-6 px-2 text-xs"
                  onClick={() => markdownInput.current?.click()}
                >
                  <Upload className="h-3 w-3" />
                  Load from a file
                </Button>
                <input
                  ref={markdownInput}
                  type="file"
                  accept={MARKDOWN_ACCEPT}
                  className="hidden"
                  onChange={(e) => {
                    const chosen = e.target.files?.[0] ?? null;
                    // Cleared so that picking the same file twice in a row
                    // still counts as a change.
                    e.target.value = "";
                    void loadMarkdownFile(chosen);
                  }}
                />
              </div>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span>
                  {words.toLocaleString()} {words === 1 ? "word" : "words"}
                </span>
                {words > 0 && (
                  <>
                    <span aria-hidden>·</span>
                    <span>about {readingMinutes} min to read</span>
                    {durationSeconds !== readingMinutes * 60 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-6 px-2 text-xs"
                        onClick={() => {
                          setMinutes(String(readingMinutes));
                          setSeconds("");
                        }}
                      >
                        Use as the duration
                      </Button>
                    )}
                  </>
                )}
              </div>
            </div>
            <div id="l-body-fields" className={cn("space-y-2", bodyCollapsed && "hidden")}>
              <BodyEditor
                key={`${itemId}:${bodySeed}`}
                initialText={bodyRef.current}
                contentFormat={contentFormat}
                onTextChange={onTextChange}
              />
              <div className="flex flex-wrap items-center gap-3">
                <div className="flex items-center gap-2">
                  <Label htmlFor="l-format" className="text-xs text-muted-foreground">
                    Stored as
                  </Label>
                  <Select
                    value={contentFormat}
                    onValueChange={(value) => setContentFormat(value as LessonContentFormat)}
                  >
                    <SelectTrigger id="l-format" className="h-7 w-36 text-xs">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {FORMATS.map((format) => (
                        <SelectItem key={format} value={format}>
                          {FORMAT_LABEL[format]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                {/* The format is recorded with the text now. It used to be
                    markdown by the editor's habit and nothing else, which left a
                    renderer free to guess wrong. */}
                <p className="flex-1 text-xs text-muted-foreground">
                  Written down with the text, so whatever displays this later does not have to
                  guess.
                </p>
              </div>
            </div>
          </section>
        )}

        {sourceType === "URL" && (
          <section className="space-y-1.5">
            <Label htmlFor="l-url">Where it is</Label>
            <Input
              id="l-url"
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://…"
            />
            <p className="text-xs text-muted-foreground">
              http and https only. Anything a browser would execute is refused.
            </p>
          </section>
        )}

        {sourceType === "FILE" && (
          <section className="space-y-2">
            <Label htmlFor="l-file">{KIND[resourceType]} file</Label>

            {hasFileAlready && (
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-muted/30 p-3">
                <div className="min-w-0 text-xs">
                  <p className="font-medium">A file is already attached.</p>
                  <p className="mt-0.5 text-muted-foreground">
                    Everything else here can be changed without touching it. Choose another file
                    only to replace it.
                  </p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => void openCurrentFile()}
                >
                  <Download className="h-3.5 w-3.5" />
                  Open the current file
                </Button>
              </div>
            )}

            <div className="rounded-xl border border-dashed p-8 text-center">
              <FileUp className="mx-auto h-6 w-6 text-muted-foreground" />
              <Input
                id="l-file"
                type="file"
                accept={acceptFor(resourceType)}
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="mx-auto mt-3 max-w-sm"
              />
              {file ? (
                <p className="mt-3 text-xs text-muted-foreground">
                  {file.name} · {(file.size / (1024 * 1024)).toFixed(1)} MB
                </p>
              ) : (
                <p className="mt-3 text-xs text-muted-foreground">
                  Uploaded straight to storage — the bytes never pass through the API.
                </p>
              )}
              {resourceType === "VIDEO" && (
                <p className="mt-1.5 text-xs text-muted-foreground">
                  A new file queues an encode, once per file rather than once per lesson. The
                  original plays until it finishes.
                </p>
              )}
            </div>
          </section>
        )}

        {stage && !error && (
          <div className="space-y-2 rounded-lg border bg-secondary/40 p-2.5">
            <p className="flex items-center justify-between gap-3 text-xs text-muted-foreground">
              <span>{stage}</span>
              {uploadPercent !== null && <span className="tabular-nums">{uploadPercent}%</span>}
            </p>
            {/* Only while there are bytes to measure. A determinate bar sitting
                at zero through a step that has no byte count reads as stuck. */}
            {uploadPercent !== null && <Progress value={uploadPercent} className="h-1" />}
          </div>
        )}
        {error && <p className="text-sm text-destructive">{error}</p>}
      </form>

      {/* Outside the form: a nested <form> is invalid, and attaching a document
          is its own action rather than part of saving the lesson. */}
      <LessonResources itemId={itemId} />

      <AlertDialog open={blocker.status === "blocked"}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Leave without saving?</AlertDialogTitle>
            <AlertDialogDescription>
              This lesson has changes that have not been saved. Leaving now loses them.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => blocker.reset?.()}>Stay here</AlertDialogCancel>
            <AlertDialogAction onClick={() => blocker.proceed?.()}>Leave anyway</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function BackLink({ courseId }: { courseId: string }) {
  return (
    <Link
      to="/courses/$courseId"
      params={{ courseId }}
      search={{ tab: "curriculum" }}
      className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground"
    >
      <ArrowLeft className="h-3.5 w-3.5" />
      Curriculum
    </Link>
  );
}
