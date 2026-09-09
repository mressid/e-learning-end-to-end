# Routes

TanStack Start uses **file-based routing**. Every `.tsx` file in this directory
defines a route. Do **not** create `src/pages/`, `src/routes/_app/index.tsx`, or
`app/layout.tsx` — those are Next.js / Remix conventions. The only root layout
is `src/routes/__root.tsx`.

## Conventions

| File                     | URL                                                     |
| ------------------------ | ------------------------------------------------------- |
| `index.tsx`              | `/`                                                     |
| `about.tsx`              | `/about`                                                |
| `users/index.tsx`        | `/users`                                                |
| `users/$id.tsx`          | `/users/:id` (dynamic — bare `$`, no curly braces)      |
| `posts/{-$category}.tsx` | `/posts/:category?` (optional segment)                  |
| `files/$.tsx`            | `/files/*` (splat — read via `_splat` param, never `*`) |
| `_layout.tsx`            | layout route (renders children via `<Outlet />`)        |
| `__root.tsx`             | app shell — wraps every page; preserve `<Outlet />`     |

`routeTree.gen.ts` is auto-generated. Don't edit it by hand.

## Where to put a new page

Under `_authenticated/`, unless you mean it to be reachable without signing in.

`_authenticated.tsx` is a **pathless** layout route: the `_` prefix means it adds
no URL segment, so `_authenticated/courses.tsx` still serves `/courses`. What it
adds is the two things every dashboard page shares — the requirement to have a
session, and the sidebar/topbar chrome. A file placed at the top level instead
gets neither, which is right for `login.tsx` and wrong for everything else.

Note the direction of that default: protection follows placement, so a page put
at the top level by accident is reachable while signed out. It will still show
nothing useful — the API refuses anonymous requests, and the frontend guard was
never the security boundary — but it will look broken rather than redirect.
