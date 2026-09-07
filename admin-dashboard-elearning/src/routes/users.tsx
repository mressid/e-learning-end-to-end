import { useCallback, useRef } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Users, GraduationCap, Shield, KeyRound, Radio, Plus } from "lucide-react";
import { usePermissions, useAdminMeQuery } from "@/hooks/queries";
import { PERMISSIONS, type Permission } from "@/api";
import { PageHeader } from "@/components/dashboard/PageHeader";
import { LearnersPanel } from "@/components/people/LearnersPanel";
import { InstructorsPanel } from "@/components/people/InstructorsPanel";
import { AdminsPanel } from "@/components/people/AdminsPanel";
import { RolesPanel } from "@/components/people/RolesPanel";
import { SessionsPanel } from "@/components/people/SessionsPanel";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ShieldAlert } from "lucide-react";

export type PeopleTab = "learners" | "instructors" | "admins" | "roles" | "sessions";

const TABS: PeopleTab[] = ["learners", "instructors", "admins", "roles", "sessions"];

export interface PeopleWorkspaceSearch {
  /**
   * Named `view`, not `tab`.
   *
   * The course workspace already owns `tab` with its own set of values, and
   * TanStack merges search-param types across routes — sharing the name makes
   * every `search: (prev) => ...` on either route see the union of both, which
   * neither can satisfy.
   */
  view?: PeopleTab | undefined;
}

export const Route = createFileRoute("/users")({
  validateSearch: (raw: Record<string, unknown>): PeopleWorkspaceSearch => {
    const view = raw["view"];
    return { view: TABS.includes(view as PeopleTab) ? (view as PeopleTab) : "learners" };
  },
  head: () => ({
    meta: [
      { title: "People — Lernova" },
      {
        name: "description",
        content: "Learners, instructors, administrators, roles and live sessions.",
      },
    ],
  }),
  component: PeopleWorkspace,
});

/**
 * One workspace for everyone on the platform.
 *
 * Learners and instructors used to be two top-level pages while administrators,
 * roles and sessions were buried in Settings tabs — so "who can do what" was
 * answered in four places, none of which linked to the others. They are the same
 * subject and now share a context, in the shape the course workspace already
 * established: the sidebar switches to contextual navigation and drives the view
 * through `?tab=`.
 *
 * Each section still enforces its own permission. The workspace only decides
 * what to offer; the server decides what to answer.
 */
const VIEWS: Record<
  PeopleTab,
  { title: string; description: string; icon: typeof Users; permission?: Permission }
> = {
  learners: {
    title: "Learners",
    description: "Everyone registered on the platform, and what to do about them.",
    icon: Users,
    permission: PERMISSIONS.USER_READ,
  },
  instructors: {
    title: "Instructors",
    description: "People who may author courses.",
    icon: GraduationCap,
    permission: PERMISSIONS.USER_READ,
  },
  admins: {
    title: "Administrators",
    description: "Dashboard accounts, and the roles they hold.",
    icon: Shield,
  },
  roles: {
    title: "Roles & permissions",
    description: "What a role carries, and who may configure it.",
    icon: KeyRound,
  },
  sessions: {
    title: "Live sessions",
    description: "Who is signed in, and how to stop them being.",
    icon: Radio,
    permission: PERMISSIONS.SETTINGS_MANAGE,
  },
};

function PeopleWorkspace() {
  const { view: requested } = Route.useSearch();
  const { has, isReady } = usePermissions();
  const me = useAdminMeQuery();

  // Roles and administrators are super-admin only, which is not a permission
  // code — it is whether one of your roles is the super role.
  const isSuperAdmin = (me.data?.roles ?? []).some((r) => r.isSuper);

  const active: PeopleTab = requested ?? "learners";
  const view = VIEWS[active];

  // Each panel owns its own "add" sheet — it knows what it is creating — while
  // the button belongs up here beside the title. The panel hands its opener out
  // once on mount and this holds it.
  const openAdd = useRef<Partial<Record<PeopleTab, () => void>>>({});
  const registerInstructorAdd = useCallback((open: () => void) => {
    openAdd.current.instructors = open;
  }, []);
  const registerAdminAdd = useCallback((open: () => void) => {
    openAdd.current.admins = open;
  }, []);
  const registerRoleAdd = useCallback((open: () => void) => {
    openAdd.current.roles = open;
  }, []);

  const addAction: Partial<Record<PeopleTab, { label: string; allowed: boolean }>> = {
    instructors: { label: "Add instructor", allowed: has(PERMISSIONS.USER_WRITE) },
    admins: { label: "Add administrator", allowed: isSuperAdmin },
    roles: { label: "Create role", allowed: isSuperAdmin },
  };
  const action = addAction[active];

  const allowed =
    active === "admins" || active === "roles"
      ? isSuperAdmin
      : view.permission
        ? has(view.permission)
        : true;

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <PageHeader title={view.title} description={view.description}>
        {action?.allowed && allowed && (
          <Button size="sm" onClick={() => openAdd.current[active]?.()}>
            <Plus className="h-4 w-4" />
            {action.label}
          </Button>
        )}
      </PageHeader>

      {!isReady || (me.isLoading && (active === "admins" || active === "roles")) ? (
        <div className="space-y-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-64 w-full rounded-xl" />
        </div>
      ) : !allowed ? (
        <NotAllowed tab={active} isSuperAdmin={isSuperAdmin} />
      ) : (
        <>
          {active === "learners" && <LearnersPanel />}
          {active === "instructors" && <InstructorsPanel onAdd={registerInstructorAdd} />}
          {active === "admins" && <AdminsPanel onAdd={registerAdminAdd} />}
          {active === "roles" && <RolesPanel onAdd={registerRoleAdd} />}
          {active === "sessions" && <SessionsPanel />}
        </>
      )}

      {/* Which section is showing lives in the URL, and the sidebar is what
          changes it — so this page never navigates itself. */}
      <span className="sr-only" aria-live="polite">
        Showing {view.title}
      </span>
    </div>
  );
}

function NotAllowed({ tab, isSuperAdmin }: { tab: PeopleTab; isSuperAdmin: boolean }) {
  const superOnly = tab === "admins" || tab === "roles";
  const permission = VIEWS[tab].permission;

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
