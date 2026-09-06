import { useI18n } from "@/lib/i18n";

export function PlaceholderPage({
  title,
  description,
  pageKey,
}: {
  title: string;
  description: string;
  pageKey?: string;
}) {
  const { t } = useI18n();
  const displayTitle = pageKey ? t(`page.${pageKey}.title`) : title;
  const displayDescription = pageKey ? t(`page.${pageKey}.desc`) : description;

  return (
    <div className="p-4 sm:p-6">
      <div className="card-surface grid min-h-[60vh] place-items-center p-8 text-center transition-colors">
        <div className="max-w-sm">
          <h1 className="text-2xl font-extrabold tracking-tight">{displayTitle}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{displayDescription}</p>
        </div>
      </div>
    </div>
  );
}
