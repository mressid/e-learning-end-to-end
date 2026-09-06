import { useState } from "react";
import { createFileRoute, Outlet, useMatchRoute, Link } from "@tanstack/react-router";
import {
  BookOpen,
  Plus,
  Search,
  SlidersHorizontal,
  Layers,
  GraduationCap,
  Calendar,
  CheckCircle2,
  Clock3,
  Archive,
  Eye,
  RefreshCw,
  FolderPlus,
  ChevronRight,
  Sparkles,
  ExternalLink,
} from "lucide-react";
import { useI18n } from "@/lib/i18n";
import {
  useCoursesQuery,
  useCourseQuery,
  useCreateCourseMutation,
  usePublishCourseMutation,
  useUnpublishCourseMutation,
  useArchiveCourseMutation,
  useSectionsQuery,
  useCreateSectionMutation,
  useCategoriesQuery,
} from "@/hooks/queries";
import { FloatingDetailSheet } from "@/components/dashboard/FloatingDetailSheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { parseApiError, type CourseResponse } from "@/api";

export const Route = createFileRoute("/courses")({
  head: () => ({
    meta: [
      { title: "Courses — Lernova" },
      { name: "description", content: "Build, publish and organize your course catalog." },
      { property: "og:title", content: "Courses — Lernova" },
      { property: "og:description", content: "Build, publish and organize your course catalog." },
    ],
  }),
  component: CoursesRouteComponent,
});

function CoursesRouteComponent() {
  const matchRoute = useMatchRoute();
  const isCourseDetail = matchRoute({ to: "/courses/$courseId" });

  if (isCourseDetail) {
    return <Outlet />;
  }

  return <CoursesPage />;
}

function CoursesPage() {
  const { t, dir } = useI18n();

  // Search & Filters
  const [searchQuery, setSearchQuery] = useState("");
  const [levelFilter, setLevelFilter] = useState<
    "ALL" | "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "ALL_LEVELS"
  >("ALL");
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // New Course Form State
  const [newTitle, setNewTitle] = useState("");
  const [newShortDesc, setNewShortDesc] = useState("");
  const [newDesc, setNewDesc] = useState("");
  const [newLevel, setNewLevel] = useState<"BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "ALL_LEVELS">(
    "BEGINNER",
  );
  const [formError, setFormError] = useState("");

  // New Section State
  const [newSectionTitle, setNewSectionTitle] = useState("");
  const [sectionError, setSectionError] = useState("");

  // Data Queries
  const { data: categories } = useCategoriesQuery();
  const {
    data: coursesData,
    isLoading,
    isError,
    refetch,
  } = useCoursesQuery(searchQuery.trim() ? { q: searchQuery.trim() } : undefined);

  const { data: activeCourse, isLoading: isActiveCourseLoading } = useCourseQuery(
    selectedCourseId || "",
  );
  const { data: activeSections, refetch: refetchSections } = useSectionsQuery(
    selectedCourseId || "",
  );

  // Mutations
  const createCourseMutation = useCreateCourseMutation();
  const publishMutation = usePublishCourseMutation();
  const unpublishMutation = useUnpublishCourseMutation();
  const archiveMutation = useArchiveCourseMutation();
  const createSectionMutation = useCreateSectionMutation(selectedCourseId || "");

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) {
      setFormError("Title is required.");
      return;
    }
    setFormError("");

    try {
      const created = await createCourseMutation.mutateAsync({
        title: newTitle.trim(),
        shortDescription: newShortDesc.trim() || null,
        description: newDesc.trim() || null,
        level: newLevel,
        language: "en",
      });
      setIsCreateOpen(false);
      setNewTitle("");
      setNewShortDesc("");
      setNewDesc("");
      if (created?.id) {
        setSelectedCourseId(created.id);
      }
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      setFormError(apiErr.message || "Failed to create course.");
    }
  };

  const handleAddSection = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSectionTitle.trim() || !selectedCourseId) return;
    setSectionError("");

    try {
      await createSectionMutation.mutateAsync({
        title: newSectionTitle.trim(),
      });
      setNewSectionTitle("");
      refetchSections();
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      setSectionError(apiErr.message || "Failed to add section.");
    }
  };

  const handlePublish = async () => {
    if (!selectedCourseId) return;
    try {
      await publishMutation.mutateAsync(selectedCourseId);
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      alert(apiErr.message || "Failed to publish course.");
    }
  };

  const handleUnpublish = async () => {
    if (!selectedCourseId) return;
    try {
      await unpublishMutation.mutateAsync(selectedCourseId);
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      alert(apiErr.message || "Failed to unpublish course.");
    }
  };

  const handleArchive = async () => {
    if (!selectedCourseId) return;
    try {
      await archiveMutation.mutateAsync(selectedCourseId);
    } catch (err: unknown) {
      const apiErr = parseApiError(err);
      alert(apiErr.message || "Failed to archive course.");
    }
  };

  const allCourses = coursesData?.content || [];
  const courses = allCourses.filter((course: CourseResponse) => {
    if (levelFilter === "ALL") return true;
    return course.level === levelFilter;
  });

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">{t("nav.courses")}</h1>
          <p className="text-sm text-muted-foreground">{t("page.courses.desc")}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()} className="gap-1.5">
            <RefreshCw className="h-4 w-4" />
            <span>Refresh</span>
          </Button>
          <Button size="sm" onClick={() => setIsCreateOpen(true)} className="gap-1.5">
            <Plus className="h-4 w-4" />
            <span>New Course</span>
          </Button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row items-center gap-3">
        <div className="relative flex-1 w-full">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground rtl:left-auto rtl:right-3" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search courses by title or keyword..."
            className="pl-9 rtl:pl-3 rtl:pr-9"
          />
        </div>
        <div className="w-full sm:w-48">
          <Select
            value={levelFilter}
            onValueChange={(val: string) =>
              setLevelFilter(val as "ALL" | "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "ALL_LEVELS")
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="All Levels" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Levels</SelectItem>
              <SelectItem value="BEGINNER">Beginner</SelectItem>
              <SelectItem value="INTERMEDIATE">Intermediate</SelectItem>
              <SelectItem value="ADVANCED">Advanced</SelectItem>
              <SelectItem value="ALL_LEVELS">All Levels</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Courses Content */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Card key={i} className="p-4 space-y-3">
              <Skeleton className="h-6 w-3/4" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-1/2" />
            </Card>
          ))}
        </div>
      ) : isError ? (
        <Card className="p-8 text-center space-y-3">
          <p className="text-sm text-destructive font-medium">Failed to load courses from API.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            Try Again
          </Button>
        </Card>
      ) : courses.length === 0 ? (
        <Card className="p-12 text-center space-y-3 border-dashed">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-secondary text-muted-foreground">
            <BookOpen className="h-6 w-6" />
          </div>
          <h3 className="text-base font-semibold text-foreground">No courses found</h3>
          <p className="text-sm text-muted-foreground max-w-sm mx-auto">
            Get started by creating your first course or adjusting your active search filters.
          </p>
          <Button size="sm" onClick={() => setIsCreateOpen(true)} className="gap-1.5 mt-2">
            <Plus className="h-4 w-4" />
            <span>Create Course</span>
          </Button>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {courses.map((course: CourseResponse) => (
            <Card
              key={course.id}
              onClick={() => setSelectedCourseId(course.id || null)}
              className="cursor-pointer transition-all hover:border-primary/50 hover:shadow-sm"
            >
              <CardContent className="p-5 space-y-3">
                <div className="flex items-start justify-between gap-2">
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
                    <span className="text-xs font-medium text-muted-foreground">
                      {course.level}
                    </span>
                  )}
                </div>

                <div>
                  <h3 className="font-semibold text-foreground line-clamp-1 group-hover:text-primary">
                    {course.title}
                  </h3>
                  <p className="text-xs text-muted-foreground line-clamp-2 mt-1">
                    {course.shortDescription || course.description || "No description provided."}
                  </p>
                </div>

                <div className="flex items-center justify-between pt-2 border-t text-xs text-muted-foreground">
                  <span className="flex items-center gap-1">
                    <Calendar className="h-3.5 w-3.5" />
                    {course.createdAt ? new Date(course.createdAt).toLocaleDateString() : "-"}
                  </span>
                  <div className="flex items-center gap-2.5">
                    {course.id && (
                      <Link
                        to="/courses/$courseId"
                        params={{ courseId: course.id }}
                        onClick={(e) => e.stopPropagation()}
                        className="flex items-center gap-0.5 text-primary font-semibold hover:underline"
                      >
                        <span>Workspace</span>
                        <ChevronRight className="h-3.5 w-3.5" />
                      </Link>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Slide-Over Drawer: Create New Course */}
      <FloatingDetailSheet
        open={isCreateOpen}
        onOpenChange={setIsCreateOpen}
        title="Create New Course"
        description="Fill out the initial metadata to start authoring your course."
        size="md"
        footerActions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsCreateOpen(false)}
              disabled={createCourseMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              size="sm"
              onClick={handleCreateSubmit}
              disabled={createCourseMutation.isPending}
            >
              {createCourseMutation.isPending ? "Creating..." : "Save Draft"}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          {formError && (
            <div className="rounded-lg bg-destructive/10 p-3 text-xs font-medium text-destructive">
              {formError}
            </div>
          )}

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">Course Title *</label>
            <Input
              value={newTitle}
              onChange={(e) => setNewTitle(e.target.value)}
              placeholder="e.g. Masterclass in Web Architecture"
              required
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">Short Summary</label>
            <Input
              value={newShortDesc}
              onChange={(e) => setNewShortDesc(e.target.value)}
              placeholder="One line synopsis for course cards"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">Target Level</label>
            <Select
              value={newLevel}
              onValueChange={(val: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "ALL_LEVELS") =>
                setNewLevel(val)
              }
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="BEGINNER">Beginner</SelectItem>
                <SelectItem value="INTERMEDIATE">Intermediate</SelectItem>
                <SelectItem value="ADVANCED">Advanced</SelectItem>
                <SelectItem value="ALL_LEVELS">All Levels</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">Full Description</label>
            <textarea
              rows={4}
              value={newDesc}
              onChange={(e) => setNewDesc(e.target.value)}
              placeholder="Detailed course curriculum and syllabus outline..."
              className="w-full rounded-md border border-input bg-card p-3 text-sm text-foreground outline-none transition focus:border-ring focus:ring-1 focus:ring-ring"
            />
          </div>
        </form>
      </FloatingDetailSheet>

      {/* Slide-Over Drawer: Course Inspection and Management */}
      <FloatingDetailSheet
        open={Boolean(selectedCourseId)}
        onOpenChange={(open) => !open && setSelectedCourseId(null)}
        title={activeCourse?.title || "Course Details"}
        description={`ID: ${selectedCourseId}`}
        badge={
          activeCourse?.status && (
            <Badge
              variant={
                activeCourse.status === "PUBLISHED"
                  ? "default"
                  : activeCourse.status === "DRAFT"
                    ? "secondary"
                    : "outline"
              }
            >
              {activeCourse.status}
            </Badge>
          )
        }
        size="lg"
        footerActions={
          <div className="flex items-center gap-2">
            {activeCourse?.status === "DRAFT" && (
              <Button
                size="sm"
                onClick={handlePublish}
                disabled={publishMutation.isPending}
                className="gap-1.5"
              >
                <CheckCircle2 className="h-4 w-4" />
                <span>Publish</span>
              </Button>
            )}
            {activeCourse?.status === "PUBLISHED" && (
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
            {activeCourse?.status !== "ARCHIVED" && (
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
            {selectedCourseId && (
              <Link
                to="/courses/$courseId"
                params={{ courseId: selectedCourseId }}
                className="inline-flex items-center gap-1.5 rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90"
              >
                <ExternalLink className="h-3.5 w-3.5" />
                <span>Open Workspace</span>
              </Link>
            )}
            <Button variant="outline" size="sm" onClick={() => setSelectedCourseId(null)}>
              Close
            </Button>
          </div>
        }
      >
        {isActiveCourseLoading ? (
          <div className="space-y-4">
            <Skeleton className="h-6 w-3/4" />
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-32 w-full" />
          </div>
        ) : activeCourse ? (
          <div className="space-y-6">
            {/* Overview Card */}
            <div className="rounded-xl border bg-card/60 p-4 space-y-3">
              <div className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Course Information
              </div>
              <p className="text-sm text-foreground">
                {activeCourse.description ||
                  activeCourse.shortDescription ||
                  "No description provided."}
              </p>
              <div className="grid grid-cols-2 gap-3 pt-2 text-xs">
                <div>
                  <span className="text-muted-foreground">Difficulty:</span>{" "}
                  <span className="font-semibold text-foreground">
                    {activeCourse.level || "N/A"}
                  </span>
                </div>
                <div>
                  <span className="text-muted-foreground">Language:</span>{" "}
                  <span className="font-semibold text-foreground uppercase">
                    {activeCourse.language || "en"}
                  </span>
                </div>
                <div>
                  <span className="text-muted-foreground">Created:</span>{" "}
                  <span className="font-semibold text-foreground">
                    {activeCourse.createdAt
                      ? new Date(activeCourse.createdAt).toLocaleDateString()
                      : "-"}
                  </span>
                </div>
                <div>
                  <span className="text-muted-foreground">Published:</span>{" "}
                  <span className="font-semibold text-foreground">
                    {activeCourse.publishedAt
                      ? new Date(activeCourse.publishedAt).toLocaleDateString()
                      : "Not published"}
                  </span>
                </div>
              </div>
            </div>

            {/* Sections / Curriculum */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="text-sm font-semibold text-foreground flex items-center gap-1.5">
                  <Layers className="h-4 w-4 text-primary" />
                  <span>Curriculum Sections</span>
                </h4>
                <span className="text-xs text-muted-foreground">
                  {activeSections?.length || 0} sections
                </span>
              </div>

              {/* Add Section Form */}
              <form onSubmit={handleAddSection} className="flex gap-2">
                <Input
                  size={1}
                  value={newSectionTitle}
                  onChange={(e) => setNewSectionTitle(e.target.value)}
                  placeholder="New section title..."
                  className="h-8 text-xs"
                />
                <Button
                  type="submit"
                  size="sm"
                  variant="outline"
                  disabled={createSectionMutation.isPending || !newSectionTitle.trim()}
                  className="h-8 shrink-0 text-xs gap-1"
                >
                  <FolderPlus className="h-3.5 w-3.5" />
                  <span>Add</span>
                </Button>
              </form>

              {sectionError && <p className="text-xs text-destructive">{sectionError}</p>}

              {/* Sections List */}
              <div className="space-y-2">
                {activeSections && activeSections.length > 0 ? (
                  activeSections.map((sec, idx) => (
                    <div
                      key={sec.id}
                      className="flex items-center justify-between rounded-lg border bg-card p-3 text-xs"
                    >
                      <div className="flex items-center gap-2">
                        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-secondary font-semibold text-muted-foreground">
                          {idx + 1}
                        </span>
                        <span className="font-medium text-foreground">{sec.title}</span>
                      </div>
                      <Badge variant="outline" className="text-[10px]">
                        Section
                      </Badge>
                    </div>
                  ))
                ) : (
                  <p className="text-xs text-muted-foreground italic text-center py-4 border rounded-lg border-dashed">
                    No curriculum sections yet. Add a section above before publishing.
                  </p>
                )}
              </div>
            </div>
          </div>
        ) : null}
      </FloatingDetailSheet>
    </div>
  );
}
