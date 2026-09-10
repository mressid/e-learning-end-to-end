import { memo, useCallback, useLayoutEffect, useMemo, useRef, useState } from "react";
import { Bold, Code, Heading2, Link2, List, Table2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { renderLessonBody } from "@/lib/render-lesson-body";
import { cn } from "@/lib/utils";
import type { LessonContentFormat } from "@/api";

/**
 * Writing a block of a lesson, and seeing what it will look like.
 *
 * Lifted out of the lesson route when a lesson stopped being one body and
 * became a list of blocks: every text block wants this, and the route is no
 * longer the only thing that has one. Nothing about it changed in the move.
 */

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
export const BodyEditor = memo(function BodyEditor({
  initialText,
  contentFormat,
  onTextChange,
  minHeight = "min-h-[65vh]",
}: {
  initialText: string;
  contentFormat: LessonContentFormat;
  onTextChange: (next: string) => void;
  /**
   * How tall the writing area stands. A lesson that is nothing but writing
   * wants most of the viewport; one block among several wants a fraction of
   * it, or the page becomes a column of near-empty boxes to scroll past.
   */
  minHeight?: string | undefined;
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
            className={cn(
              "prose prose-sm dark:prose-invert max-w-none rounded-lg border bg-background p-4",
              minHeight,
            )}
            dangerouslySetInnerHTML={{ __html: rendered.html }}
          />
        ) : (
          <pre
            className={cn(
              "whitespace-pre-wrap rounded-lg border bg-background p-4 font-mono text-xs",
              minHeight,
            )}
          >
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
            className={cn("font-mono text-xs", minHeight)}
            spellCheck={contentFormat !== "HTML"}
          />
        </div>
      )}
    </div>
  );
});
