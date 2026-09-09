import MarkdownIt from "markdown-it";
import DOMPurify from "dompurify";

/**
 * The three shapes a lesson body can be stored in.
 *
 * Defined here rather than imported from the API layer so this module stays
 * a plain function of text in, safe HTML (or nothing) out — no dependency on
 * this app's generated types, so it can move to a learner-facing app, or a
 * shared package the two of them both pull from, without dragging anything
 * else along.
 */
export type LessonContentFormat = "MARKDOWN" | "HTML" | "PLAIN_TEXT";

// One parser for the process, not one per render. markdown-it's setup cost —
// building its rule chain — is the same regardless of how much text it is
// about to see, so paying it once at import time is strictly better than
// paying it again on every keystroke.
const markdownRenderer = new MarkdownIt({
  // Raw HTML inside a markdown body is escaped, not executed. Someone who
  // wants HTML picks the HTML format instead — that way what runs is always
  // what the author's chosen format says should run, not whatever happened
  // to be pasted into a markdown box.
  html: false,
  linkify: true,
  typographer: false,
});

/**
 * What `renderLessonBody` hands back: either HTML that has already been
 * through a sanitizer and is safe for `dangerouslySetInnerHTML`, or a signal
 * that there is nothing to sanitize because the format never produces
 * markup in the first place.
 */
export type RenderedLessonBody = { kind: "html"; html: string } | { kind: "text" };

/**
 * Turns a stored lesson body into something safe to put on a page.
 *
 * MARKDOWN and HTML both end up sanitized with DOMPurify — even the markdown
 * path, whose own `html: false` already refuses to execute embedded markup.
 * That refusal is a parsing choice, not a security boundary: markdown-it's
 * linkify can still produce an `href`, and a future plugin or option change
 * could reopen the door without anyone touching this file. Running every
 * format's output through the same sanitizer means the guarantee does not
 * depend on remembering that markdown-it currently behaves itself.
 *
 * PLAIN_TEXT never reaches a sanitizer or a parser at all — it comes back as
 * a signal to render, not a string of markup, so the caller is structurally
 * unable to feed it through `dangerouslySetInnerHTML` by accident.
 */
export function renderLessonBody(body: string, format: LessonContentFormat): RenderedLessonBody {
  if (format === "PLAIN_TEXT") {
    return { kind: "text" };
  }

  const rawHtml = format === "MARKDOWN" ? markdownRenderer.render(body) : body;
  return { kind: "html", html: DOMPurify.sanitize(rawHtml) };
}
