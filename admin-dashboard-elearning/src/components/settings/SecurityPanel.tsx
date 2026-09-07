import { useState, type FormEvent } from "react";
import { useNavigate } from "@tanstack/react-router";
import { KeyRound, ShieldAlert } from "lucide-react";
import { useChangePasswordMutation } from "@/hooks/queries";
import { parseApiError } from "@/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

/** The backend's own minimum. Stated here so the form can say so before asking. */
const MIN_LENGTH = 12;

export function SecurityPanel() {
  const navigate = useNavigate();
  const changePassword = useChangePasswordMutation();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");

    if (newPassword.length < MIN_LENGTH) {
      setError(`The new password must be at least ${MIN_LENGTH} characters.`);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("The two new passwords do not match.");
      return;
    }

    changePassword.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          toast.success("Password changed. Sign in again with the new one.");
          // Every session ended, this one included — there is no token left to
          // stay here with.
          navigate({ to: "/login" });
        },
        onError: (err) => setError(parseApiError(err).message || "Could not change the password."),
      },
    );
  };

  return (
    <section className="card-surface space-y-4 p-5 sm:p-6">
      <div className="flex items-center gap-2">
        <div className="grid h-8 w-8 place-items-center rounded-lg bg-primary/10 text-primary">
          <KeyRound className="h-4 w-4" />
        </div>
        <div>
          <h2 className="text-lg font-bold">Your password</h2>
          <p className="text-xs text-muted-foreground">
            Changing it ends every session, including this one.
          </p>
        </div>
      </div>

      <form onSubmit={submit} className="max-w-md space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="current-password">Current password</Label>
          <Input
            id="current-password"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
          />
          <p className="text-xs text-muted-foreground">
            Required, so a borrowed session cannot lock you out of your own account.
          </p>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="new-password">New password</Label>
          <Input
            id="new-password"
            type="password"
            autoComplete="new-password"
            minLength={MIN_LENGTH}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
          />
          <p className="text-xs text-muted-foreground">At least {MIN_LENGTH} characters.</p>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="confirm-password">Confirm new password</Label>
          <Input
            id="confirm-password"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          />
        </div>

        {error && (
          <p className="flex items-start gap-1.5 text-sm text-destructive">
            <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
            <span>{error}</span>
          </p>
        )}

        <Button type="submit" disabled={changePassword.isPending}>
          {changePassword.isPending ? "Changing…" : "Change password"}
        </Button>
      </form>
    </section>
  );
}
