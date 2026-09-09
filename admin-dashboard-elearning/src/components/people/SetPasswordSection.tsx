import { useState } from "react";
import { KeyRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/** The backend's minimum, stated up front rather than discovered on submit. */
const MIN_PASSWORD = 12;

/**
 * Setting somebody's password for them, as a block inside their detail sheet.
 *
 * A section rather than a row action: this is the rare, deliberate thing you do
 * once you have opened an account and looked at it, not something to reach for
 * while scanning a list — and a button that takes over an account should not sit
 * one stray click away from the row above it.
 *
 * Shared by the administrator and instructor sheets, the two places where
 * reset-by-email is not an option. Admin accounts never had one, and an
 * instructor's address may never have been real; that is why this exists rather
 * than a "send a reset link" button.
 *
 * Renders no `<form>` of its own. It is dropped into sheets that already have
 * one, and a nested form is both invalid and a way to submit the wrong thing.
 */
export function SetPasswordSection({
  subject,
  pending,
  error,
  onSubmit,
}: {
  /** Whose password this is — named so nobody resets the wrong account. */
  subject: string;
  pending: boolean;
  /** A server refusal, rendered under the field. */
  error?: string | undefined;
  onSubmit: (newPassword: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const [tooShort, setTooShort] = useState(false);

  const close = () => {
    setEditing(false);
    setValue("");
    setTooShort(false);
  };

  const submit = () => {
    if (value.length < MIN_PASSWORD) {
      setTooShort(true);
      return;
    }
    setTooShort(false);
    onSubmit(value);
  };

  return (
    <div className="rounded-lg border border-dashed p-3">
      <p className="flex items-center gap-1.5 text-sm font-medium">
        <KeyRound className="h-3.5 w-3.5" />
        Password
      </p>

      {!editing ? (
        <>
          <p className="mt-1 text-xs text-muted-foreground">
            For when {subject || "this person"} has lost theirs and no reset email can reach them.
            You choose the password and pass it on — nothing is sent, and nothing forces a change at
            their next sign-in.
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="mt-2"
            onClick={() => setEditing(true)}
          >
            Set a new password
          </Button>
        </>
      ) : (
        <div className="mt-2 space-y-2">
          <Label htmlFor="new-password">New password</Label>
          {/*
            Not masked. The point is to produce something you then read out or
            paste into a message, and a row of dots you cannot check is a worse
            trade here than the shoulder-surfing it would save.
          */}
          <Input
            id="new-password"
            name="new-password"
            type="text"
            autoComplete="off"
            spellCheck={false}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={`At least ${MIN_PASSWORD} characters`}
            aria-invalid={tooShort || Boolean(error)}
            aria-describedby="new-password-help"
          />
          <p id="new-password-help" className="text-xs text-muted-foreground">
            {tooShort
              ? `Too short — at least ${MIN_PASSWORD} characters.`
              : "Their sessions end, so anyone signed in with the old password is signed out."}
          </p>
          {error && <p className="text-sm text-destructive">{error}</p>}

          <div className="flex gap-2">
            {/*
              type="button" throughout: this sits inside a sheet that already has
              a form, and a submit here would save that form instead.
            */}
            <Button type="button" size="sm" disabled={pending} onClick={submit}>
              {pending ? "Setting…" : "Set password"}
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={close}>
              Cancel
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
