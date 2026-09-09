import { Outlet, createFileRoute, redirect } from "@tanstack/react-router";

import { SidebarProvider } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/dashboard/AppSidebar";
import { Topbar } from "@/components/dashboard/Topbar";
import { hasAdminSession } from "@/lib/auth";

/**
 * Everything behind a session.
 *
 * Pathless — the `_` prefix means it contributes no URL segment, so the routes
 * beneath it keep the paths they always had. What it contributes instead is the
 * two things every one of them shares: the requirement to be signed in, and the
 * sidebar-and-topbar chrome around the page.
 *
 * Both used to live in `__root` as a `pathname === "/login"` comparison, which
 * meant the root re-derived at render time what the route tree already knew,
 * and every new page inherited the arrangement by accident rather than by
 * placement. Here it is declared once, in the layout the pages sit under.
 */
export const Route = createFileRoute("/_authenticated")({
  /**
   * Client-rendered, and the guard below depends on it.
   *
   * The session lives in localStorage, which the server cannot read, so this
   * subtree has nothing useful to server-render anyway. But the real reason is
   * mechanical: the router skips `beforeLoad` on the client for any match it
   * already rendered on the server (`shouldSkipLoader` returns early when a
   * match is dehydrated). A guard that abstained during SSR — as this one used
   * to — therefore never ran at all on a direct hit to a dashboard URL, and
   * only appeared to work because clicking a link is a client-side navigation,
   * where it does run. Opting out of SSR keeps the match undehydrated, so the
   * guard runs in the browser, where it can see the token.
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
    if (hasAdminSession()) return;

    throw redirect({
      to: "/login",
      // Remember where they were headed so sign-in can finish the journey.
      // `href` is path and search only, which is what the sign-in page's
      // open-redirect guard accepts.
      search: { redirect: location.href },
    });
  },
  component: AuthenticatedLayout,
});

function AuthenticatedLayout() {
  return (
    <SidebarProvider>
      {/*
        Exactly one viewport tall, and the page itself never scrolls. With
        `min-h-screen` the row grew with its content, so the sidebar — however
        tall — scrolled off the top as soon as a page was longer than the
        window. Scrolling belongs to <main> alone; the sidebar and topbar stay
        put.
      */}
      <div className="flex h-svh w-full overflow-hidden bg-background text-foreground transition-colors duration-200">
        <AppSidebar />
        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <Topbar />
          <main className="min-w-0 flex-1 overflow-y-auto">
            {/* Required: nested routes render here. Removing <Outlet /> breaks all child routes. */}
            <Outlet />
          </main>
        </div>
      </div>
    </SidebarProvider>
  );
}
