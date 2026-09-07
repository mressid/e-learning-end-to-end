import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  Outlet,
  Link,
  createRootRouteWithContext,
  redirect,
  useNavigate,
  useRouter,
  useRouterState,
  HeadContent,
  Scripts,
} from "@tanstack/react-router";
import { useEffect, type ReactNode } from "react";

import appCss from "../styles.css?url";
import { reportLovableError } from "../lib/lovable-error-reporting";
import { SidebarProvider } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/dashboard/AppSidebar";
import { Topbar } from "@/components/dashboard/Topbar";
import { ThemeProvider } from "@/lib/theme";
import { LanguageProvider } from "@/lib/i18n";
import { AUTH_EXPIRED_EVENT, getAccessToken, isAdminToken } from "@/api";

const LOGIN_PATH = "/login";

function NotFoundComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      <div className="max-w-md text-center">
        <h1 className="text-7xl font-bold text-foreground">404</h1>
        <h2 className="mt-4 text-xl font-semibold text-foreground">Page not found</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          The page you're looking for doesn't exist or has been moved.
        </p>
        <div className="mt-6">
          <Link
            to="/"
            className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
          >
            Go home
          </Link>
        </div>
      </div>
    </div>
  );
}

function ErrorComponent({ error, reset }: { error: Error; reset: () => void }) {
  console.error(error);
  const router = useRouter();
  useEffect(() => {
    reportLovableError(error, { boundary: "tanstack_root_error_component" });
  }, [error]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      <div className="max-w-md text-center">
        <h1 className="text-xl font-semibold tracking-tight text-foreground">
          This page didn't load
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Something went wrong on our end. You can try refreshing or head back home.
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <button
            onClick={() => {
              router.invalidate();
              reset();
            }}
            className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
          >
            Try again
          </button>
          <a
            href="/"
            className="inline-flex items-center justify-center rounded-md border border-input bg-background px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-accent"
          >
            Go home
          </a>
        </div>
      </div>
    </div>
  );
}

export const Route = createRootRouteWithContext<{ queryClient: QueryClient }>()({
  /**
   * Keep the dashboard behind a session.
   *
   * The token lives in localStorage, which does not exist while rendering on
   * the server — so this only decides anything in the browser. Guarding during
   * SSR would redirect every first paint to the sign-in page. The pages behind
   * it call an API that refuses anonymous requests regardless; this exists so
   * the user sees a sign-in form instead of a screen of failed queries.
   */
  beforeLoad: ({ location }) => {
    if (typeof window === "undefined") return;

    const token = getAccessToken();
    // A learner token here is not a session, whatever localStorage thinks: the
    // backend rejects `typ=user` on every /admin route.
    const signedIn = Boolean(token) && isAdminToken(token);
    const atLogin = location.pathname === LOGIN_PATH;

    if (!signedIn && !atLogin) {
      throw redirect({
        to: LOGIN_PATH,
        // Remember where they were headed so sign-in can finish the journey.
        search: { redirect: location.href },
      });
    }

    if (signedIn && atLogin) {
      throw redirect({ to: "/" });
    }
  },
  head: () => ({
    meta: [
      { charSet: "utf-8" },
      { name: "viewport", content: "width=device-width, initial-scale=1" },
      { title: "Lernova — Learning Platform" },
      {
        name: "description",
        content: "Analytics and course management for online learning teams.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
    links: [
      {
        rel: "stylesheet",
        href: appCss,
      },
      { rel: "preconnect", href: "https://fonts.googleapis.com" },
      { rel: "preconnect", href: "https://fonts.gstatic.com", crossOrigin: "anonymous" },
      {
        rel: "stylesheet",
        href: "https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap",
      },
      { rel: "icon", href: "/favicon.ico", type: "image/x-icon" },
    ],
  }),
  shellComponent: RootShell,
  component: RootComponent,
  notFoundComponent: NotFoundComponent,
  errorComponent: ErrorComponent,
});

function RootShell({ children }: { children: ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <HeadContent />
        <script
          dangerouslySetInnerHTML={{
            __html: `
              try {
                const theme = localStorage.getItem('lernova-theme') || 'system';
                const isDark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
                if (isDark) {
                  document.documentElement.classList.add('dark');
                  document.documentElement.style.colorScheme = 'dark';
                } else {
                  document.documentElement.classList.remove('dark');
                  document.documentElement.style.colorScheme = 'light';
                }
                const lang = localStorage.getItem('lernova-lang') || 'en';
                document.documentElement.lang = lang;
                if (lang === 'ar') {
                  document.documentElement.dir = 'rtl';
                }
              } catch (e) {}
            `,
          }}
        />
      </head>
      <body>
        {children}
        <Scripts />
      </body>
    </html>
  );
}

function RootComponent() {
  const { queryClient } = Route.useRouteContext();
  const navigate = useNavigate();
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const isAuth = pathname === LOGIN_PATH;

  // The API client raises this when a refresh fails, which can happen long after
  // the last navigation. Without it the user sits on a dashboard whose every
  // request is quietly 401ing.
  useEffect(() => {
    const onExpired = () => {
      if (window.location.pathname === LOGIN_PATH) return;
      // Path and query only. An absolute URL is rejected by the sign-in page's
      // open-redirect guard, which would quietly lose where the user was.
      const from = `${window.location.pathname}${window.location.search}`;
      navigate({ to: LOGIN_PATH, search: { redirect: from } });
    };
    window.addEventListener(AUTH_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired);
  }, [navigate]);

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <LanguageProvider>
          {isAuth ? (
            <main className="min-h-screen w-full bg-background text-foreground transition-colors duration-200">
              <Outlet />
            </main>
          ) : (
            <SidebarProvider>
              {/*
                Exactly one viewport tall, and the page itself never scrolls.
                With `min-h-screen` the row grew with its content, so the
                sidebar — however tall — scrolled off the top as soon as a page
                was longer than the window. Scrolling belongs to <main> alone;
                the sidebar and topbar stay put.
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
          )}
        </LanguageProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
