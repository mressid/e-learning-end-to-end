import { Link } from "@tanstack/react-router";
import { Bell, Check, Laptop, Moon, Search, Sun, Languages } from "lucide-react";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { useTheme } from "@/lib/theme";
import { useI18n, languages } from "@/lib/i18n";
import { useAdminMeQuery } from "@/hooks/queries";
import { Skeleton } from "@/components/ui/skeleton";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

function ProfileBadge() {
  const { data: admin, isLoading } = useAdminMeQuery();

  if (isLoading || !admin) {
    return <Skeleton className="h-9 w-9 rounded-full" />;
  }

  const name = admin.username || admin.email || "Administrator";

  return (
    <Link
      to="/settings"
      title={admin.email}
      aria-label={`Signed in as ${name}`}
      className="grid h-9 w-9 place-items-center rounded-full bg-primary/15 text-xs font-bold text-primary ring-1 ring-border transition-transform hover:scale-105 hover:ring-primary active:scale-95"
    >
      {name.slice(0, 2).toUpperCase()}
    </Link>
  );
}

export function Topbar() {
  const { theme, resolvedTheme, setTheme } = useTheme();
  const { lang, setLang, t, dir } = useI18n();

  const currentLang = languages.find((l) => l.code === lang) || languages[0];

  return (
    <header className="sticky top-0 z-30 grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 border-b bg-card/80 px-3 py-3 backdrop-blur sm:px-6">
      <SidebarTrigger className="shrink-0" />

      <div className="relative min-w-0">
        <Search
          className={`pointer-events-none absolute top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground ${
            dir === "rtl" ? "right-3" : "left-3"
          }`}
        />
        <input
          type="search"
          placeholder={t("top.search")}
          className={`h-10 w-full min-w-0 rounded-xl border bg-secondary/60 text-sm outline-none transition focus:border-ring focus:bg-card lg:max-w-md ${
            dir === "rtl" ? "pr-9 pl-3" : "pl-9 pr-3"
          }`}
        />
      </div>

      <div className="flex shrink-0 items-center gap-2">
        {/* Language Switcher */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label={t("top.language")}
              className="flex h-9 items-center gap-1.5 rounded-full border bg-card px-2.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
            >
              <Languages className="h-4 w-4 shrink-0" />
              <span className="hidden sm:inline font-medium">{currentLang.label}</span>
              <span className="sm:hidden font-semibold uppercase">{currentLang.code}</span>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align={dir === "rtl" ? "start" : "end"} className="w-36">
            <DropdownMenuLabel className="text-xs text-muted-foreground">
              {t("top.language")}
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            {languages.map((l) => (
              <DropdownMenuItem
                key={l.code}
                onClick={() => setLang(l.code)}
                className="flex cursor-pointer items-center justify-between"
              >
                <span className={`text-sm ${lang === l.code ? "font-semibold text-primary" : ""}`}>
                  {l.label}
                </span>
                {lang === l.code && <Check className="h-4 w-4 text-primary" />}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>

        {/* Theme Switcher */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label={t("top.theme")}
              className="grid h-9 w-9 place-items-center rounded-full border bg-card text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
            >
              {resolvedTheme === "dark" ? (
                <Moon className="h-4 w-4 text-primary" />
              ) : (
                <Sun className="h-4 w-4 text-amber-500" />
              )}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align={dir === "rtl" ? "start" : "end"} className="w-36">
            <DropdownMenuLabel className="text-xs text-muted-foreground">
              {t("top.theme")}
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => setTheme("light")}
              className="flex cursor-pointer items-center justify-between"
            >
              <span className="flex items-center gap-2">
                <Sun className="h-4 w-4 text-amber-500" />
                <span>{t("theme.light")}</span>
              </span>
              {theme === "light" && <Check className="h-4 w-4 text-primary" />}
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => setTheme("dark")}
              className="flex cursor-pointer items-center justify-between"
            >
              <span className="flex items-center gap-2">
                <Moon className="h-4 w-4 text-primary" />
                <span>{t("theme.dark")}</span>
              </span>
              {theme === "dark" && <Check className="h-4 w-4 text-primary" />}
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => setTheme("system")}
              className="flex cursor-pointer items-center justify-between"
            >
              <span className="flex items-center gap-2">
                <Laptop className="h-4 w-4 text-muted-foreground" />
                <span>{t("theme.system")}</span>
              </span>
              {theme === "system" && <Check className="h-4 w-4 text-primary" />}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        {/* Notifications */}
        <button
          type="button"
          aria-label={t("top.notifications")}
          className="relative grid h-9 w-9 place-items-center rounded-full border bg-card text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
        >
          <Bell className="h-4 w-4" />
          <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-destructive" />
        </button>

        {/* Profile. Was a stock portrait linking to /login — which the route
            guard now bounces straight back, since reaching this bar at all
            means you are already signed in. */}
        <ProfileBadge />
      </div>
    </header>
  );
}
