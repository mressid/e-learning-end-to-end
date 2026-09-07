import { ShieldAlert } from "lucide-react";
import type { ReactNode } from "react";
import { usePermissions } from "@/hooks/queries";
import { Skeleton } from "@/components/ui/skeleton";
import type { Permission } from "@/api";

/**
 * Hides a page from an administrator who lacks the permission behind it.
 *
 * Courtesy, not security. The server refuses the request regardless — this only
 * decides whether to show a page that would answer 403 to every call on it,
 * which reads as breakage rather than as a boundary.
 */
export function PermissionGate({
  permission,
  children,
}: {
  permission: Permission;
  children: ReactNode;
}) {
  const { has, isReady } = usePermissions();

  // Until the browser has read the token, "no permission" and "not known yet"
  // look identical. Showing the refusal during that window would flash a denial
  // at an administrator who holds the permission perfectly well.
  if (!isReady) {
    return (
      <div className="space-y-4 p-4 sm:p-6">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-4 w-80" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  if (has(permission)) return <>{children}</>;

  return (
    <div className="p-4 sm:p-6">
      <div className="card-surface grid min-h-[50vh] place-items-center p-8 text-center">
        <div className="max-w-sm">
          <div className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-muted text-muted-foreground">
            <ShieldAlert className="h-5 w-5" />
          </div>
          <h1 className="mt-4 text-xl font-bold tracking-tight">Not available to you</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This page needs the{" "}
            <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs">{permission}</code>{" "}
            permission, which your account does not hold. A super admin can grant it through a role.
          </p>
        </div>
      </div>
    </div>
  );
}
