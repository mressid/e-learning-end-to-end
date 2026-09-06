<!-- LOVABLE:BEGIN -->

> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.

<!-- LOVABLE:END -->

## UI and UX Architecture Rules

### 1. Floating Sidebar / Slide-Over Drawer Oriented (No Modal Dialogs for Details/Forms)

The platform follows a **floating sidebar / slide-over drawer** paradigm instead of centered modal popups:

- **Detail Inspection**: All entity details (course preview, student profile, instructor dossier, invoice breakdown, audit logs) must render inside a floating side sheet/drawer, preserving table and page context.
- **Creation and Edit Workflows**: Entity creation (e.g., New Course, Add Student, Create Lesson) and editing forms must slide in from the screen edge as an inspector panel or drawer.
- **Filters and Facets**: Advanced data filters, column visibility pickers, and search drawers must use slide-over sheets.
- **Text Direction and RTL**: Side sheets must anchor to the trailing edge of the viewport:
  - In LTR mode (`en`, `fr`, `es`): Slides in from the **right**.
  - In RTL mode (`ar`): Slides in from the **left**.
- **Exception for Modals**: Centered dialogs (`AlertDialog`) are strictly reserved for critical destructive confirmations (such as irreversible resource deletion) where user flow intentionally demands a hard stop.

### 2. Zero Emojis Policy

- Absolutely no emojis in source code, component templates, documentation, or commit messages. Use Lucide icons or clean text badges instead.

### 3. Language Selector Presentation

- Languages must be listed using plain typographic labels (e.g., `English`, `Français`, `Español`, `العربية`). Country flags are not permitted.
