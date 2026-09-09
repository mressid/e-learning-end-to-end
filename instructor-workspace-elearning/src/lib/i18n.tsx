import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export const languages = [
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
  { code: "es", label: "Español" },
  { code: "ar", label: "العربية" },
] as const;

export type Lang = (typeof languages)[number]["code"];

type Dict = Record<string, string>;

/*
 * The instructor workspace's own strings, not the dashboard's.
 *
 * This started as a copy of the admin dictionary — five hundred lines naming
 * pages that do not exist here: a dashboard, a student directory, certificates,
 * chart axis labels. Translating an app by carrying another app's vocabulary
 * around is how the two stop meaning anything, so what is left is what this app
 * actually says. It grows as the app does.
 *
 * `t` falls back to English and then to the key itself, so a missing
 * translation shows the English rather than a blank.
 */

const en: Dict = {
  "brand.name": "Lernova",
  "brand.tagline": "Instructor workspace",
  "nav.teaching": "Teaching",
  "nav.courses": "My courses",
  "top.theme": "Toggle theme",
  "top.language": "Language",
  "theme.light": "Light",
  "theme.dark": "Dark",
  "theme.system": "System",
  "auth.signInTitle": "Welcome back",
  "auth.signInSubtitle": "Sign in to the courses you teach.",
  "auth.emailLabel": "Email address",
  "auth.emailPlaceholder": "you@example.com",
  "auth.passwordLabel": "Password",
  "auth.passwordPlaceholder": "Enter your password",
  "auth.rememberMe": "Keep me signed in on this device",
  "auth.forgotPassword": "Forgotten it?",
  "auth.signInButton": "Sign in",
  "auth.signingIn": "Signing in...",
  "auth.orContinueWith": "Single sign-on",
  "auth.ssoUnavailable": "Not configured on this platform yet",
  "auth.dontHaveAccount": "Instructor accounts are created for you.",
  "auth.contactAdmin": "Ask an administrator",
  "auth.wrongApp":
    "This is the instructor workspace. Administrator accounts sign in to the admin dashboard instead.",
  "auth.badge": "Your courses, before anyone sees them",
  "auth.heroTitle": "Write, structure and publish the courses you teach.",
  "auth.heroSubtitle":
    "Everything you own or co-instruct in one place - including the drafts nobody else can see yet.",
  "auth.heroPoint1": "Drafts",
  "auth.heroStat1": "Private until you publish",
  "auth.heroPoint2": "Sections",
  "auth.heroStat2": "Structure a course your way",
  "auth.heroPoint3": "Co-teaching",
  "auth.heroStat3": "Courses you share with colleagues",
  "auth.previewTitle": "Publishing is reversible",
  "auth.previewBody":
    "A published course can be returned to draft at any time. Learners keep what they have already completed.",
};

const fr: Dict = {
  "brand.name": "Lernova",
  "brand.tagline": "Espace formateur",
  "nav.teaching": "Enseignement",
  "nav.courses": "Mes cours",
  "top.theme": "Changer de thème",
  "top.language": "Langue",
  "theme.light": "Clair",
  "theme.dark": "Sombre",
  "theme.system": "Système",
  "auth.signInTitle": "Bon retour",
  "auth.signInSubtitle": "Connectez-vous aux cours que vous enseignez.",
  "auth.emailLabel": "Adresse e-mail",
  "auth.emailPlaceholder": "vous@exemple.com",
  "auth.passwordLabel": "Mot de passe",
  "auth.passwordPlaceholder": "Saisissez votre mot de passe",
  "auth.rememberMe": "Rester connecté sur cet appareil",
  "auth.forgotPassword": "Oublié ?",
  "auth.signInButton": "Se connecter",
  "auth.signingIn": "Connexion...",
  "auth.orContinueWith": "Authentification unique",
  "auth.ssoUnavailable": "Pas encore configurée sur cette plateforme",
  "auth.dontHaveAccount": "Les comptes formateur sont créés pour vous.",
  "auth.contactAdmin": "Demandez à un administrateur",
  "auth.wrongApp":
    "Ceci est l'espace formateur. Les comptes administrateur se connectent au tableau de bord d'administration.",
  "auth.badge": "Vos cours, avant que quiconque les voie",
  "auth.heroTitle": "Rédigez, structurez et publiez les cours que vous enseignez.",
  "auth.heroSubtitle":
    "Tout ce que vous possédez ou co-encadrez au même endroit - y compris les brouillons que personne ne voit encore.",
  "auth.heroPoint1": "Brouillons",
  "auth.heroStat1": "Privés jusqu'à publication",
  "auth.heroPoint2": "Sections",
  "auth.heroStat2": "Structurez un cours à votre façon",
  "auth.heroPoint3": "Co-enseignement",
  "auth.heroStat3": "Les cours partagés avec vos collègues",
  "auth.previewTitle": "La publication est réversible",
  "auth.previewBody":
    "Un cours publié peut redevenir un brouillon à tout moment. Les apprenants conservent ce qu'ils ont déjà terminé.",
};

const es: Dict = {
  "brand.name": "Lernova",
  "brand.tagline": "Espacio del instructor",
  "nav.teaching": "Docencia",
  "nav.courses": "Mis cursos",
  "top.theme": "Cambiar tema",
  "top.language": "Idioma",
  "theme.light": "Claro",
  "theme.dark": "Oscuro",
  "theme.system": "Sistema",
  "auth.signInTitle": "Bienvenido de nuevo",
  "auth.signInSubtitle": "Inicia sesión en los cursos que impartes.",
  "auth.emailLabel": "Correo electrónico",
  "auth.emailPlaceholder": "tu@ejemplo.com",
  "auth.passwordLabel": "Contraseña",
  "auth.passwordPlaceholder": "Introduce tu contraseña",
  "auth.rememberMe": "Mantener la sesión en este dispositivo",
  "auth.forgotPassword": "¿La olvidaste?",
  "auth.signInButton": "Iniciar sesión",
  "auth.signingIn": "Iniciando sesión...",
  "auth.orContinueWith": "Inicio de sesión único",
  "auth.ssoUnavailable": "Aún no configurado en esta plataforma",
  "auth.dontHaveAccount": "Las cuentas de instructor se crean para ti.",
  "auth.contactAdmin": "Pide a un administrador",
  "auth.wrongApp":
    "Este es el espacio del instructor. Las cuentas de administrador inician sesión en el panel de administración.",
  "auth.badge": "Tus cursos, antes de que nadie los vea",
  "auth.heroTitle": "Escribe, estructura y publica los cursos que impartes.",
  "auth.heroSubtitle":
    "Todo lo que posees o co-impartes en un solo lugar, incluidos los borradores que nadie ve todavía.",
  "auth.heroPoint1": "Borradores",
  "auth.heroStat1": "Privados hasta que publiques",
  "auth.heroPoint2": "Secciones",
  "auth.heroStat2": "Estructura un curso a tu manera",
  "auth.heroPoint3": "Co-docencia",
  "auth.heroStat3": "Cursos que compartes con colegas",
  "auth.previewTitle": "Publicar es reversible",
  "auth.previewBody":
    "Un curso publicado puede volver a borrador en cualquier momento. Los estudiantes conservan lo que ya completaron.",
};

const ar: Dict = {
  "brand.name": "ليرنوفا",
  "brand.tagline": "مساحة المدرّب",
  "nav.teaching": "التدريس",
  "nav.courses": "دوراتي",
  "top.theme": "تبديل المظهر",
  "top.language": "اللغة",
  "theme.light": "فاتح",
  "theme.dark": "داكن",
  "theme.system": "النظام",
  "auth.signInTitle": "مرحبًا بعودتك",
  "auth.signInSubtitle": "سجّل الدخول إلى الدورات التي تدرّسها.",
  "auth.emailLabel": "البريد الإلكتروني",
  "auth.emailPlaceholder": "you@example.com",
  "auth.passwordLabel": "كلمة المرور",
  "auth.passwordPlaceholder": "أدخل كلمة المرور",
  "auth.rememberMe": "أبقني مسجّلاً على هذا الجهاز",
  "auth.forgotPassword": "نسيتها؟",
  "auth.signInButton": "تسجيل الدخول",
  "auth.signingIn": "جارٍ تسجيل الدخول...",
  "auth.orContinueWith": "الدخول الموحّد",
  "auth.ssoUnavailable": "غير مُفعّل على هذه المنصة بعد",
  "auth.dontHaveAccount": "حسابات المدرّبين تُنشأ لك.",
  "auth.contactAdmin": "اطلب من مسؤول",
  "auth.wrongApp": "هذه مساحة المدرّب. حسابات المسؤولين تسجّل الدخول من لوحة الإدارة.",
  "auth.badge": "دوراتك، قبل أن يراها أحد",
  "auth.heroTitle": "اكتب دوراتك ونظّمها وانشرها.",
  "auth.heroSubtitle":
    "كل ما تملكه أو تشارك في تدريسه في مكان واحد - بما في ذلك المسوّدات التي لم يرها أحد بعد.",
  "auth.heroPoint1": "المسوّدات",
  "auth.heroStat1": "خاصة حتى تنشرها",
  "auth.heroPoint2": "الأقسام",
  "auth.heroStat2": "نظّم الدورة كما تريد",
  "auth.heroPoint3": "التدريس المشترك",
  "auth.heroStat3": "دورات تشاركها مع زملائك",
  "auth.previewTitle": "النشر قابل للتراجع",
  "auth.previewBody":
    "يمكن إعادة أي دورة منشورة إلى مسوّدة في أي وقت، ويحتفظ المتعلّمون بما أنجزوه.",
};

const dicts: Record<Lang, Dict> = { en, fr, es, ar };

type Ctx = {
  lang: Lang;
  setLang: (l: Lang) => void;
  dir: "ltr" | "rtl";
  t: (key: string, vars?: Record<string, string | number>) => string;
};

const LanguageContext = createContext<Ctx | null>(null);
export const STORAGE_KEY = "lernova-lang";

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(() => {
    if (typeof window === "undefined") return "en";
    try {
      const saved = localStorage.getItem(STORAGE_KEY) as Lang | null;
      if (saved && saved in dicts) return saved;
    } catch {
      // Ignore storage errors in SSR or restricted environments
    }
    return "en";
  });

  const dir: "ltr" | "rtl" = lang === "ar" ? "rtl" : "ltr";

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = dir;
  }, [lang, dir]);

  const value = useMemo<Ctx>(
    () => ({
      lang,
      dir,
      setLang: (l: Lang) => {
        setLangState(l);
        try {
          localStorage.setItem(STORAGE_KEY, l);
        } catch {
          // Ignore storage errors in SSR or restricted environments
        }
      },
      t: (key, vars) => {
        let out = dicts[lang]?.[key] ?? en[key] ?? key;
        if (vars) {
          for (const [k, v] of Object.entries(vars)) out = out.replace(`{${k}}`, String(v));
        }
        return out;
      },
    }),
    [lang, dir],
  );

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useI18n() {
  const ctx = useContext(LanguageContext);
  if (!ctx) throw new Error("useI18n must be used within LanguageProvider");
  return ctx;
}
