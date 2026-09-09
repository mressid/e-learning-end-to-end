import { useEffect, useState, type FormEvent } from "react";
import { Pencil } from "lucide-react";
import {
  useRolesQuery,
  usePermissionCatalogueQuery,
  useCreateRoleMutation,
  useSetRolePermissionsMutation,
  useDeleteRoleMutation,
  useAdminsQuery,
  useCreateAdminMutation,
  useSetAdminRoleMutation,
  useSetAdminStatusMutation,
  useSetAdminPasswordMutation,
  useAdminMeQuery,
} from "@/hooks/queries";
import { parseApiError, type AdminResponse, type RoleResponse } from "@/api";
import { StatusBadge } from "@/components/dashboard/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";
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
import { FloatingDetailSheet } from "@/components/dashboard/FloatingDetailSheet";
import { SetPasswordSection } from "./SetPasswordSection";
import type { PanelAddProps } from "./panel";
import { toast } from "sonner";

const CREATE_ADMIN_FORM = "create-admin-form";

const fail = (err: unknown, fallback: string) =>
  toast.error(parseApiError(err).message || fallback);

/**
 * Everything here is **super-admin only**. The backend answers
 * `403 SUPER_ADMIN_ONLY` to anyone else, and that is not a permission the super
 * admin can grant away: the ability to hand out power is deliberately not
 * itself grantable, so no role can be configured into becoming one.
 */

export function AdminsPanel({ onAdd }: PanelAddProps) {
  const admins = useAdminsQuery({ page: 0, size: 50 });
  const roles = useRolesQuery();
  const me = useAdminMeQuery();
  const createAdmin = useCreateAdminMutation();
  const setRole = useSetAdminRoleMutation();

  // The administrator whose sheet is open, if any. The row itself rather than an
  // id, so the sheet can name them — acting on the wrong account is silent.
  const [managing, setManaging] = useState<AdminResponse | null>(null);

  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ username: "", email: "", password: "" });

  useEffect(() => {
    onAdd?.(() => setCreating(true));
  }, [onAdd]);

  const closeCreate = () => {
    setForm({ username: "", email: "", password: "" });
    setCreating(false);
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    createAdmin.mutate(form, {
      onSuccess: (created) => {
        toast.success(`${created.username} added — give them a role below`);
        closeCreate();
      },
      onError: (err) => fail(err, "Could not create that administrator."),
    });
  };

  if (admins.isLoading || roles.isLoading) return <Skeleton className="h-40 w-full" />;

  if (admins.isError) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground">
        {parseApiError(admins.error).message || "Could not load administrators."}
      </p>
    );
  }

  return (
    <div className="space-y-5">
      <div className="space-y-3">
        {admins.data?.content?.map((admin) => {
          const heldRoles = new Set((admin.roles ?? []).map((r) => r.id));
          const isSelf = admin.id === me.data?.id;

          return (
            <div key={admin.id} className="rounded-xl border p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <h3 className="font-semibold">{admin.username}</h3>
                    <StatusBadge status={admin.status} />
                    {isSelf && (
                      <Badge variant="outline" className="h-5 px-1.5 text-[10px]">
                        you
                      </Badge>
                    )}
                  </div>
                  <p className="mt-0.5 text-xs text-muted-foreground">{admin.email}</p>
                </div>

                {/* Nothing to manage on your own account. Suspending yourself
                    locks the platform's only unlocker out, and setting your own
                    password here would skip the current-password check that
                    stops a borrowed session locking you out. The server refuses
                    both regardless; this only avoids offering them.

                    Roles stay on the row below because granting one is a quick,
                    reversible thing you do while comparing people. Suspending
                    and replacing a password are neither, so they moved into the
                    sheet, where you have opened the account and looked at it. */}
                {!isSelf && admin.id && (
                  <Button variant="ghost" size="sm" onClick={() => setManaging(admin)}>
                    <Pencil className="h-3.5 w-3.5" />
                    <span className="sr-only sm:not-sr-only">Manage</span>
                  </Button>
                )}
              </div>

              <div className="mt-3 flex flex-wrap gap-2">
                {roles.data?.map((role) => {
                  if (!role.id || !admin.id) return null;
                  const granted = heldRoles.has(role.id);
                  return (
                    <button
                      key={role.id}
                      type="button"
                      disabled={setRole.isPending}
                      onClick={() =>
                        setRole.mutate(
                          { adminId: admin.id!, roleId: role.id!, granted: !granted },
                          {
                            onSuccess: () =>
                              toast.success(
                                granted
                                  ? `${role.name} taken from ${admin.username}`
                                  : `${role.name} given to ${admin.username}`,
                              ),
                            onError: (err) => fail(err, "Could not change that role."),
                          },
                        )
                      }
                      className={
                        granted
                          ? "rounded-full bg-primary px-2.5 py-1 text-xs font-semibold text-primary-foreground disabled:opacity-50"
                          : "rounded-full border px-2.5 py-1 text-xs font-medium text-muted-foreground hover:bg-secondary disabled:opacity-50"
                      }
                    >
                      {role.name}
                    </button>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      <ManageAdminSheet admin={managing} onClose={() => setManaging(null)} />

      <FloatingDetailSheet
        open={creating}
        onOpenChange={(next) => !next && closeCreate()}
        title="Add an administrator"
        description="They start with no roles, and so no permissions. Give them one from the list afterwards."
        footerActions={
          <>
            <Button type="button" variant="outline" onClick={closeCreate}>
              Cancel
            </Button>
            <Button type="submit" form={CREATE_ADMIN_FORM} disabled={createAdmin.isPending}>
              {createAdmin.isPending ? "Adding…" : "Add administrator"}
            </Button>
          </>
        }
      >
        <form id={CREATE_ADMIN_FORM} onSubmit={submit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="admin-username">Username</Label>
            <Input
              id="admin-username"
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="admin-email">Email</Label>
            <Input
              id="admin-email"
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="admin-password">Initial password</Label>
            <Input
              id="admin-password"
              type="password"
              minLength={12}
              autoComplete="new-password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              required
            />
            <p className="text-xs text-muted-foreground">
              At least 12 characters. There is no confirmation email and nothing forces a change at
              first sign-in, so pass it on deliberately.
            </p>
          </div>
        </form>
      </FloatingDetailSheet>
    </div>
  );
}

/**
 * One administrator, and the things you do to them deliberately.
 *
 * Suspending an account and replacing its password are both rare and both hard
 * to take back, so neither belongs on a row you are scanning past. Granting a
 * role is the opposite — quick, reversible, and something you do while comparing
 * people — so it stays on the row.
 *
 * Never opened for your own account: the panel does not offer it, and the server
 * refuses both operations on yourself regardless.
 */
function ManageAdminSheet({
  admin,
  onClose,
}: {
  admin: AdminResponse | null;
  onClose: () => void;
}) {
  const setStatus = useSetAdminStatusMutation();
  const setPassword = useSetAdminPasswordMutation();
  const [passwordError, setPasswordError] = useState("");

  const name = admin?.username || admin?.email || "";
  const suspended = admin ? admin.status !== "ACTIVE" : false;

  return (
    <FloatingDetailSheet
      open={admin !== null}
      onOpenChange={(next) => !next && onClose()}
      title={admin ? `Manage ${name}` : "Manage"}
      description={admin?.email}
      footerActions={
        <Button type="button" variant="outline" onClick={onClose}>
          Close
        </Button>
      }
    >
      {admin?.id && (
        <div className="space-y-4">
          <SetPasswordSection
            key={admin.id}
            subject={name}
            pending={setPassword.isPending}
            error={passwordError}
            onSubmit={(newPassword) => {
              setPasswordError("");
              setPassword.mutate(
                { adminId: admin.id!, newPassword },
                {
                  onSuccess: () => toast.success(`New password set for ${name}. Pass it on.`),
                  onError: (err) =>
                    setPasswordError(parseApiError(err).message || "Could not set that password."),
                },
              );
            }}
          />

          <div className="rounded-lg border border-dashed p-3">
            <p className="text-sm font-medium">
              {suspended ? "This account is suspended" : "Account access"}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              {suspended
                ? "They cannot sign in. Reinstating restores exactly the roles they held."
                : "Suspending ends their sessions and stops them signing in. Their roles are kept, so reinstating restores what they had."}
            </p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="mt-2"
              disabled={setStatus.isPending}
              onClick={() =>
                setStatus.mutate(
                  { adminId: admin.id!, status: suspended ? "ACTIVE" : "SUSPENDED" },
                  {
                    onSuccess: () => {
                      toast.success(suspended ? `${name} reinstated` : `${name} suspended`);
                      onClose();
                    },
                    onError: (err) => fail(err, "Could not change that account."),
                  },
                )
              }
            >
              {suspended ? "Reinstate" : "Suspend"}
            </Button>
          </div>
        </div>
      )}
    </FloatingDetailSheet>
  );
}
