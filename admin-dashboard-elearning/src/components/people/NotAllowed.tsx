import { ShieldAlert } from "lucide-react";
import type { Permission } from "@/api";

/**
 * What a page shows instead of a directory the signed-in admin may not read.
 *
 * Shared by the three people pages rather than repeated in each: hiding the
 * sidebar link is courtesy, but a page reached by its URL still has to say
 * something, and it should say the same thing everywhere.
 *
 * This is never the security boundary — the server refuses the call regardless.
 * It exists so the refusal reads as a rule rather than a failure.
 */
export function NotAllowed({
  permission,
  superOnly = false,
  isSuperAdmin = false,
}: {
  /** The permission the section needs, when that is what is missing. */
  permission?: Permission | undefined;
  superOnly?: boolean;
  isSuperAdmin?: boolean;
}) {
  return (
    <div className="card-surface grid min-h-[40vh] place-items-center p-8 text-center">
      <div className="max-w-sm">
        <div className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-muted text-muted-foreground">
          <ShieldAlert className="h-5 w-5" />
        </div>
        <h2 className="mt-4 text-lg font-bold tracking-tight">Not available to you</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          {superOnly ? (
            <>
              Managing administrators and roles is reserved for a super admin
              {isSuperAdmin ? "" : ", which your account is not"}. It is deliberately not a
              permission: anything that can hand out power could hand out the power to hand out
              power.
            </>
          ) : (
            <>
              This section needs the{" "}
              <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs">{permission}</code>{" "}
              permission, which your account does not hold. A super admin can grant it through a
              role.
            </>
          )}
        </p>
      </div>
    </div>
  );
}
