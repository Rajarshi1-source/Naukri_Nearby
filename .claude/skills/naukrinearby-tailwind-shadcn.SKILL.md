---
name: naukrinearby-tailwind-shadcn
description: >-
  Style the NaukriNearby UI with Tailwind CSS + shadcn/ui. Use this skill whenever building or
  refactoring ANY visual component, layout, theme, or form in this project — JobCard, DistanceBadge,
  SkillTag, RadiusSlider, SearchBar, OTPInput, LoadingSkeleton, EmptyState, dashboards, the job
  posting / alert-preference forms — or whenever the user mentions Tailwind, shadcn, Radix,
  components.json, the cn() helper, theming/CSS variables, dark mode, accessibility (a11y),
  responsive/mobile layout, react-hook-form + zod forms, or "make this look good / less generic."
  Trigger even without an explicit ask when styling decisions are being made. MANDATE: shadcn/ui
  components are copied INTO the repo (components/ui) and edited there — never installed as an npm
  dependency; preserve Radix accessibility; theme via tokens, not hardcoded colors; design
  mobile-first for slow Tier-2/3 networks. Pair with naukrinearby-nextjs for component structure.
---

# Tailwind CSS + shadcn/ui — NaukriNearby UI

You are a senior UI engineer styling NaukriNearby with **Tailwind CSS and shadcn/ui** on a
**Next.js 16 + React 19.2** frontend. shadcn/ui is not a component library you install — it's a set
of **accessible, Radix-based components you copy into the repo and own**. The users are on **budget
phones and slow networks in Tier-2/3 India**, so every styling decision is filtered through:
mobile-first, accessible, and light on payload.

## Core model (get this right first)

- **shadcn components live in `components/ui/` and are editable source**, added with
  `npx shadcn@latest add <component>` (button, card, dialog, form, input, slider, skeleton, table,
  tabs, etc.). They are yours to modify — don't treat them as a locked dependency, and don't add
  `shadcn-ui` to `package.json` as a runtime dep.
- **`components.json`** records the project config (style, base color, path aliases, Tailwind
  setup). Respect the aliases already there (`@/components`, `@/lib/utils`).
- **`cn()` in `lib/utils.ts`** merges class names (`clsx` + `tailwind-merge`). Use it for every
  component that takes a `className` prop so overrides don't conflict:

```ts
import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
export function cn(...inputs: ClassValue[]) { return twMerge(clsx(inputs)); }
```

- **React 19 (Next.js 16):** `ref` is a regular prop — current shadcn/ui source no longer wraps
  components in `forwardRef`. When you add or edit components in `components/ui`, accept `ref`
  directly (`function Input({ className, ref, ...props })`) rather than reintroducing `forwardRef`.
  Run `npx shadcn@latest add` to pull the React-19-ready versions.

## Theming — tokens, never hardcoded colors

shadcn themes through CSS variables. **A fresh Next.js 16 `create-next-app` ships Tailwind v4 by
default, so new code here is v4 (CSS-first).** Still **detect the Tailwind version in the repo and
follow its convention** — check `globals.css` / `components.json`:

- **Tailwind v4 (CSS-first):** tokens live in `@theme` in CSS, using the **OKLCH** color space for
  better perceived lightness. There is no `tailwind.config.js` for colors.
- **Tailwind v3:** tokens live in `tailwind.config.{js,ts}` and CSS variables are typically HSL.

Either way, **use the semantic tokens** (`bg-background`, `text-foreground`, `bg-primary`,
`text-muted-foreground`, `border-border`, `bg-destructive`) — never raw hex or arbitrary values when
a token exists. This is what makes light/dark mode and rebrands work without touching components.

```css
/* Tailwind v4 example — globals.css */
@theme {
  --color-background: oklch(1 0 0);
  --color-foreground: oklch(0.15 0 0);
  --color-primary: oklch(0.55 0.18 250);     /* brand accent */
  --color-muted-foreground: oklch(0.55 0 0);
  --radius: 0.625rem;
}
```

**Dark mode** uses the `class` strategy (`<html class="dark">`). Define the `.dark` token overrides;
don't hand-roll `dark:` variants per element when a token flip covers it.

## Accessibility is a feature, not an add-on

shadcn/ui sits on **Radix UI**, which provides keyboard navigation, focus management, and ARIA out
of the box. **Do not strip that out.** Concretely:

- Keep Radix primitives (Dialog, Select, Popover, Slider, Tabs) — don't replace them with bare
  `<div>`s that lose focus traps and roles.
- Associate every input with a `<Label>` (`htmlFor` / shadcn `FormLabel`).
- Touch targets ≥ 44×44px (budget phones, thumbs) — size buttons/inputs accordingly.
- Visible focus rings; sufficient contrast (OKLCH lightness helps); don't remove `outline` without a
  replacement.
- For the `OTPInput`, ensure each field is keyboard-navigable and announces position to screen
  readers.

## Forms: react-hook-form + zod + shadcn Form

The job-posting form, alert preferences, and registration all use the shadcn `Form` components on
top of react-hook-form with a zod resolver — one schema drives validation and types:

```tsx
const AlertPrefsSchema = z.object({
  radiusKm: z.number().min(1).max(50),
  language: z.enum(["en", "hi", "ta", "te"]),
  categories: z.array(z.string()).min(1, "Pick at least one category"),
});

const form = useForm<z.infer<typeof AlertPrefsSchema>>({
  resolver: zodResolver(AlertPrefsSchema),
  defaultValues: { radiusKm: 10, language: "en", categories: [] },
});

<Form {...form}>
  <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
    <FormField control={form.control} name="radiusKm" render={({ field }) => (
      <FormItem>
        <FormLabel>Search radius</FormLabel>
        <FormControl><RadiusSlider {...field} min={1} max={50} /></FormControl>
        <FormMessage />
      </FormItem>
    )} />
  </form>
</Form>
```

Validation errors render through `<FormMessage>` with proper `aria-describedby` wiring — keep it.

## Project components (build these on shadcn primitives)

| Component | Built from | Notes |
|---|---|---|
| `JobCard` | `Card` | title, company, `DistanceBadge`, salary, skills; tap target ≥ 44px |
| `DistanceBadge` | `Badge` | "2.4 km away" — `bg-primary/10 text-primary` tokens |
| `SkillTag` | `Badge` (secondary) | wrap in a flex-wrap row; truncate long lists |
| `RadiusSlider` | `Slider` | controlled; show the value; keyboard-accessible |
| `SearchBar` | `Input` + `Button` | large touch target; debounced |
| `OTPInput` | `Input` group / `InputOTP` | one digit per box, keyboard + paste support |
| `LoadingSkeleton` | `Skeleton` | match the real layout (card, list, map) |
| `EmptyState` | `Card` + icon | friendly copy + a clear next action |
| Dashboards | `Table`, `Tabs`, `Card` | responsive; horizontal scroll for tables on mobile |

## Mobile-first + slow-network discipline

- **Design at the smallest breakpoint first**, then add `sm:`/`md:`/`lg:` — most users are on phones.
- Prefer **system font stack** (or one well-subset variable font) to cut payload; don't pull several
  heavy web fonts.
- Avoid layout shift: reserve space with skeletons; set image dimensions.
- Don't ship megabytes of decorative CSS/animation — keep motion subtle and purposeful.
- Tables: stack or horizontally scroll on small screens; never overflow the viewport silently.

## Avoid the generic "AI slop" look

A distinctive, trustworthy UI reads as a real product (this matters for a portfolio interview).

- **One cohesive palette** anchored on a single brand hue (OKLCH), with consistent muted/foreground
  tokens — not five unrelated bright colors.
- **A real type scale** (consistent step ratio), generous line-height, limited weights.
- **Consistent spacing rhythm** using Tailwind's scale; avoid arbitrary one-off margins.
- **Subtle, staggered motion** for lists/cards; never bouncy or excessive.
- **Composition over configuration:** build compound components and use Radix's `asChild` to compose
  behavior rather than piling on boolean props.

## Anti-patterns to fix on sight

| Anti-pattern | Fix |
|---|---|
| Adding `shadcn-ui` as an npm runtime dependency | `npx shadcn@latest add` → own the files in `components/ui` |
| Editing components inside `node_modules` | edit the copied source in `components/ui` |
| Hardcoded hex / arbitrary color values | semantic tokens (`bg-primary`, `text-foreground`) |
| Replacing Radix primitives with bare `<div>`s | keep Radix for a11y (focus, ARIA, keyboard) |
| Per-element `dark:` overrides everywhere | flip tokens in `.dark`; use semantic classes |
| `className` props that fight each other | merge with `cn()` (tailwind-merge) |
| Tiny tap targets | ≥ 44px; size buttons/inputs for thumbs |
| Several heavy web fonts | system stack or one subset variable font |
| Generic rainbow palette / AI-default look | one brand hue, OKLCH, consistent scale & spacing |

## Quick reference

- shadcn = copied, editable, Radix-based components in `components/ui` — never a locked npm dep.
- Theme via tokens; new code is Tailwind **v4** (`@theme`/OKLCH) — but detect v3 (`tailwind.config`/HSL) and match it.
- Keep Radix accessibility; ≥44px targets; labels + focus rings.
- Forms → react-hook-form + zod + shadcn `Form`.
- Mobile-first, light payload, real loading/empty states (slow Tier-2/3 networks).
- Distinctive look: one palette, real type scale, consistent spacing, subtle motion.
- Component structure / `'use client'` rules → naukrinearby-nextjs.
