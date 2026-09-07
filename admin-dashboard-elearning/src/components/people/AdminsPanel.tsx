import { useEffect, useState, type FormEvent } from "react";
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
  useAdminMeQuery,
} from "@/hooks/queries";
import { parseApiError, type RoleResponse } from "@/api";
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
  const setStatus = useSetAdminStatusMutation();

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
          const suspended = admin.status !== "ACTIVE";

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

                {/* No self-suspension: locking yourself out of the only account
                    that can unlock accounts is not a recoverable mistake. */}
                {!isSelf && admin.id && (
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={setStatus.isPending}
                    onClick={() =>
                      setStatus.mutate(
                        { adminId: admin.id!, status: suspended ? "ACTIVE" : "SUSPENDED" },
                        {
                          onSuccess: () =>
                            toast.success(
                              suspended
                                ? `${admin.username} reinstated`
                                : `${admin.username} suspended`,
                            ),
                          onError: (err) => fail(err, "Could not change that account."),
                        },
                      )
                    }
                  >
                    {suspended ? "Reinstate" : "Suspend"}
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
