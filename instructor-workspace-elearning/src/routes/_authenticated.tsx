import { Outlet, createFileRoute, redirect } from "@tanstack/react-router";

import { SidebarProvider } from "@/components/ui/sidebar";
import { WorkspaceSidebar } from "@/components/workspace/WorkspaceSidebar";
import { WorkspaceTopbar } from "@/components/workspace/WorkspaceTopbar";
import { hasUserSession } from "@/lib/auth";

/**
 * Everything behind a session.
 *
 * Pathless — the `_` prefix means it contributes no URL segment, so the routes
 * beneath it keep the paths they have. What it contributes instead is the two
 * things every one of them shares: the requirement to be signed in, and the
 * chrome around the page.
 */
export const Route = createFileRoute("/_authenticated")({
  /**
   * Client-rendered, and the guard below depends on it.
   *
   * The session lives in localStorage, which the server cannot read, so this
   * subtree has nothing useful to server-render anyway. But the real reason is
   * mechanical: the router skips `beforeLoad` on the client for any match it
   * already rendered on the server, so a guard that abstained during SSR would
   * never run at all on a direct hit to a workspace URL — it would only appear
   * to work when clicking a link, which is a client-side navigation. Opting out
   * of SSR keeps the match undehydrated, so the guard runs in the browser where
   * it can see the token.
   *
   * Do not put SSR back without moving the check somewhere React executes.
   */
  ssr: false,
  /**
   * The pages behind this call an API that refuses anonymous requests
   * regardless; the guard exists so the user meets a sign-in form instead of a
   * screen of failed queries.
   */
  beforeLoad: ({ location }) => {
    if (hasUserSession()) return;

    throw redirect({
      to: "/login",
      // Remember where they were headed so sign-in can finish the journey.
      // `href` is path and search only, which is what the sign-in page's
      // open-redirect guard accepts.
      search: { redirect: location.href },
    });
  },
  component: WorkspaceLayout,
});

function WorkspaceLayout() {
  return (
    <SidebarProvider>
      {/*
        Exactly one viewport tall, and the page itself never scrolls. Scrolling
        belongs to <main> alone; the sidebar and topbar stay put.
      */}
      <div className="flex h-svh w-full overflow-hidden bg-background text-foreground transition-colors duration-200">
        <WorkspaceSidebar />
        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <WorkspaceTopbar />
          <main className="min-w-0 flex-1 overflow-y-auto">
            {/* Required: nested routes render here. Removing <Outlet /> breaks all child routes. */}
            <Outlet />
          </main>
        </div>
      </div>
    </SidebarProvider>
  );
}
