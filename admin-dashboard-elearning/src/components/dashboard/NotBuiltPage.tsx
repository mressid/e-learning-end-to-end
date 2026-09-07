import { Link } from "@tanstack/react-router";
import { Construction } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * A route that resolves but has nothing behind it.
 *
 * These pages were `PlaceholderPage` — a title over blank space, which reads as
 * a page that failed to load. The distinction matters: this is not missing
 * data, it is a feature the platform does not have. Saying which is which saves
 * someone half an hour deciding whether it is broken.
 *
 * They are kept as routes, and removed from the navigation, so existing links
 * do not 404.
 */
export function NotBuiltPage({
  title,
  reason,
  wouldNeed,
}: {
  title: string;
  reason: string;
  wouldNeed: string;
}) {
  return (
    <div className="p-4 sm:p-6">
      <div className="card-surface grid min-h-[60vh] place-items-center p-8 text-center">
        <div className="max-w-md">
          <div className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-muted text-muted-foreground">
            <Construction className="h-5 w-5" />
          </div>
          <h1 className="mt-4 text-2xl font-extrabold tracking-tight">{title}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{reason}</p>
          <p className="mt-3 text-sm text-muted-foreground">
            <span className="font-semibold text-foreground">To build it: </span>
            {wouldNeed}
          </p>
          <Button asChild variant="outline" size="sm" className="mt-5">
            <Link to="/">Back to the dashboard</Link>
          </Button>
        </div>
      </div>
    </div>
  );
}
