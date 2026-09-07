import { useState, type FormEvent } from "react";
import { Shield, Plus, Trash2, UserPlus } from "lucide-react";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
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
import { toast } from "sonner";

const fail = (err: unknown, fallback: string) =>
  toast.error(parseApiError(err).message || fallback);

/**
 * Roles, permissions and administrator accounts.
 *
 * Everything here is super-admin only. The backend answers
 * `403 SUPER_ADMIN_ONLY` to anyone else, and that is not a permission a super
 * admin can grant away — the ability to hand out power is deliberately not
 * itself grantable, so no role can be configured into becoming one.
 */
export function AccessPanel() {
  return (
    <section className="card-surface space-y-4 p-5 sm:p-6">
      <div className="flex items-center gap-2">
        <div className="grid h-8 w-8 place-items-center rounded-lg bg-primary/10 text-primary">
          <Shield className="h-4 w-4" />
        </div>
        <div>
          <h2 className="text-lg font-bold">Administrators &amp; roles</h2>
          <p className="text-xs text-muted-foreground">
            Super admin only. A role change takes effect on that administrator's next token refresh,
            not immediately.
          </p>
        </div>
      </div>

      <Tabs defaultValue="roles">
        <TabsList>
          <TabsTrigger value="roles">Roles</TabsTrigger>
          <TabsTrigger value="admins">Administrators</TabsTrigger>
        </TabsList>
        <TabsContent value="roles" className="mt-4">
          <RolesTab />
        </TabsContent>
        <TabsContent value="admins" className="mt-4">
          <AdminsTab />
        </TabsContent>
      </Tabs>
    </section>
  );
}

function RolesTab() {
  const roles = useRolesQuery();
  const permissions = usePermissionCatalogueQuery();
  const createRole = useCreateRoleMutation();
  const deleteRole = useDeleteRoleMutation();

  const [name, setName] = useState("");
  const [pendingDelete, setPendingDelete] = useState<RoleResponse | null>(null);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    createRole.mutate(
      { name: name.trim(), description: null, permissions: [] },
      {
        onSuccess: () => {
          toast.success(`Role "${name.trim()}" created`);
          setName("");
        },
        onError: (err) => fail(err, "Could not create that role."),
      },
    );
  };

  if (roles.isLoading || permissions.isLoading) {
    return <Skeleton className="h-40 w-full" />;
  }

  if (roles.isError) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground">
        {parseApiError(roles.error).message || "Could not load roles."}
      </p>
    );
  }

  return (
    <div className="space-y-5">
      <form onSubmit={submit} className="flex flex-wrap items-end gap-2">
        <div className="min-w-0 flex-1 space-y-1.5 sm:max-w-xs">
          <Label htmlFor="role-name">New role</Label>
          <Input
            id="role-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Content moderator"
          />
        </div>
        <Button type="submit" disabled={createRole.isPending || !name.trim()}>
          <Plus className="h-4 w-4" />
          Create
        </Button>
      </form>

      <div className="space-y-4">
        {roles.data?.map((role) => (
          <RoleCard
            key={role.id}
            role={role}
            catalogue={permissions.data ?? []}
            onDelete={() => setPendingDelete(role)}
          />
        ))}
      </div>

      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete "{pendingDelete?.name}"?</AlertDialogTitle>
            <AlertDialogDescription>
              Administrators holding this role lose the permissions it carried, on their next token
              refresh. The backend refuses if the role is still assigned to anyone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep it</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                const target = pendingDelete;
                if (!target?.id) return;
                deleteRole.mutate(target.id, {
                  onSuccess: () => toast.success(`Role "${target.name}" deleted`),
                  onError: (err) => fail(err, "Could not delete that role."),
                  onSettled: () => setPendingDelete(null),
                });
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function RoleCard({
  role,
  catalogue,
  onDelete,
}: {
  role: RoleResponse;
  catalogue: { code?: string; description?: string }[];
  onDelete: () => void;
}) {
  const setPermissions = useSetRolePermissionsMutation();
  const held = new Set(role.permissions ?? []);

  const toggle = (code: string, next: boolean) => {
    if (!role.id) return;
    const updated = new Set(held);
    if (next) updated.add(code);
    else updated.delete(code);

    setPermissions.mutate(
      { roleId: role.id, permissions: [...updated] },
      { onError: (err) => fail(err, "Could not update that role.") },
    );
  };

  return (
    <div className="rounded-xl border p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="font-semibold">{role.name}</h3>
            {role.isSuper && (
              <Badge variant="default" className="h-5 px-1.5 text-[10px] font-semibold">
                every permission
              </Badge>
            )}
            {role.isSystem && (
              <Badge variant="outline" className="h-5 px-1.5 text-[10px] font-semibold">
                system
              </Badge>
            )}
          </div>
          {role.description && (
            <p className="mt-0.5 text-xs text-muted-foreground">{role.description}</p>
          )}
        </div>
        {!role.isSystem && (
          <Button variant="ghost" size="sm" onClick={onDelete}>
            <Trash2 className="h-3.5 w-3.5" />
            Delete
          </Button>
        )}
      </div>

      {role.isSuper ? (
        // Not a checklist to edit: the super role is defined as "all of them,
        // including ones a future migration adds". Rendering it as ticked boxes
        // would invite unticking one, which the backend would ignore.
        <p className="mt-3 text-xs text-muted-foreground">
          Holds every permission automatically, including any added later. Not editable.
        </p>
      ) : (
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          {catalogue.map((permission) => {
            const code = permission.code;
            if (!code) return null;
            const id = `${role.id}-${code}`;
            return (
              <label
                key={code}
                htmlFor={id}
                className="flex cursor-pointer items-start gap-2 rounded-lg p-1.5 hover:bg-secondary/60"
              >
                <Checkbox
                  id={id}
                  checked={held.has(code)}
                  disabled={setPermissions.isPending}
                  onCheckedChange={(v) => toggle(code, v === true)}
                  className="mt-0.5"
                />
                <span className="min-w-0">
                  <span className="block font-mono text-[11px] font-medium">{code}</span>
                  <span className="block text-[11px] text-muted-foreground">
                    {permission.description}
                  </span>
                </span>
              </label>
            );
          })}
        </div>
      )}
    </div>
  );
}

function AdminsTab() {
  const admins = useAdminsQuery({ page: 0, size: 50 });
  const roles = useRolesQuery();
  const me = useAdminMeQuery();
  const createAdmin = useCreateAdminMutation();
  const setRole = useSetAdminRoleMutation();
  const setStatus = useSetAdminStatusMutation();

  const [form, setForm] = useState({ username: "", email: "", password: "" });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    createAdmin.mutate(form, {
      onSuccess: (created) => {
        toast.success(`${created.username} added`);
        setForm({ username: "", email: "", password: "" });
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
      <form onSubmit={submit} className="flex flex-wrap items-end gap-2">
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
        </div>
        <Button type="submit" disabled={createAdmin.isPending}>
          <UserPlus className="h-4 w-4" />
          Add
        </Button>
      </form>
      <p className="-mt-3 text-xs text-muted-foreground">
        A new administrator starts with no roles, and so no permissions. Grant one below.
      </p>

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
    </div>
  );
}
