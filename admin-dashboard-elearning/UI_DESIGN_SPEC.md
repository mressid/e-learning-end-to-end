# UI and UX Design Specification: Floating Sidebar Architecture

## Overview

This specification establishes the architectural standard for interaction design across the Lernova Admin Dashboard. The system strictly adopts a **Floating Sidebar and Slide-Over Drawer** orientation rather than a centered modal dialog paradigm for displaying entity details, editing records, and side-by-side inspection.

---

## 1. Design Rationale

### Why Floating Sidebars Over Modals?

1. **Context Retention**: When inspecting a course, student, or transaction from a data grid, centered modals obscure the underlying list. Slide-over floating sidebars allow the user to view the detail pane while maintaining peripheral vision and spatial orientation of the dataset.
2. **Vertical Space Ergonomics**: Modern administrative records (course curriculums, lesson trees, student enrollment histories, audit logs) are vertically oriented. A full-height side drawer offers superior scroll ergonomics and form layout compared to constrained modal boxes.
3. **Workflow Continuity**: Users can quickly toggle through items in a table (e.g. clicking down a list of pending student submissions) with the side drawer dynamically updating its contents, without the jarring open-close animations of modal backdrops.
4. **Responsive Adaptability**: On desktop and tablet viewports, the drawer acts as a secondary inspection blade. On mobile devices, it smoothly scales to a full-width bottom sheet or full-screen drawer.

---

## 2. Interaction Model and Component Matrix

| Use Case                                                                      | Recommended UI Pattern    | Component                       | Justification                                                               |
| :---------------------------------------------------------------------------- | :------------------------ | :------------------------------ | :-------------------------------------------------------------------------- |
| **Entity Detail Inspector** (Course preview, student profile, order overview) | Floating Slide-Over Sheet | `Sheet` / `FloatingDetailSheet` | Keeps primary table visible; full-height vertical scroll for rich metadata. |
| **Record Creation Forms** (Add course, invite user, create lesson)            | Slide-Over Sheet          | `Sheet` / `FloatingDetailSheet` | Accommodates multi-section forms without viewport cramming.                 |
| **Quick Edit / Inspector**                                                    | Slide-Over Sheet          | `Sheet` / `FloatingDetailSheet` | Enables rapid edits with sticky save/cancel action bars.                    |
| **Advanced Filters & Columns**                                                | Slide-Over Drawer         | `Sheet`                         | Allows deep filtering configuration alongside live grid updates.            |
| **Destructive Confirmation** (Delete course, revoke access)                   | Centered Alert Dialog     | `AlertDialog`                   | Intentionally interrupts flow to prevent accidental irreversible actions.   |

---

## 3. Directional Adaptation (RTL Support)

The floating sidebar dynamically adjusts its entry side according to the active locale:

- **Left-to-Right (LTR)**: English (`en`), French (`fr`), Spanish (`es`)
  - Floating panel slides in from the **right edge**.
  - Close trigger and back actions align with standard LTR flow.
- **Right-to-Left (RTL)**: Arabic (`ar`)
  - Floating panel slides in from the **left edge**.
  - Action buttons and alignment invert automatically via CSS logical properties and `dir="rtl"`.

```text
[LTR Layout]
+-------------------------------------------------------------+
| Sidebar (Left) | Main Data Table / Content | Floating Sheet |
|                |                           | (Slides from   |
|                |                           |  Right)        |
+-------------------------------------------------------------+

[RTL Layout]
+-------------------------------------------------------------+
| Floating Sheet | Main Data Table / Content | Sidebar (Right)|
| (Slides from   |                           |                |
|  Left)         |                           |                |
+-------------------------------------------------------------+
```

---

## 4. Visual Anatomy of a Floating Sheet

- **Overlay**: Subtle darkened backdrop (`bg-black/40` or `bg-black/60` in dark mode) with `backdrop-blur-xs`.
- **Panel Container**:
  - Border and elevation: `border-l` (or `border-r` in RTL) with `bg-card` and subtle shadow (`shadow-2xl`).
  - Max width: standard (`max-w-md`), wide (`max-w-xl`), or extra-wide for complex builders (`max-w-2xl`).
- **Header**:
  - Sticky top bar with record title, category/status badge, and accessible close button (`X`).
- **Body Area**:
  - Scrollable content with distinct grouped sections, metadata key-values, and preview media.
- **Footer Action Bar**:
  - Sticky bottom container with primary actions (Save, Edit, Archive) and secondary actions (Cancel, Close).

---

## 5. Summary Rule for Developers and Agents

- **Rule**: Never introduce a centered `<Dialog>` for content inspection, previews, or multi-field forms. Always use `<Sheet side={dir === "rtl" ? "left" : "right"}>` or the pre-configured `<FloatingDetailSheet>`.
- **Modal Exception**: Reserve `<AlertDialog>` strictly for dangerous, irreversible operations (e.g. deleting a course).
