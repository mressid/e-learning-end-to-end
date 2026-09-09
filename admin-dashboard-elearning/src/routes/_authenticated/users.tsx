import { useCallback, useRef } from "react";
import { createFileRoute, redirect } from "@tanstack/react-router";
import { Plus } from "lucide-react";
import { usePermissions, useAdminMeQuery } from "@/hooks/queries";
import { PERMISSIONS, type Permission } from "@/api";
import { PageHeader } from "@/components/dashboard/PageHeader";
import { AdminsPanel } from "@/components/people/AdminsPanel";
import { RolesPanel } from "@/components/people/RolesPanel";
import { SessionsPanel } from "@/components/people/SessionsPanel";
import { NotAllowed } from "@/components/people/NotAllowed";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

export type AccessTab = "admins" | "roles" | "sessions";

const TABS: AccessTab[] = ["admins", "roles", "sessions"];

/**
 * The two views that left, still recognised here so their URLs can be
 * forwarded. `beforeLoad` redirects them; nothing renders them.
 */
type MovedView = "learners" | "instructors";

const MOVED: Record<MovedView, "/students" | "/instructors"> = {
  learners: "/students",
  instructors: "/instructors",
};

export interface AccessWorkspaceSearch {
  /**
   * Named `view`, not `tab`.
   *
   * The course workspace already owns `tab` with its own set of values, and
   * TanStack merges search-param types across routes — sharing the name makes
   * every `search: (prev) => ...` on either route see the union of both, which
   * neither can satisfy.
   */
  view?: AccessTab | MovedView | undefined;
}

export const Route = createFileRoute("/_authenticated/users")({
  validateSearch: (raw: Record<string, unknown>): AccessWorkspaceSearch => {
    const view = raw["view"];
    if (TABS.includes(view as AccessTab)) return { view: view as AccessTab };
    // Deliberately preserved rather than normalised away: validation runs
    // before `beforeLoad`, so folding these into "admins" here would leave the
    // redirect below nothing to match and quietly land an old bookmark on the
    // administrator list.
    if (view === "learners" || view === "instructors") return { view };
    return { view: "admins" };
  },
  /*
   * Learners and instructors used to be views here, and are pages again. Their
   * old URLs are forwarded so a bookmark or an open tab lands on the list it
   * was pointing at rather than a different one.
   */
  beforeLoad: ({ search }) => {
    const view = search.view;
    if (view === "learners" || view === "instructors") {
      throw redirect({ to: MOVED[view] });
    }
  },
  head: () => ({
    meta: [
      { title: "Access — Lernova" },
      { name: "description", content: "Dashboard accounts, roles and live sessions." },
    ],
  }),
  component: AccessWorkspace,
});

/**
 * Who may work in the dashboard, and what they may do there.
 *
 * These three were once tabs inside Settings, then views inside a People
 * workspace that also held learners and instructors. What is left is the part
 * that genuinely is one subject: an administrator account, the role that gives
 * it power, and the session that account is currently using. Answering "who can
 * do what" means all three at once, which is why they stayed together when the
 * two directories split off.
 *
 * Learners and instructors are *not* here, and are not the same subject: they
 * are platform accounts, these are staff accounts, and nothing on this page
 * grants anything on that side.
 *
 * Each section still enforces its own rule. The workspace only decides what to
 * offer; the server decides what to answer.
 */
const VIEWS: Record<
  AccessTab,
  { title: string; description: string; permission?: Permission; superOnly?: boolean }
> = {
  admins: {
    title: "Administrators",
    description: "Dashboard accounts, and the roles they hold.",
    superOnly: true,
  },
  roles: {
    title: "Roles & permissions",
    description: "What a role carries, and who may configure it.",
    superOnly: true,
  },
  sessions: {
    title: "Live sessions",
    description: "Who is signed in, and how to stop them being.",
    permission: PERMISSIONS.SETTINGS_MANAGE,
  },
};

function AccessWorkspace() {
  const { view: requested } = Route.useSearch();
  const { has, isReady } = usePermissions();
  const me = useAdminMeQuery();

  // Roles and administrators are super-admin only, which is not a permission
  // code — it is whether one of your roles is the super role.
  const isSuperAdmin = (me.data?.roles ?? []).some((r) => r.isSuper);

  // `beforeLoad` has already redirected the two moved views, so anything
  // reaching here is one of the three — or absent, on a bare /users.
  const active: AccessTab = TABS.includes(requested as AccessTab)
    ? (requested as AccessTab)
    : "admins";
  const view = VIEWS[active];

  // Each panel owns its own "add" sheet — it knows what it is creating — while
  // the button belongs up here beside the title. The panel hands its opener out
  // once on mount and this holds it.
  const openAdd = useRef<Partial<Record<AccessTab, () => void>>>({});
  const registerAdminAdd = useCallback((open: () => void) => {
    openAdd.current.admins = open;
  }, []);
  const registerRoleAdd = useCallback((open: () => void) => {
    openAdd.current.roles = open;
  }, []);

  const addAction: Partial<Record<AccessTab, { label: string; allowed: boolean }>> = {
    admins: { label: "Add administrator", allowed: isSuperAdmin },
    roles: { label: "Create role", allowed: isSuperAdmin },
  };
  const action = addAction[active];

  const allowed = view.superOnly ? isSuperAdmin : view.permission ? has(view.permission) : true;

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

      {!isReady || (me.isLoading && Boolean(view.superOnly)) ? (
        <div className="space-y-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-64 w-full rounded-xl" />
        </div>
      ) : !allowed ? (
        <NotAllowed
          {...(view.permission ? { permission: view.permission } : {})}
          superOnly={Boolean(view.superOnly)}
          isSuperAdmin={isSuperAdmin}
        />
      ) : (
        <>
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
