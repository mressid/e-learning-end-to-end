import { createFileRoute, Link, redirect, useNavigate } from "@tanstack/react-router";
import { useState, type FormEvent } from "react";
import {
  GraduationCap,
  Mail,
  Lock,
  Eye,
  EyeOff,
  Sun,
  Moon,
  Laptop,
  Languages,
  Check,
  ArrowRight,
  ShieldCheck,
  Clock3,
  Sparkles,
} from "lucide-react";
import { useI18n, languages, type Lang } from "@/lib/i18n";
import { useTheme } from "@/lib/theme";
import { useAuth } from "@/hooks/queries";
import { hasAdminSession } from "@/lib/auth";
import { parseApiError } from "@/api";
import { Checkbox } from "@/components/ui/checkbox";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export const Route = createFileRoute("/login")({
  // The `_authenticated` guard appends where the user was headed. Anything else
  // in the query string is ignored rather than trusted.
  validateSearch: (search: Record<string, unknown>): { redirect?: string } => {
    const target = search["redirect"];
    return typeof target === "string" ? { redirect: target } : {};
  },
  /**
   * Client-rendered, for the same mechanical reason as `_authenticated`: the
   * router does not re-run `beforeLoad` in the browser for a match it already
   * server-rendered, so a guard that abstained during SSR would be dead on
   * exactly the path that needs it — someone with a session opening /login
   * from a bookmark or the address bar. The cost is that the form is no longer
   * server-rendered; on a dashboard that is entirely behind a sign-in, that
   * buys nothing worth a guard that only pretends to work.
   */
  ssr: false,
  /** The mirror of the `_authenticated` guard: a session belongs on the dashboard. */
  beforeLoad: () => {
    if (hasAdminSession()) throw redirect({ to: "/" });
  },
  head: () => ({
    meta: [
      { title: "Sign in — Lernova" },
      { name: "description", content: "Sign in to access your e-learning dashboard and courses." },
      { property: "og:title", content: "Sign in — Lernova" },
      {
        property: "og:description",
        content: "Sign in to access your e-learning dashboard and courses.",
      },
    ],
  }),
  component: LoginPage,
});

function LoginPage() {
  const { t, lang, setLang, dir } = useI18n();
  const { theme, resolvedTheme, setTheme } = useTheme();
  const navigate = useNavigate();
  const { login, isLoggingIn } = useAuth();
  const { redirect } = Route.useSearch();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const isLoading = isLoggingIn;
  const currentLang = languages.find((l) => l.code === lang) || languages[0];

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMessage("Please enter both email and password.");
      return;
    }
    setErrorMessage("");

    try {
      await login({ email, password });
      // Only ever resume to somewhere in this app — an absolute URL from the
      // query string would be an open redirect.
      const target = redirect && redirect.startsWith("/") ? redirect : "/";
      navigate({ to: target });
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      setErrorMessage(apiErr.message || "Failed to sign in. Please verify your credentials.");
    }
  };

  // The super admin seeded by V6 for local work. Filled in on request rather
  // than prefilled, so a deployed build never ships credentials in its inputs.
  const handleQuickFill = () => {
    setEmail("admin@elearning.local");
    setPassword("change this password now");
    setErrorMessage("");
  };

  return (
    <main className="relative flex min-h-screen w-full flex-col bg-background text-foreground transition-colors duration-200 lg:grid lg:grid-cols-12">
      {/* Top Utility Bar: Theme and Language Controls */}
      <div
        className={`absolute top-4 z-20 flex items-center gap-2 ${
          dir === "rtl" ? "left-4" : "right-4"
        }`}
      >
        {/* Language Selector */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label={t("top.language")}
              className="flex h-9 items-center gap-1.5 rounded-full border bg-card px-3 text-xs font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
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
                onClick={() => setLang(l.code as Lang)}
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

        {/* Theme Selector */}
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
      </div>

      {/* Left Column: Sign In Form */}
      <div className="flex flex-1 flex-col justify-center px-4 py-12 sm:px-6 md:px-12 lg:col-span-6 lg:px-16 xl:col-span-5">
        <div className="mx-auto w-full max-w-md">
          {/* Brand Header */}
          <div className="flex items-center gap-2.5">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-primary text-primary-foreground shadow-sm">
              <GraduationCap className="h-5 w-5" />
            </span>
            <span className="text-xl font-extrabold tracking-tight">{t("brand.name")}</span>
          </div>

          <div className="mt-8">
            <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
              {t("auth.signInTitle")}
            </h1>
            <p className="mt-2 text-sm text-muted-foreground">{t("auth.signInSubtitle")}</p>
          </div>

          {/* Error Banner */}
          {errorMessage && (
            <div className="mt-4 rounded-xl border border-destructive/20 bg-destructive/10 p-3 text-sm text-destructive">
              {errorMessage}
            </div>
          )}

          {/* Quick Demo Fill Helper */}
          <div className="mt-6 flex items-center justify-between rounded-xl border border-border bg-card p-3">
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold">Demo credentials available</p>
              <p className="text-xs text-muted-foreground truncate">admin@lernova.edu</p>
            </div>
            <button
              type="button"
              onClick={handleQuickFill}
              className="inline-flex shrink-0 items-center gap-1 rounded-lg bg-secondary px-2.5 py-1 text-xs font-semibold text-secondary-foreground transition-colors hover:bg-secondary/80"
            >
              <Sparkles className="h-3 w-3" />
              {t("auth.demoAdmin")}
            </button>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label htmlFor="email" className="block text-xs font-semibold text-foreground">
                {t("auth.emailLabel")}
              </label>
              <div className="relative mt-1.5">
                <Mail
                  className={`pointer-events-none absolute top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground ${
                    dir === "rtl" ? "right-3" : "left-3"
                  }`}
                />
                <input
                  id="email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={t("auth.emailPlaceholder")}
                  className={`h-10 w-full rounded-xl border border-input bg-card text-sm text-foreground outline-none transition focus:border-ring focus:ring-1 focus:ring-ring ${
                    dir === "rtl" ? "pr-9 pl-3" : "pl-9 pr-3"
                  }`}
                />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between">
                <label htmlFor="password" className="block text-xs font-semibold text-foreground">
                  {t("auth.passwordLabel")}
                </label>
                <a
                  href="#forgot"
                  onClick={(e) => {
                    e.preventDefault();
                    alert("Password reset instructions will be sent to your registered email.");
                  }}
                  className="text-xs font-medium text-primary hover:underline"
                >
                  {t("auth.forgotPassword")}
                </a>
              </div>
              <div className="relative mt-1.5">
                <Lock
                  className={`pointer-events-none absolute top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground ${
                    dir === "rtl" ? "right-3" : "left-3"
                  }`}
                />
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={t("auth.passwordPlaceholder")}
                  className={`h-10 w-full rounded-xl border border-input bg-card text-sm text-foreground outline-none transition focus:border-ring focus:ring-1 focus:ring-ring ${
                    dir === "rtl" ? "pr-9 pl-10" : "pl-9 pr-10"
                  }`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className={`absolute top-1/2 -translate-y-1/2 z-10 flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground cursor-pointer ${
                    dir === "rtl" ? "left-2" : "right-2"
                  }`}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <div className="flex items-center gap-2 pt-1">
              <Checkbox
                id="remember"
                checked={rememberMe}
                onCheckedChange={(v) => setRememberMe(Boolean(v))}
              />
              <label
                htmlFor="remember"
                className="cursor-pointer select-none text-xs text-muted-foreground"
              >
                {t("auth.rememberMe")}
              </label>
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="mt-2 flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-primary text-sm font-semibold text-primary-foreground shadow-sm transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              {isLoading ? (
                <span>{t("auth.signingIn")}</span>
              ) : (
                <>
                  <span>{t("auth.signInButton")}</span>
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </form>

          {/* SSO Options */}
          <div className="mt-6">
            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-border" />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="bg-background px-2 text-muted-foreground">
                  {t("auth.orContinueWith")}
                </span>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => navigate({ to: "/" })}
                className="inline-flex h-9 items-center justify-center gap-2 rounded-xl border border-input bg-card text-xs font-semibold text-foreground transition-colors hover:bg-secondary"
              >
                <svg className="h-4 w-4" viewBox="0 0 24 24">
                  <path
                    fill="currentColor"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="currentColor"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="currentColor"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                  />
                  <path
                    fill="currentColor"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                  />
                </svg>
                Google
              </button>
              <button
                type="button"
                onClick={() => navigate({ to: "/" })}
                className="inline-flex h-9 items-center justify-center gap-2 rounded-xl border border-input bg-card text-xs font-semibold text-foreground transition-colors hover:bg-secondary"
              >
                <svg className="h-4 w-4" viewBox="0 0 24 24">
                  <path
                    fill="currentColor"
                    d="M11.4 24H0V12.6h11.4V24zM24 24H12.6V12.6H24V24zM11.4 11.4H0V0h11.4v11.4zm12.6 0H12.6V0H24v11.4z"
                  />
                </svg>
                Microsoft
              </button>
            </div>
          </div>

          <div className="mt-8 text-center text-xs text-muted-foreground">
            <span>{t("auth.dontHaveAccount")} </span>
            <span className="font-semibold text-foreground">{t("auth.contactAdmin")}</span>
          </div>
        </div>
      </div>

      {/* Right Column: Hero Showcase (Desktop only) */}
      <div className="relative hidden lg:col-span-6 xl:col-span-7 lg:flex flex-col justify-between overflow-hidden border-l border-border bg-secondary/30 p-12">
        <div className="absolute inset-0 bg-gradient-to-br from-primary/10 via-transparent to-transparent pointer-events-none" />

        {/* Top Header */}
        <div className="relative z-10 flex items-center justify-between">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-card/80 px-3 py-1 text-xs font-semibold backdrop-blur">
            <ShieldCheck className="h-3.5 w-3.5 text-primary" />
            Enterprise Grade Security
          </span>
          <Link
            to="/"
            className="text-xs font-semibold text-muted-foreground hover:text-foreground transition-colors"
          >
            Visit Public Catalog
          </Link>
        </div>

        {/* Central Hero Showcase */}
        <div className="relative z-10 my-auto max-w-lg space-y-6">
          <h2 className="text-3xl font-extrabold tracking-tight xl:text-4xl text-foreground leading-tight">
            {t("auth.heroTitle")}
          </h2>
          <p className="text-sm text-muted-foreground leading-relaxed">{t("auth.heroSubtitle")}</p>

          {/* Metric Badges */}
          <div className="grid grid-cols-3 gap-3 pt-2">
            <div className="rounded-2xl border border-border bg-card/80 p-4 backdrop-blur shadow-sm">
              <p className="text-xl font-extrabold tracking-tight text-primary">16,400+</p>
              <p className="text-xs text-muted-foreground mt-0.5">{t("auth.heroStat1")}</p>
            </div>
            <div className="rounded-2xl border border-border bg-card/80 p-4 backdrop-blur shadow-sm">
              <p className="text-xl font-extrabold tracking-tight text-success">94.2%</p>
              <p className="text-xs text-muted-foreground mt-0.5">{t("auth.heroStat2")}</p>
            </div>
            <div className="rounded-2xl border border-border bg-card/80 p-4 backdrop-blur shadow-sm">
              <p className="text-xl font-extrabold tracking-tight text-foreground">99.9%</p>
              <p className="text-xs text-muted-foreground mt-0.5">{t("auth.heroStat3")}</p>
            </div>
          </div>

          {/* Mini Live Preview Widget */}
          <div className="rounded-2xl border border-border bg-card/90 p-4 backdrop-blur shadow-sm space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="inline-flex items-center gap-1.5 text-xs font-bold text-foreground">
                <Clock3 className="h-3.5 w-3.5 text-primary" />
                Live Class Starting Now
              </span>
              <span className="rounded-full bg-success/15 px-2 py-0.5 text-2xs font-bold text-success uppercase tracking-wider">
                Online
              </span>
            </div>
            <p className="text-xs text-muted-foreground">
              Neural Networks and Deep Learning Architecture · Cohort 12 with Dr. Hana Ferjani
            </p>
          </div>
        </div>

        {/* Footer */}
        <div className="relative z-10 flex items-center justify-between text-xs text-muted-foreground">
          <span>Lernova Platform v2.4</span>
          <span>Terms of Service · Privacy Policy</span>
        </div>
      </div>
    </main>
  );
}
