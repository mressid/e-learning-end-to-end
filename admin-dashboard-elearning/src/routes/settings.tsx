import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { Check, Laptop, Moon, Sun, Globe, Bell, Sparkles, ShieldCheck } from "lucide-react";
import { useI18n, languages, type Lang } from "@/lib/i18n";
import { useTheme, type Theme } from "@/lib/theme";
import { Switch } from "@/components/ui/switch";

export const Route = createFileRoute("/settings")({
  head: () => ({
    meta: [
      { title: "Settings — Lernova" },
      { name: "description", content: "Platform preferences, dark mode, and multi-language." },
      { property: "og:title", content: "Settings — Lernova" },
      {
        property: "og:description",
        content: "Platform preferences, dark mode, and multi-language.",
      },
    ],
  }),
  component: SettingsPage,
});

function SettingsPage() {
  const { t, lang, setLang } = useI18n();
  const { theme, setTheme } = useTheme();

  const [emailAlerts, setEmailAlerts] = useState(true);
  const [weeklyDigest, setWeeklyDigest] = useState(true);
  const [liveAlerts, setLiveAlerts] = useState(false);

  const themeOptions: { value: Theme; labelKey: string; icon: typeof Sun; desc: string }[] = [
    { value: "light", labelKey: "theme.light", icon: Sun, desc: "Bright and clear contrast" },
    { value: "dark", labelKey: "theme.dark", icon: Moon, desc: "Easy on the eyes in low light" },
    {
      value: "system",
      labelKey: "theme.system",
      icon: Laptop,
      desc: "Matches your device setting",
    },
  ];

  return (
    <div className="space-y-6 p-4 sm:p-6 max-w-5xl mx-auto">
      <header>
        <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
          {t("page.settings.title")}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">{t("page.settings.desc")}</p>
      </header>

      {/* Appearance & Theme Section */}
      <section className="card-surface p-5 sm:p-6 space-y-4">
        <div className="flex items-center gap-2">
          <div className="grid h-8 w-8 place-items-center rounded-lg bg-primary/10 text-primary">
            <Sparkles className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-lg font-bold">{t("settings.appearance")}</h2>
            <p className="text-xs text-muted-foreground">{t("settings.appearanceDesc")}</p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2">
          {themeOptions.map((opt) => {
            const Icon = opt.icon;
            const isSelected = theme === opt.value;
            return (
              <button
                key={opt.value}
                type="button"
                onClick={() => setTheme(opt.value)}
                className={`flex flex-col items-start p-4 rounded-xl border text-left transition-all ${
                  isSelected
                    ? "border-primary bg-primary/5 ring-2 ring-primary/20 shadow-sm"
                    : "border-border bg-card hover:bg-secondary/60"
                }`}
              >
                <div className="flex w-full items-center justify-between">
                  <span
                    className={`grid h-8 w-8 place-items-center rounded-lg ${
                      isSelected
                        ? "bg-primary text-primary-foreground"
                        : "bg-muted text-muted-foreground"
                    }`}
                  >
                    <Icon className="h-4 w-4" />
                  </span>
                  {isSelected && (
                    <span className="grid h-5 w-5 place-items-center rounded-full bg-primary text-primary-foreground">
                      <Check className="h-3 w-3" />
                    </span>
                  )}
                </div>
                <p className="mt-3 font-semibold text-sm">{t(opt.labelKey)}</p>
                <p className="text-xs text-muted-foreground mt-0.5">{opt.desc}</p>
              </button>
            );
          })}
        </div>
      </section>

      {/* Language & Region Section */}
      <section className="card-surface p-5 sm:p-6 space-y-4">
        <div className="flex items-center gap-2">
          <div className="grid h-8 w-8 place-items-center rounded-lg bg-primary/10 text-primary">
            <Globe className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-lg font-bold">{t("settings.language")}</h2>
            <p className="text-xs text-muted-foreground">{t("settings.languageDesc")}</p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-2">
          {languages.map((l) => {
            const isSelected = lang === l.code;
            return (
              <button
                key={l.code}
                type="button"
                onClick={() => setLang(l.code as Lang)}
                className={`flex items-center justify-between p-3.5 rounded-xl border text-left transition-all ${
                  isSelected
                    ? "border-primary bg-primary/5 ring-2 ring-primary/20 shadow-sm"
                    : "border-border bg-card hover:bg-secondary/60"
                }`}
              >
                <div className="min-w-0">
                  <p
                    className={`text-sm font-semibold truncate ${isSelected ? "text-primary" : ""}`}
                  >
                    {l.label}
                  </p>
                  <p className="text-xs text-muted-foreground uppercase">{l.code}</p>
                </div>
                {isSelected && (
                  <span className="grid h-5 w-5 place-items-center rounded-full bg-primary text-primary-foreground shrink-0">
                    <Check className="h-3 w-3" />
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </section>

      {/* Notification Preferences */}
      <section className="card-surface p-5 sm:p-6 space-y-4">
        <div className="flex items-center gap-2">
          <div className="grid h-8 w-8 place-items-center rounded-lg bg-primary/10 text-primary">
            <Bell className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-lg font-bold">{t("settings.notifications")}</h2>
            <p className="text-xs text-muted-foreground">{t("settings.notificationsDesc")}</p>
          </div>
        </div>

        <div className="space-y-3 pt-2">
          <div className="flex items-center justify-between p-3 rounded-xl border border-border bg-card">
            <div>
              <p className="text-sm font-semibold">{t("settings.emailAlerts")}</p>
              <p className="text-xs text-muted-foreground">
                Instant notifications for course activity
              </p>
            </div>
            <Switch checked={emailAlerts} onCheckedChange={setEmailAlerts} />
          </div>

          <div className="flex items-center justify-between p-3 rounded-xl border border-border bg-card">
            <div>
              <p className="text-sm font-semibold">{t("settings.weeklySummary")}</p>
              <p className="text-xs text-muted-foreground">
                Weekly roll-up of enrollment and completion rates
              </p>
            </div>
            <Switch checked={weeklyDigest} onCheckedChange={setWeeklyDigest} />
          </div>

          <div className="flex items-center justify-between p-3 rounded-xl border border-border bg-card">
            <div>
              <p className="text-sm font-semibold">{t("settings.liveAlerts")}</p>
              <p className="text-xs text-muted-foreground">Reminders 15 minutes before workshops</p>
            </div>
            <Switch checked={liveAlerts} onCheckedChange={setLiveAlerts} />
          </div>
        </div>
      </section>

      {/* Platform Information */}
      <div className="flex items-center justify-between text-xs text-muted-foreground px-2">
        <span className="inline-flex items-center gap-1">
          <ShieldCheck className="h-3.5 w-3.5 text-success" /> Lernova Platform v2.4
        </span>
        <span>{t("settings.saved")}</span>
      </div>
    </div>
  );
}
