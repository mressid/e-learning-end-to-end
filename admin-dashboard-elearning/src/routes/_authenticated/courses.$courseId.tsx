import { useState, useEffect } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import {
  ArrowLeft,
  BookOpen,
  Plus,
  Layers,
  FileText,
  HelpCircle,
  FileCheck,
  CheckCircle2,
  Clock3,
  Archive,
  RefreshCw,
  FolderPlus,
  ChevronRight,
  GraduationCap,
  Star,
  Calendar,
  Save,
  LayoutDashboard,
  Globe,
  ExternalLink,
} from "lucide-react";
import { useI18n } from "@/lib/i18n";
import {
  useCourseQuery,
  useSectionsQuery,
  useCreateSectionMutation,
  useItemsQuery,
  useCreateItemMutation,
  useLessonQuery,
  useSaveLessonMutation,
  usePublishCourseMutation,
  useUnpublishCourseMutation,
  useArchiveCourseMutation,
  useCourseInstructorsQuery,
  useCourseReviewSummaryQuery,
} from "@/hooks/queries";
import { FloatingDetailSheet } from "@/components/dashboard/FloatingDetailSheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { parseApiError, type CourseItemResponse } from "@/api";

export interface CourseWorkspaceSearch {
  tab?: "overview" | "curriculum" | "instructors" | "reviews" | undefined;
  item?: string | undefined;
  itemTitle?: string | undefined;
  section?: string | undefined;
}

export const Route = createFileRoute("/_authenticated/courses/$courseId")({
  validateSearch: (rawSearch: Record<string, unknown>): CourseWorkspaceSearch => {
    const rawTab = rawSearch["tab"];
    const tab =
      rawTab === "curriculum" ||
      rawTab === "instructors" ||
      rawTab === "reviews" ||
      rawTab === "overview"
        ? rawTab
        : "overview";

    return {
      tab,
      ...(typeof rawSearch["item"] === "string" ? { item: rawSearch["item"] } : {}),
      ...(typeof rawSearch["itemTitle"] === "string" ? { itemTitle: rawSearch["itemTitle"] } : {}),
      ...(typeof rawSearch["section"] === "string" ? { section: rawSearch["section"] } : {}),
    };
  },
  head: () => ({
    meta: [
      { title: "Course Workspace — Lernova" },
      { name: "description", content: "Manage curriculum, lessons, sections, and assessments." },
    ],
  }),
  component: CourseWorkspacePage,
});

function SectionItemsList({
  sectionId,
  onSelectItem,
}: {
  sectionId: string;
  onSelectItem: (item: CourseItemResponse) => void;
}) {
  const { data: items, isLoading } = useItemsQuery(sectionId);

  if (isLoading) {
    return (
      <div className="space-y-2 py-2">
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
      </div>
    );
  }

  if (!items || items.length === 0) {
    return (
      <p className="text-xs text-muted-foreground italic py-3 text-center border rounded-lg border-dashed bg-background/50">
        No lessons or assessments yet in this section.
      </p>
    );
  }

  return (
    <div className="space-y-1.5 pt-1">
      {items.map((item, idx) => {
        const isLesson = item.type === "LESSON";
        const isQuiz = item.type === "QUIZ";
        const Icon = isLesson ? FileText : isQuiz ? HelpCircle : FileCheck;

        return (
          <div
            key={item.id}
            onClick={() => onSelectItem(item)}
            className="flex items-center justify-between rounded-lg border bg-card px-3.5 py-2.5 text-xs transition-colors hover:border-primary/40 hover:bg-secondary/40 cursor-pointer"
          >
            <div className="flex items-center gap-2.5">
              <span className="flex h-5 w-5 items-center justify-center rounded-md bg-secondary text-[11px] font-semibold text-muted-foreground">
                {idx + 1}
              </span>
              <Icon className="h-4 w-4 text-primary shrink-0" />
              <span className="font-medium text-foreground">{item.title}</span>
            </div>

            <div className="flex items-center gap-2">
              <Badge variant="outline" className="text-[10px] uppercase font-medium">
                {item.type}
              </Badge>
              {item.isRequired && (
                <Badge variant="secondary" className="text-[10px]">
                  Required
                </Badge>
              )}
              <ChevronRight className="h-3.5 w-3.5 text-muted-foreground" />
            </div>
          </div>
        );
      })}
    </div>
  );
}

function LessonContentInspector({
  itemId,
  itemTitle,
  open,
  onOpenChange,
}: {
  itemId: string;
  itemTitle: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { data: lesson, isLoading } = useLessonQuery(itemId);
  const saveLessonMutation = useSaveLessonMutation(itemId);

  const [contentBody, setContentBody] = useState("");
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");

  // Only a lesson written in place can be edited from here. One backed by an
  // uploaded file or a link is a resource with a file behind it, and replacing
  // that is authoring work: it belongs in the instructor workspace, which has
  // the upload flow and the editor to do it with.
  const isWritten = lesson?.sourceType === "INLINE";

  useEffect(() => {
    if (lesson && lesson.courseItemId === itemId) {
      setContentBody(lesson.content || "");
    }
  }, [lesson, itemId]);

  const handleSave = async () => {
    setErrorMsg("");
    setSaveSuccess(false);

    try {
      await saveLessonMutation.mutateAsync({
        title: itemTitle,
        resourceType: "DOCUMENT",
        sourceType: "INLINE",
        content: contentBody,
        // Kept as it was written rather than assumed: the format travels with
        // the text now.
        contentFormat:
          (lesson?.contentFormat as "MARKDOWN" | "HTML" | "PLAIN_TEXT" | undefined) ?? "MARKDOWN",
        completionRule:
          (lesson?.completionRule as "MANUAL" | "VIEW" | "DURATION" | undefined) ?? "MANUAL",
      });
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2500);
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      setErrorMsg(apiErr.message || "Failed to save lesson content.");
    }
  };

  return (
    <FloatingDetailSheet
      open={open}
      onOpenChange={onOpenChange}
      title={itemTitle}
      description="Edit the text of a written lesson. Files and links are authored in the instructor workspace."
      size="lg"
      footerActions={
        <div className="flex items-center gap-2">
          {saveSuccess && (
            <span className="text-xs text-success font-medium flex items-center gap-1">
              <CheckCircle2 className="h-3.5 w-3.5" />
              Saved successfully
            </span>
          )}
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          <Button
            size="sm"
            onClick={handleSave}
            disabled={saveLessonMutation.isPending || (Boolean(lesson) && !isWritten)}
            className="gap-1.5"
          >
            <Save className="h-3.5 w-3.5" />
            <span>{saveLessonMutation.isPending ? "Saving..." : "Save Content"}</span>
          </Button>
        </div>
      }
    >
      {isLoading ? (
        <div className="space-y-3">
          <Skeleton className="h-6 w-1/3" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : (
        <div className="space-y-4">
          {errorMsg && (
            <div className="rounded-lg bg-destructive/10 p-3 text-xs font-medium text-destructive">
              {errorMsg}
            </div>
          )}

          {lesson && !isWritten ? (
            <div className="rounded-lg border border-dashed p-6 text-center">
              <p className="text-sm font-semibold text-foreground">
                This lesson is {lesson.sourceType === "URL" ? "a link" : "an uploaded file"}.
              </p>
              <p className="mt-1.5 text-xs text-muted-foreground">
                {lesson.url ??
                  "Replacing it means uploading another file, which the instructor workspace does."}
              </p>
            </div>
          ) : (
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-foreground">
                Lesson body ({(lesson?.contentFormat ?? "MARKDOWN").toLowerCase().replace("_", " ")}
                )
              </label>
              <textarea
                rows={12}
                value={contentBody}
                onChange={(e) => setContentBody(e.target.value)}
                placeholder="Write the article or lesson notes here..."
                className="w-full font-mono rounded-md border border-input bg-card p-3 text-xs text-foreground outline-none transition focus:border-ring focus:ring-1 focus:ring-ring"
              />
            </div>
          )}
        </div>
      )}
    </FloatingDetailSheet>
  );
}

function CourseWorkspacePage() {
  const { t } = useI18n();
  const { courseId } = Route.useParams();
  const search = Route.useSearch();
  const navigate = useNavigate();
  const activeTab = search.tab || "overview";

  const { data: course, isLoading, isError, refetch } = useCourseQuery(courseId);
  const { data: sections, refetch: refetchSections } = useSectionsQuery(courseId);
  const { data: instructors } = useCourseInstructorsQuery(courseId);
  const { data: reviewSummary } = useCourseReviewSummaryQuery(courseId);

  // Mutations
  const publishMutation = usePublishCourseMutation();
  const unpublishMutation = useUnpublishCourseMutation();
  const archiveMutation = useArchiveCourseMutation();
  const createSectionMutation = useCreateSectionMutation(courseId);

  // New Section Form State
  const [newSectionTitle, setNewSectionTitle] = useState("");
  const [sectionError, setSectionError] = useState("");

  // New Item Drawer State
  const [activeSectionIdForItem, setActiveSectionIdForItem] = useState<string | null>(null);
  const [newItemTitle, setNewItemTitle] = useState("");
  const [newItemType, setNewItemType] = useState<"LESSON" | "QUIZ" | "ASSIGNMENT">("LESSON");
  const [itemError, setItemError] = useState("");
  const createItemMutation = useCreateItemMutation(activeSectionIdForItem || "");

  // Inspect Item Drawer State
  const [inspectedItem, setInspectedItem] = useState<CourseItemResponse | null>(null);
  const activeItemId = search.item || inspectedItem?.id || null;

  const handleTabChange = (val: string) => {
    navigate({
      to: "/courses/$courseId",
      params: { courseId },
      search: (prev) => ({
        ...prev,
        tab: val as CourseWorkspaceSearch["tab"],
      }),
    });
  };

  const handleSelectItem = (item: CourseItemResponse) => {
    setInspectedItem(item);
    navigate({
      to: "/courses/$courseId",
      params: { courseId },
      search: (prev) => ({
        ...prev,
        tab: "curriculum",
        item: item.id,
      }),
    });
  };

  const handleCloseInspector = (open: boolean) => {
    if (!open) {
      setInspectedItem(null);
      navigate({
        to: "/courses/$courseId",
        params: { courseId },
        search: (prev) => ({
          ...prev,
          item: undefined,
        }),
      });
    }
  };

  const handleAddSection = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSectionTitle.trim()) return;
    setSectionError("");

    try {
      await createSectionMutation.mutateAsync({ title: newSectionTitle.trim() });
      setNewSectionTitle("");
      refetchSections();
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      setSectionError(apiErr.message || "Failed to add section.");
    }
  };

  const handleCreateItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newItemTitle.trim() || !activeSectionIdForItem) return;
    setItemError("");

    try {
      await createItemMutation.mutateAsync({
        title: newItemTitle.trim(),
        type: newItemType,
      });
      setActiveSectionIdForItem(null);
      setNewItemTitle("");
      refetchSections();
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      setItemError(apiErr.message || "Failed to add item to section.");
    }
  };

  const handlePublish = async () => {
    try {
      await publishMutation.mutateAsync(courseId);
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      alert(apiErr.message || "Failed to publish course.");
    }
  };

  const handleUnpublish = async () => {
    try {
      await unpublishMutation.mutateAsync(courseId);
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      alert(apiErr.message || "Failed to unpublish course.");
    }
  };

  const handleArchive = async () => {
    try {
      await archiveMutation.mutateAsync(courseId);
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      alert(apiErr.message || "Failed to archive course.");
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6 p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError || !course) {
    return (
      <div className="p-8 text-center space-y-4">
        <p className="text-sm font-semibold text-destructive">
          Course not found or failed to load.
        </p>
        <Link
          to="/courses"
          className="inline-flex items-center gap-1.5 text-xs text-primary font-medium"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>Return to Courses Catalog</span>
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      {/* Top Breadcrumb & Return Action */}
      <div className="flex items-center justify-between">
        <Link
          to="/courses"
          className="inline-flex items-center gap-2 text-xs font-semibold text-muted-foreground hover:text-foreground transition-colors group"
        >
          <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-1 rtl:group-hover:translate-x-1" />
          <span>Return to Courses</span>
        </Link>

        <div className="flex items-center gap-2">
          {course.status === "DRAFT" && (
            <Button
              size="sm"
              onClick={handlePublish}
              disabled={publishMutation.isPending}
              className="gap-1.5"
            >
              <CheckCircle2 className="h-4 w-4" />
              <span>Publish Course</span>
            </Button>
          )}
          {course.status === "PUBLISHED" && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleUnpublish}
              disabled={unpublishMutation.isPending}
              className="gap-1.5"
            >
              <Clock3 className="h-4 w-4" />
              <span>Unpublish</span>
            </Button>
          )}
          {course.status !== "ARCHIVED" && (
            <Button
              variant="secondary"
              size="sm"
              onClick={handleArchive}
              disabled={archiveMutation.isPending}
              className="gap-1.5"
            >
              <Archive className="h-4 w-4" />
              <span>Archive</span>
            </Button>
          )}
        </div>
      </div>

      {/* Course Context Header Card */}
      {activeTab === "overview" && (
        <Card className="border-border/60 shadow-xs">
          <CardContent className="p-6 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
              <div className="space-y-1.5">
                <div className="flex items-center gap-2.5">
                  <Badge
                    variant={
                      course.status === "PUBLISHED"
                        ? "default"
                        : course.status === "DRAFT"
                          ? "secondary"
                          : "outline"
                    }
                    className="text-xs uppercase tracking-wide"
                  >
                    {course.status}
                  </Badge>
                  {course.level && (
                    <Badge variant="outline" className="text-xs">
                      {course.level}
                    </Badge>
                  )}
                  <span className="text-xs text-muted-foreground uppercase font-mono">
                    {course.language || "en"}
                  </span>
                </div>

                <h1 className="text-2xl font-bold tracking-tight text-foreground">
                  {course.title}
                </h1>
                <p className="text-sm text-muted-foreground max-w-3xl">
                  {course.shortDescription || course.description || "No description provided."}
                </p>
              </div>

              <div className="flex sm:flex-col items-end gap-2 shrink-0 text-xs text-muted-foreground">
                <span className="flex items-center gap-1">
                  <Calendar className="h-3.5 w-3.5" />
                  Created {course.createdAt ? new Date(course.createdAt).toLocaleDateString() : "-"}
                </span>
                {course.publishedAt && (
                  <span className="flex items-center gap-1 text-primary">
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    Published {new Date(course.publishedAt).toLocaleDateString()}
                  </span>
                )}
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Active Workspace View Header (Driven by Sidebar Navigation) */}
      <div className="flex items-center justify-between border-b pb-3">
        <div>
          <h2 className="text-base font-bold text-foreground">
            {activeTab === "overview"
              ? "Course Overview"
              : activeTab === "curriculum"
                ? "Curriculum & Lessons"
                : activeTab === "instructors"
                  ? "Assigned Instructors"
                  : "Reviews & Ratings"}
          </h2>
          <p className="text-xs text-muted-foreground">
            {activeTab === "overview"
              ? "Summary, syllabus, system metadata, and publication checklist."
              : activeTab === "curriculum"
                ? "Manage chapters, lessons, quizzes, and project assessments."
                : activeTab === "instructors"
                  ? "Instructors and faculty members assigned to this course."
                  : "Student reviews, ratings, and course satisfaction metrics."}
          </p>
        </div>

        {activeTab === "curriculum" && (
          <Button
            size="sm"
            onClick={() => setActiveSectionIdForItem(sections?.[0]?.id || null)}
            disabled={!sections || sections.length === 0}
            className="h-8 gap-1.5 text-xs font-semibold"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>Add Item</span>
          </Button>
        )}
      </div>

      {/* Course Management Views */}
      <Tabs value={activeTab} onValueChange={handleTabChange} className="space-y-4">
        {/* Tab 0: Overview */}
        <TabsContent value="overview" className="space-y-6">
          {/* Key Metrics Grid */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div className="rounded-xl border border-border/60 bg-card p-4 space-y-1">
              <p className="text-xs text-muted-foreground font-medium">Curriculum Sections</p>
              <p className="text-2xl font-bold tracking-tight text-foreground">
                {sections?.length || 0}
              </p>
              <p className="text-[11px] text-muted-foreground">Organized chapters</p>
            </div>
            <div className="rounded-xl border border-border/60 bg-card p-4 space-y-1">
              <p className="text-xs text-muted-foreground font-medium">Instructors Assigned</p>
              <p className="text-2xl font-bold tracking-tight text-foreground">
                {instructors?.length || 0}
              </p>
              <p className="text-[11px] text-muted-foreground">Teaching team</p>
            </div>
            <div className="rounded-xl border border-border/60 bg-card p-4 space-y-1">
              <p className="text-xs text-muted-foreground font-medium">Audience Level</p>
              <p className="text-2xl font-bold tracking-tight text-foreground">
                {course.level || "ALL"}
              </p>
              <p className="text-[11px] text-muted-foreground">Skill prerequisite</p>
            </div>
            <div className="rounded-xl border border-border/60 bg-card p-4 space-y-1">
              <p className="text-xs text-muted-foreground font-medium">Average Rating</p>
              <p className="text-2xl font-bold tracking-tight text-foreground">
                {reviewSummary?.average != null ? reviewSummary.average.toFixed(1) : "N/A"}
              </p>
              <p className="text-[11px] text-muted-foreground">
                {reviewSummary?.total || 0} student reviews
              </p>
            </div>
          </div>

          {/* Description & Syllabus Overview */}
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div className="space-y-4 lg:col-span-2">
              <Card className="border-border/60">
                <CardHeader className="py-3 px-4.5 border-b bg-secondary/10">
                  <CardTitle className="text-sm font-semibold text-foreground">
                    Course Syllabus & Description
                  </CardTitle>
                </CardHeader>
                <CardContent className="p-4 space-y-3 text-xs leading-relaxed text-foreground">
                  {course.shortDescription && (
                    <div className="rounded-lg bg-secondary/30 p-3 text-xs font-medium text-foreground border border-border/40">
                      {course.shortDescription}
                    </div>
                  )}
                  <p className="whitespace-pre-line text-muted-foreground">
                    {course.description || "No full description provided yet for this course."}
                  </p>
                </CardContent>
              </Card>

              {/* Sections Quick Summary */}
              <Card className="border-border/60">
                <CardHeader className="py-3 px-4.5 border-b bg-secondary/10 flex flex-row items-center justify-between space-y-0">
                  <CardTitle className="text-sm font-semibold text-foreground">
                    Curriculum Structure ({sections?.length || 0} sections)
                  </CardTitle>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleTabChange("curriculum")}
                    className="h-7 text-xs gap-1"
                  >
                    <span>Manage Curriculum</span>
                    <ExternalLink className="h-3 w-3" />
                  </Button>
                </CardHeader>
                <CardContent className="p-4">
                  {sections && sections.length > 0 ? (
                    <div className="space-y-2">
                      {sections.map((sec, idx) => (
                        <div
                          key={sec.id}
                          className="flex items-center justify-between rounded-lg border border-border/60 bg-card p-3 text-xs"
                        >
                          <div className="flex items-center gap-2.5">
                            <span className="flex h-5 w-5 items-center justify-center rounded bg-primary/10 text-primary font-bold text-[11px]">
                              {idx + 1}
                            </span>
                            <span className="font-semibold text-foreground">{sec.title}</span>
                          </div>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => handleTabChange("curriculum")}
                            className="h-6 text-xs text-primary hover:text-primary hover:bg-primary/10"
                          >
                            View Items
                          </Button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground italic py-3 text-center border rounded-lg border-dashed">
                      No curriculum sections have been added yet.
                    </p>
                  )}
                </CardContent>
              </Card>
            </div>

            {/* Sidebar Cards in Overview: Checklist & Metadata */}
            <div className="space-y-4">
              <Card className="border-border/60">
                <CardHeader className="py-3 px-4.5 border-b bg-secondary/10">
                  <CardTitle className="text-sm font-semibold text-foreground">
                    Publication Readiness
                  </CardTitle>
                </CardHeader>
                <CardContent className="p-4 space-y-3 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Title configured</span>
                    <CheckCircle2 className="h-4 w-4 text-success" />
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Description provided</span>
                    {course.description ? (
                      <CheckCircle2 className="h-4 w-4 text-success" />
                    ) : (
                      <Clock3 className="h-4 w-4 text-warning" />
                    )}
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Curriculum sections</span>
                    {sections && sections.length > 0 ? (
                      <CheckCircle2 className="h-4 w-4 text-success" />
                    ) : (
                      <Clock3 className="h-4 w-4 text-warning" />
                    )}
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Lead Instructor assigned</span>
                    {instructors && instructors.length > 0 ? (
                      <CheckCircle2 className="h-4 w-4 text-success" />
                    ) : (
                      <Clock3 className="h-4 w-4 text-muted-foreground" />
                    )}
                  </div>

                  <div className="pt-2 border-t border-border/60 flex items-center justify-between">
                    <span className="font-semibold text-foreground">Status</span>
                    <Badge
                      variant={course.status === "PUBLISHED" ? "default" : "secondary"}
                      className="text-[10px] uppercase font-bold"
                    >
                      {course.status}
                    </Badge>
                  </div>
                </CardContent>
              </Card>

              <Card className="border-border/60">
                <CardHeader className="py-3 px-4.5 border-b bg-secondary/10">
                  <CardTitle className="text-sm font-semibold text-foreground">
                    Course Metadata
                  </CardTitle>
                </CardHeader>
                <CardContent className="p-4 space-y-2.5 text-xs">
                  <div>
                    <span className="text-[11px] text-muted-foreground block">System ID</span>
                    <span className="font-mono text-[11px] text-foreground break-all">
                      {course.id}
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] text-muted-foreground block">Slug</span>
                    <span className="font-mono text-[11px] text-foreground">
                      {course.slug || "-"}
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] text-muted-foreground block">Language</span>
                    <span className="font-semibold text-foreground uppercase">
                      {course.language || "en"}
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] text-muted-foreground block">Owner ID</span>
                    <span className="font-mono text-[10px] text-muted-foreground break-all">
                      {course.ownerId || "-"}
                    </span>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        </TabsContent>

        {/* Tab 1: Curriculum Builder */}
        <TabsContent value="curriculum" className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 bg-card p-4 rounded-xl border">
            <div>
              <h2 className="text-sm font-semibold text-foreground">Course Sections & Structure</h2>
              <p className="text-xs text-muted-foreground">
                Build chapters, lessons, quizzes, and project assignments for learners.
              </p>
            </div>

            {/* Add Section Inline Form */}
            <form onSubmit={handleAddSection} className="flex gap-2">
              <Input
                value={newSectionTitle}
                onChange={(e) => setNewSectionTitle(e.target.value)}
                placeholder="New section title..."
                className="h-8 w-56 text-xs"
              />
              <Button
                type="submit"
                size="sm"
                variant="outline"
                disabled={createSectionMutation.isPending || !newSectionTitle.trim()}
                className="h-8 gap-1 text-xs"
              >
                <FolderPlus className="h-3.5 w-3.5" />
                <span>Add Section</span>
              </Button>
            </form>
          </div>

          {sectionError && <p className="text-xs text-destructive">{sectionError}</p>}

          {/* Sections List */}
          <div className="space-y-4">
            {sections && sections.length > 0 ? (
              sections.map((section, idx) => (
                <Card key={section.id} className="border-border/60">
                  <CardHeader className="py-3 px-4.5 bg-secondary/15 border-b flex flex-row items-center justify-between space-y-0">
                    <div className="flex items-center gap-2.5">
                      <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-primary/10 text-primary text-xs font-bold">
                        {idx + 1}
                      </span>
                      <CardTitle className="text-sm font-semibold text-foreground">
                        {section.title}
                      </CardTitle>
                    </div>

                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setActiveSectionIdForItem(section.id || null)}
                      className="h-7 text-xs gap-1 text-primary hover:text-primary hover:bg-primary/10"
                    >
                      <Plus className="h-3.5 w-3.5" />
                      <span>Add Item</span>
                    </Button>
                  </CardHeader>

                  <CardContent className="p-4">
                    {section.id && (
                      <SectionItemsList
                        sectionId={section.id}
                        onSelectItem={(item) => handleSelectItem(item)}
                      />
                    )}
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card className="p-12 text-center space-y-3 border-dashed">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-secondary text-muted-foreground">
                  <Layers className="h-6 w-6" />
                </div>
                <h3 className="text-base font-semibold text-foreground">
                  No curriculum sections yet
                </h3>
                <p className="text-sm text-muted-foreground max-w-sm mx-auto">
                  Courses require at least one section and lesson before they can be published.
                </p>
              </Card>
            )}
          </div>
        </TabsContent>

        {/* Tab 2: Instructors */}
        <TabsContent value="instructors">
          <Card className="p-6 space-y-4">
            <h3 className="text-sm font-semibold text-foreground">Assigned Course Instructors</h3>
            {instructors && instructors.length > 0 ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {instructors.map((inst) => (
                  <div
                    key={inst.instructorId}
                    className="flex items-center justify-between border rounded-lg p-3 text-xs"
                  >
                    <div>
                      <p className="font-semibold text-foreground">
                        Instructor {inst.instructorId ? `#${inst.instructorId.slice(0, 8)}` : ""}
                      </p>
                      <p className="text-muted-foreground text-[11px]">
                        Role: {inst.role || "Instructor"}
                      </p>
                    </div>
                    <Badge variant="outline">{inst.role}</Badge>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground italic">
                Only the course creator is currently assigned as the instructor.
              </p>
            )}
          </Card>
        </TabsContent>

        {/* Tab 3: Reviews */}
        <TabsContent value="reviews">
          <Card className="p-6 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-semibold text-foreground">
                  Learner Reviews & Feedback
                </h3>
                <p className="text-xs text-muted-foreground">
                  Ratings submitted by learners enrolled in this course.
                </p>
              </div>
              <div className="text-right">
                <div className="text-xl font-bold text-foreground flex items-center gap-1">
                  <Star className="h-4 w-4 fill-accent text-accent" />
                  <span>{reviewSummary?.average ? reviewSummary.average.toFixed(1) : "N/A"}</span>
                </div>
                <span className="text-[11px] text-muted-foreground">
                  {reviewSummary?.total || 0} total ratings
                </span>
              </div>
            </div>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Slide-Over Drawer: Add Item to Section */}
      <FloatingDetailSheet
        open={Boolean(activeSectionIdForItem)}
        onOpenChange={(open) => !open && setActiveSectionIdForItem(null)}
        title="Add Course Item"
        description="Add a new lesson, quiz, or assignment to this section."
        size="md"
        footerActions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setActiveSectionIdForItem(null)}
              disabled={createItemMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              size="sm"
              onClick={handleCreateItem}
              disabled={createItemMutation.isPending || !newItemTitle.trim()}
            >
              {createItemMutation.isPending ? "Adding..." : "Add Item"}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateItem} className="space-y-4">
          {itemError && (
            <div className="rounded-lg bg-destructive/10 p-3 text-xs font-medium text-destructive">
              {itemError}
            </div>
          )}

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">Item Title *</label>
            <Input
              value={newItemTitle}
              onChange={(e) => setNewItemTitle(e.target.value)}
              placeholder="e.g. Introduction to Routing"
              required
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">Item Type</label>
            <Select
              value={newItemType}
              onValueChange={(val: "LESSON" | "QUIZ" | "ASSIGNMENT") => setNewItemType(val)}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="LESSON">Lesson (Article, Video, or Document)</SelectItem>
                <SelectItem value="QUIZ">Quiz / Assessment</SelectItem>
                <SelectItem value="ASSIGNMENT">Assignment / Project</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </form>
      </FloatingDetailSheet>

      {/* Slide-Over Drawer: Inspect & Edit Lesson */}
      {activeItemId && (
        <LessonContentInspector
          key={activeItemId}
          itemId={activeItemId}
          itemTitle={inspectedItem?.title || search.itemTitle || "Lesson Editor"}
          open={Boolean(activeItemId)}
          onOpenChange={handleCloseInspector}
        />
      )}
    </div>
  );
}
