import { Suspense, lazy, useEffect, useRef, useState } from "react";
import type { MDXEditorMethods } from "@mdxeditor/editor";

import { Skeleton } from "@/components/ui/skeleton";
import { useTheme } from "@/lib/theme";
import { cn } from "@/lib/utils";

/**
 * Loaded on demand, never at startup.
 *
 * The editor and its ProseMirror/CodeMirror dependencies are around a megabyte
 * — more than the rest of this app put together — and most visits never open a
 * lesson body. Lazy so that cost is paid by the person who actually writes.
 *
 * It also touches the DOM on construction, so it must not be part of a server
 * render. This whole subtree is `ssr: false` already; the lazy boundary makes
 * that independent of that setting rather than dependent on it.
 */
const Inner = lazy(async () => {
  const mod = await import("@mdxeditor/editor");
  await import("@mdxeditor/editor/style.css");

  const {
    MDXEditor,
    headingsPlugin,
    listsPlugin,
    quotePlugin,
    thematicBreakPlugin,
    linkPlugin,
    linkDialogPlugin,
    imagePlugin,
    tablePlugin,
    codeBlockPlugin,
    codeMirrorPlugin,
    markdownShortcutPlugin,
    diffSourcePlugin,
    toolbarPlugin,
    UndoRedo,
    BoldItalicUnderlineToggles,
    BlockTypeSelect,
    CreateLink,
    InsertImage,
    InsertTable,
    InsertThematicBreak,
    InsertCodeBlock,
    ListsToggle,
    DiffSourceToggleWrapper,
    Separator,
  } = mod;

  function Editor({
    markdown,
    onChange,
    editorRef,
    dark,
  }: {
    markdown: string;
    onChange: (value: string) => void;
    editorRef: React.RefObject<MDXEditorMethods | null>;
    dark: boolean;
  }) {
    return (
      <MDXEditor
        ref={editorRef}
        markdown={markdown}
        onChange={onChange}
        // Their own dark palette; without it the editor stays light inside a
        // dark sheet.
        className={cn("lernova-mdx", dark && "dark-theme dark-editor")}
        contentEditableClassName="lernova-mdx-content"
        plugins={[
          headingsPlugin(),
          listsPlugin(),
          quotePlugin(),
          thematicBreakPlugin(),
          linkPlugin(),
          linkDialogPlugin(),
          imagePlugin(),
          tablePlugin(),
          codeBlockPlugin({ defaultCodeBlockLanguage: "txt" }),
          codeMirrorPlugin({
            codeBlockLanguages: {
              txt: "Plain text",
              js: "JavaScript",
              ts: "TypeScript",
              kotlin: "Kotlin",
              java: "Java",
              python: "Python",
              sql: "SQL",
              json: "JSON",
              bash: "Shell",
              html: "HTML",
              css: "CSS",
            },
          }),
          // Typing `# ` or `- ` becomes a heading or a list, as in Notion.
          markdownShortcutPlugin(),
          // Provides the rich / source toggle in the toolbar.
          diffSourcePlugin({ viewMode: "rich-text" }),
          toolbarPlugin({
            toolbarContents: () => (
              <DiffSourceToggleWrapper options={["rich-text", "source"]}>
                <UndoRedo />
                <Separator />
                <BoldItalicUnderlineToggles />
                <Separator />
                <BlockTypeSelect />
                <ListsToggle />
                <Separator />
                <CreateLink />
                <InsertImage />
                <InsertTable />
                <InsertCodeBlock />
                <InsertThematicBreak />
              </DiffSourceToggleWrapper>
            ),
          }),
        ]}
      />
    );
  }

  return { default: Editor };
});

/**
 * A document editor whose value is a markdown string.
 *
 * You type into it like a document — headings, lists, tables, code blocks — and
 * what comes out is markdown, so `article_contents.content` stays readable by
 * anything and is not tied to this editor. The toolbar's toggle switches
 * between the rich view and the markdown source, which is the preview in both
 * directions.
 */
export function MarkdownEditor({
  value,
  onChange,
  /** Changing this remounts the editor — it seeds from `markdown` only once. */
  seedKey,
  className,
}: {
  value: string;
  onChange: (next: string) => void;
  seedKey?: string;
  className?: string;
}) {
  const { resolvedTheme } = useTheme();
  const editorRef = useRef<MDXEditorMethods | null>(null);
  const [seeded, setSeeded] = useState(value);

  // MDXEditor takes `markdown` as an initial value, not a controlled prop:
  // pushing every keystroke back in would fight the cursor. It is re-seeded
  // only when the caller says the subject changed.
  useEffect(() => {
    setSeeded(value);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seedKey]);

  return (
    <div
      className={cn(
        "overflow-hidden rounded-lg border bg-background",
        "[&_.lernova-mdx-content]:min-h-[45vh] [&_.lernova-mdx-content]:px-4 [&_.lernova-mdx-content]:py-3",
        className,
      )}
    >
      <Suspense
        fallback={
          <div className="space-y-2 p-4">
            <Skeleton className="h-8 w-64" />
            <Skeleton className="h-[40vh] w-full" />
          </div>
        }
      >
        <Inner
          key={seedKey}
          markdown={seeded}
          onChange={onChange}
          editorRef={editorRef}
          dark={resolvedTheme === "dark"}
        />
      </Suspense>
    </div>
  );
}
