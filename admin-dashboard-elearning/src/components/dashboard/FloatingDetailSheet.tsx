"use client";

import * as React from "react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useI18n } from "@/lib/i18n";
import { cn } from "@/lib/utils";

export interface FloatingDetailSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: React.ReactNode;
  description?: React.ReactNode;
  badge?: React.ReactNode;
  children: React.ReactNode;
  footerActions?: React.ReactNode;
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
}

const sizeClasses = {
  sm: "sm:max-w-md",
  md: "sm:max-w-lg",
  lg: "sm:max-w-xl",
  xl: "sm:max-w-2xl",
};

/**
 * FloatingDetailSheet
 *
 * Core architectural primitive for entity inspection, detail previews,
 * and quick-edit forms, replacing centered modal dialogs.
 *
 * Automatically positions at the trailing edge based on text direction (LTR / RTL).
 */
export function FloatingDetailSheet({
  open,
  onOpenChange,
  title,
  description,
  badge,
  children,
  footerActions,
  size = "md",
  className,
}: FloatingDetailSheetProps) {
  const { dir } = useI18n();
  const side = dir === "rtl" ? "left" : "right";

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side={side}
        className={cn(
          "flex flex-col gap-0 p-0 sm:ring-1 sm:ring-border/40 overflow-hidden",
          sizeClasses[size],
          className,
        )}
      >
        <SheetHeader className="border-b px-6 py-4.5 bg-card/60 backdrop-blur-xs shrink-0 text-start">
          <div className="flex items-center gap-2 pr-6 rtl:pr-0 rtl:pl-6">
            <SheetTitle className="text-lg font-semibold tracking-tight">{title}</SheetTitle>
            {badge && <div className="shrink-0">{badge}</div>}
          </div>
          {description && (
            <SheetDescription className="text-xs text-muted-foreground mt-0.5">
              {description}
            </SheetDescription>
          )}
        </SheetHeader>

        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-6">{children}</div>

        {footerActions && (
          <SheetFooter className="border-t px-6 py-3.5 bg-card/40 backdrop-blur-xs shrink-0 flex items-center justify-end gap-2.5 sm:space-x-0">
            {footerActions}
          </SheetFooter>
        )}
      </SheetContent>
    </Sheet>
  );
}
