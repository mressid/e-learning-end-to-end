import { useEffect, useState, type FormEvent } from "react";
import { Trash2 } from "lucide-react";
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

const CREATE_ROLE_FORM = "create-role-form";

const fail = (err: unknown, fallback: string) =>
  toast.error(parseApiError(err).message || fallback);

/**
 * Everything here is **super-admin only**. The backend answers
 * `403 SUPER_ADMIN_ONLY` to anyone else, and that is not a permission the super
 * admin can grant away: the ability to hand out power is deliberately not
 * itself grantable, so no role can be configured into becoming one.
 */

export function RolesPanel({ onAdd }: PanelAddProps) {
  const roles = useRolesQuery();
  const permissions = usePermissionCatalogueQuery();
  const createRole = useCreateRoleMutation();
  const deleteRole = useDeleteRoleMutation();

  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [pendingDelete, setPendingDelete] = useState<RoleResponse | null>(null);

  useEffect(() => {
    onAdd?.(() => setCreating(true));
  }, [onAdd]);

  const reset = () => {
    setName("");
    setDescription("");
    setChosen(new Set());
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    createRole.mutate(
      // Permissions are chosen here rather than only afterwards on the card: a
      // role created empty grants nothing, and the moment between creating it
      // and ticking the first box is a role that exists and does nothing.
      { name: name.trim(), description: description.trim() || null, permissions: [...chosen] },
      {
        onSuccess: () => {
          toast.success(`Role "${name.trim()}" created`);
          reset();
          setCreating(false);
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

  const closeCreate = () => {
    reset();
    setCreating(false);
  };

  return (
    <div className="space-y-5">
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

      <FloatingDetailSheet
        open={creating}
        onOpenChange={(next) => !next && closeCreate()}
        title="Create a role"
        description="A role is a named set of permissions you can hand to an administrator."
        size="lg"
        footerActions={
          <>
            <Button type="button" variant="outline" onClick={closeCreate}>
              Cancel
            </Button>
            <Button
              type="submit"
              form={CREATE_ROLE_FORM}
              disabled={createRole.isPending || !name.trim()}
            >
              {createRole.isPending ? "Creating…" : "Create role"}
            </Button>
          </>
        }
      >
        <form id={CREATE_ROLE_FORM} onSubmit={submit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="new-role-name">Name</Label>
            <Input
              id="new-role-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Content moderator"
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="new-role-description">Description</Label>
            <Input
              id="new-role-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What is this role for?"
            />
            <p className="text-xs text-muted-foreground">
              Optional, but the next person to read the list will thank you.
            </p>
          </div>

          <div className="space-y-2">
            <Label>Permissions</Label>
            <p className="text-xs text-muted-foreground">
              {chosen.size === 0
                ? "None chosen — the role will grant nothing until you add some."
                : `${chosen.size} of ${permissions.data?.length ?? 0} selected.`}
            </p>
            <div className="space-y-1">
              {permissions.data?.map((permission) => {
                const code = permission.code;
                if (!code) return null;
                return (
                  <label
                    key={code}
                    htmlFor={`new-role-${code}`}
                    className="flex cursor-pointer items-start gap-2 rounded-lg p-1.5 hover:bg-secondary/60"
                  >
                    <Checkbox
                      id={`new-role-${code}`}
                      checked={chosen.has(code)}
                      onCheckedChange={(v) =>
                        setChosen((prev) => {
                          const next = new Set(prev);
                          if (v === true) next.add(code);
                          else next.delete(code);
                          return next;
                        })
                      }
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
          </div>
        </form>
      </FloatingDetailSheet>

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
