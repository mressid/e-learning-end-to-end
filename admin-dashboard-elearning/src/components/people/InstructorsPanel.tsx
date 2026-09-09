import { useEffect, useState, type FormEvent } from "react";
import { GraduationCap, Plus, Search, X, Pencil } from "lucide-react";
import {
  useAdminInstructorsQuery,
  useCreateUserMutation,
  useUpdateUserMutation,
  useSetUserPasswordMutation,
  usePermissions,
} from "@/hooks/queries";
import { PERMISSIONS, parseApiError, type InstructorRosterEntry } from "@/api";
import { Pager } from "@/components/dashboard/Pager";
import { FloatingDetailSheet } from "@/components/dashboard/FloatingDetailSheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { SetPasswordSection } from "./SetPasswordSection";
import type { PanelAddProps } from "./panel";
import { toast } from "sonner";

const description = "People who may author courses.";

const PAGE_SIZE = 20;
/** The backend's minimum. Stated up front rather than discovered on submit. */
const MIN_PASSWORD = 12;

// The sheet renders its actions in a footer outside the scrolling body, so the
// buttons reference their form by id rather than being nested inside it.
const ADD_FORM_ID = "add-instructor-form";
const EDIT_FORM_ID = "edit-instructor-form";

export function InstructorsPanel({ onAdd }: PanelAddProps) {
  const { has } = usePermissions();
  const canWrite = has(PERMISSIONS.USER_WRITE);

  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    onAdd?.(() => setAdding(true));
  }, [onAdd]);
  const [editing, setEditing] = useState<InstructorRosterEntry | null>(null);

  const { data, isLoading, isError, error, refetch } = useAdminInstructorsQuery({
    ...(query ? { q: query } : {}),
    page,
    size: PAGE_SIZE,
  });

  const rows = data?.content ?? [];

  const clearSearch = () => {
    setSearch("");
    setQuery("");
    setPage(0);
  };

  return (
    <div className="space-y-6">
      <section className="card-surface p-4 sm:p-5">
        <div className="flex flex-wrap items-center gap-2">
          <form
            onSubmit={(e: FormEvent) => {
              e.preventDefault();
              setQuery(search.trim());
              setPage(0);
            }}
            className="relative min-w-0 flex-1 sm:max-w-sm"
          >
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground rtl:left-auto rtl:right-3" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search name or email…"
              className="pl-9 rtl:pl-3 rtl:pr-9"
              aria-label="Search instructors"
            />
            {search && (
              <button
                type="button"
                onClick={clearSearch}
                aria-label="Clear search"
                className="absolute right-2 top-1/2 grid h-6 w-6 -translate-y-1/2 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground rtl:right-auto rtl:left-2"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </form>
        </div>

        {/*
          The kind of account is what puts someone here, not their course count:
          an instructor added this morning belongs on this list even though they
          have not authored anything.
        */}
        <p className="mt-3 text-xs text-muted-foreground">
          A separate kind of account from a learner, and not convertible either way — being one is
          what allows authoring courses. It is not authority over any particular course; that still
          comes from owning it or being added to it.
        </p>

        <div className="mt-4 overflow-x-auto">
          {isLoading ? (
            <div className="space-y-3 py-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex items-center gap-3">
                  <Skeleton className="h-8 w-8 shrink-0 rounded-full" />
                  <Skeleton className="h-4 flex-1" />
                  <Skeleton className="h-4 w-24" />
                </div>
              ))}
            </div>
          ) : isError ? (
            <div className="grid place-items-center py-14 text-center">
              <div className="max-w-sm">
                <p className="font-semibold">Could not load instructors</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {parseApiError(error).message || "The server did not respond."}
                </p>
                <Button variant="outline" size="sm" className="mt-3" onClick={() => refetch()}>
                  Try again
                </Button>
              </div>
            </div>
          ) : rows.length === 0 ? (
            <div className="grid place-items-center py-14 text-center">
              <div className="max-w-xs">
                <div className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-muted text-muted-foreground">
                  <GraduationCap className="h-5 w-5" />
                </div>
                <p className="mt-3 font-semibold">
                  {query ? "Nobody matches that" : "No instructors yet"}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {query
                    ? "Try a different search."
                    : canWrite
                      ? "Add one. Nobody arrives here by authoring a course — it is the other way round."
                      : "An administrator creates them; nobody becomes one by authoring a course."}
                </p>
                {query && (
                  <Button variant="outline" size="sm" className="mt-3" onClick={clearSearch}>
                    Clear search
                  </Button>
                )}
              </div>
            </div>
          ) : (
            <table className="w-full min-w-[40rem] text-sm">
              <thead>
                <tr className="border-b text-left text-xs uppercase tracking-wider text-muted-foreground rtl:text-right">
                  <th className="pb-2 pr-3 font-semibold">Instructor</th>
                  <th className="pb-2 pr-3 font-semibold">Courses</th>
                  <th className="pb-2 font-semibold">Published</th>
                  {canWrite && <th className="pb-2 text-right font-semibold rtl:text-left" />}
                </tr>
              </thead>
              <tbody className="divide-y">
                {rows.map((person) => {
                  const total = person.courseCount ?? 0;
                  const live = person.publishedCourseCount ?? 0;
                  const drafts = total - live;
                  return (
                    <tr key={person.id}>
                      <td className="py-2.5 pr-3">
                        <div className="flex items-center gap-2.5">
                          <span
                            aria-hidden
                            className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-primary/10 text-[11px] font-bold text-primary"
                          >
                            {(person.displayName || person.username || "?")
                              .slice(0, 2)
                              .toUpperCase()}
                          </span>
                          <div className="min-w-0">
                            <p className="truncate font-medium">
                              {person.displayName || person.username}
                            </p>
                            <p className="truncate text-xs text-muted-foreground">{person.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="py-2.5 pr-3 font-semibold tabular-nums">{total}</td>
                      <td className="py-2.5">
                        {total === 0 ? (
                          <span className="text-xs text-muted-foreground">
                            Not authored anything yet
                          </span>
                        ) : (
                          <div className="flex items-center gap-1.5">
                            <Badge
                              variant="default"
                              className="h-5 px-1.5 text-[10px] font-semibold"
                            >
                              {live} live
                            </Badge>
                            {drafts > 0 && (
                              <Badge
                                variant="secondary"
                                className="h-5 px-1.5 text-[10px] font-semibold"
                              >
                                {drafts} unpublished
                              </Badge>
                            )}
                          </div>
                        )}
                      </td>
                      {canWrite && (
                        <td className="py-2.5 text-right rtl:text-left">
                          <Button variant="ghost" size="sm" onClick={() => setEditing(person)}>
                            <Pencil className="h-3.5 w-3.5" />
                            <span className="sr-only sm:not-sr-only">Edit</span>
                          </Button>
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        <div className="mt-4">
          <Pager
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 0}
            totalElements={data?.totalElements ?? 0}
            onChange={setPage}
            noun="instructor"
          />
        </div>
      </section>

      <AddInstructorSheet open={adding} onClose={() => setAdding(false)} />
      <EditInstructorSheet person={editing} onClose={() => setEditing(null)} />
    </div>
  );
}

function AddInstructorSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const create = useCreateUserMutation();
  const [form, setForm] = useState({
    firstName: "",
    lastName: "",
    username: "",
    email: "",
    password: "",
  });
  const [error, setError] = useState("");

  const reset = () => {
    setForm({ firstName: "", lastName: "", username: "", email: "", password: "" });
    setError("");
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    if (form.password.length < MIN_PASSWORD) {
      setError(`The password must be at least ${MIN_PASSWORD} characters.`);
      return;
    }
    create.mutate(
      {
        email: form.email.trim(),
        username: form.username.trim(),
        password: form.password,
        firstName: form.firstName.trim() || null,
        lastName: form.lastName.trim() || null,
        type: "INSTRUCTOR",
      },
      {
        onSuccess: (created) => {
          toast.success(`${created.username} can now author courses`);
          reset();
          onClose();
        },
        onError: (err) => setError(parseApiError(err).message || "Could not create that account."),
      },
    );
  };

  const close = () => {
    reset();
    onClose();
  };

  return (
    <FloatingDetailSheet
      open={open}
      onOpenChange={(next) => !next && close()}
      title="Add an instructor"
      description="Active straight away — there is no confirmation email, so pass the password on yourself. Nothing forces a change at first sign-in."
      footerActions={
        <>
          <Button type="button" variant="outline" onClick={close}>
            Cancel
          </Button>
          {/* The footer sits outside the <form>, so it submits by id rather
              than by being nested in it. */}
          <Button type="submit" form={ADD_FORM_ID} disabled={create.isPending}>
            {create.isPending ? "Adding…" : "Add instructor"}
          </Button>
        </>
      }
    >
      <form id={ADD_FORM_ID} onSubmit={submit} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="i-first">First name</Label>
            <Input
              id="i-first"
              value={form.firstName}
              onChange={(e) => setForm({ ...form, firstName: e.target.value })}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="i-last">Last name</Label>
            <Input
              id="i-last"
              value={form.lastName}
              onChange={(e) => setForm({ ...form, lastName: e.target.value })}
            />
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="i-username">Username</Label>
          <Input
            id="i-username"
            required
            minLength={3}
            value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="i-email">Email</Label>
          <Input
            id="i-email"
            type="email"
            required
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="i-password">Starting password</Label>
          <Input
            id="i-password"
            type="password"
            autoComplete="new-password"
            required
            minLength={MIN_PASSWORD}
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
          <p className="text-xs text-muted-foreground">At least {MIN_PASSWORD} characters.</p>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}
      </form>
    </FloatingDetailSheet>
  );
}

function EditInstructorSheet({
  person,
  onClose,
}: {
  person: InstructorRosterEntry | null;
  onClose: () => void;
}) {
  const update = useUpdateUserMutation();
  const [error, setError] = useState("");

  // Instructors are created by an administrator and may never have had a
  // working inbox, so reset-by-email is not a route back in for them.
  const setPassword = useSetUserPasswordMutation();
  const [passwordError, setPasswordError] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!person?.id) return;
    setError("");
    const data = new FormData(e.target as HTMLFormElement);
    update.mutate(
      {
        userId: person.id,
        username: String(data.get("username") ?? "").trim(),
        email: String(data.get("email") ?? "").trim(),
      },
      {
        onSuccess: () => {
          toast.success("Instructor updated");
          onClose();
        },
        onError: (err) => setError(parseApiError(err).message || "Could not save that."),
      },
    );
  };

  return (
    <FloatingDetailSheet
      open={person !== null}
      onOpenChange={(next) => !next && onClose()}
      title={person ? `Edit ${person.username}` : "Edit"}
      description="Only the fields you change are sent."
      footerActions={
        <>
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form={EDIT_FORM_ID} disabled={update.isPending}>
            {update.isPending ? "Saving…" : "Save changes"}
          </Button>
        </>
      }
    >
      {person && (
        <form id={EDIT_FORM_ID} onSubmit={submit} className="space-y-4" key={person.id}>
          <div className="space-y-1.5">
            <Label htmlFor="e-username">Username</Label>
            <Input id="e-username" name="username" defaultValue={person.username} minLength={3} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="e-email">Email</Label>
            <Input id="e-email" name="email" type="email" defaultValue={person.email} />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </form>
      )}

      {person && (
        <div className="mt-4 space-y-4">
          <SetPasswordSection
            key={person.id}
            subject={person.displayName || person.username || ""}
            pending={setPassword.isPending}
            error={passwordError}
            onSubmit={(newPassword) => {
              if (!person.id) return;
              const name = person.displayName || person.username;
              setPasswordError("");
              setPassword.mutate(
                { userId: person.id, newPassword },
                {
                  onSuccess: () => toast.success(`New password set for ${name}. Pass it on.`),
                  onError: (err) =>
                    setPasswordError(parseApiError(err).message || "Could not set that password."),
                },
              );
            }}
          />

          {/* Stated because "how do I undo this" is the question anyone asks
              here, and the answer is no longer a button. */}
          <div className="rounded-lg border border-dashed p-3">
            <p className="text-sm font-medium">This account is an instructor permanently</p>
            <p className="mt-1 text-xs text-muted-foreground">
              There is no way to turn it into a learner account, here or anywhere. An instructor and
              a learner are different kinds of account, decided when the account is made — someone
              who wants to take courses as well registers as a learner separately. To stop this
              person working, suspend the account.
            </p>
          </div>
        </div>
      )}
    </FloatingDetailSheet>
  );
}
