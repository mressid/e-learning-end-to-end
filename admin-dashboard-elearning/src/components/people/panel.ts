/**
 * How a panel hands its "add" action to the workspace toolbar.
 *
 * The button belongs in the page header, next to the title, while the sheet and
 * its form state belong to the panel that knows what it is creating. Rather than
 * lift the whole sheet up, the panel passes its opener out once on mount and the
 * workspace holds it in a ref.
 */
export interface PanelAddProps {
  onAdd?: (open: () => void) => void;
}
