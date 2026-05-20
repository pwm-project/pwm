# Issue #729 — AngularJS EOL (v1.8.3 / v1.3.10) — upgrade impact assessment

**Issue:** https://github.com/pwm-project/pwm/issues/729
**PWM version evaluated:** v2.0.8 (`v2_0` branch)
**Date of analysis:** 2026-05-20
**Status:** Option B (modern Angular migration) **in progress** on branch `issue-729-angular-migration` — see "Progress" section at the bottom of this document. No PR until UI is tested end-to-end against a deployed WAR.

---

## Verified surface area

The AngularJS dependency in PWM is **non-trivial**:

| Module          | Files                        | Lines        | Bound to JSP            |
| --------------- | ---------------------------- | ------------ | ----------------------- |
| `changepassword`| 3 (1 controller, 81 LOC)     | small        | `changepassword.jsp`    |
| `configeditor`  | 2 (1 controller, 48 LOC)     | small + textangular WYSIWYG | `configeditor.jsp` |
| `helpdesk`      | 17                           | medium-large | `helpdesk.jsp`          |
| `peoplesearch`  | 23                           | largest module | `peoplesearch.jsp`    |
| **Total**       | **67 .ts + 20 .html**        | **~4,812 LOC** | 4 JSP entry points    |

All four are loaded via the same pattern:

```jsp
<pwm:script-ref url="/public/resources/webjars/pwm-client/vendor.js" />
<pwm:script-ref url="/public/resources/webjars/pwm-client/<module>.ng.js" />
```

`vendor.js` is the webpack-bundled `node_modules/*` output containing AngularJS, ui-router, ng-translate, textangular, and the MicroFocus internal libraries.

---

## What's actually in there

Verified from `client/angular/package-lock.json`:

```
angular           1.8.3   ← direct
angular-aria      1.8.3   ← direct
angular-sanitize  1.8.3   ← direct (this is the one most CVEs target)
angular-translate 2.19.0
angular-mocks     1.6.9   ← devDep only, not shipped
@uirouter/angularjs 1.0.30
@microfocus/ng-ias  1.0.1  ← depends on angular >=1.2.0
@microfocus/ux-ias  1.1.3  ← depends on angular >=1.2.0
textangular        1.5.16 ← bundles its own internal angular fragment (this is the v1.3.10 the reporter saw)
```

The reporter's "v1.3.10" finding is real but mis-attributed — it's the **textangular WYSIWYG editor's** embedded AngularJS fragment used internally for its sanitization shim, not a second top-level AngularJS dependency.

---

## Why CI hasn't been flagging this

`pom.xml` (parent):

```xml
<configuration>
    <skip>${owasp.skip}</skip>
    <yarnAuditAnalyzerEnabled>false</yarnAuditAnalyzerEnabled>
    <nodeAnalyzerEnabled>false</nodeAnalyzerEnabled>
</configuration>
```

OWASP `dependency-check` has the Node analyzer **disabled**. That's why these CVEs never appeared in the existing security-review pipeline — only customer-side scanners (like the reporter's PCI scan) hit them.

**Worth turning the Node analyzer back on as part of any remediation** so this doesn't recur silently.

---

## CVE applicability to PWM's actual attack surface

| CVE                  | Vector                                       | Exploitable in PWM?                                                                       |
| -------------------- | -------------------------------------------- | ----------------------------------------------------------------------------------------- |
| **CVE-2022-25844**   | ReDoS in `$resource`                         | **No** — `vendor.js` doesn't bundle `angular-resource`                                    |
| **CVE-2022-25869**   | XSS via expressions, user input → ng-bind-html | **Limited** — helpdesk/peoplesearch search boxes; backend already sanitizes via `Validator` |
| **CVE-2024-8372**    | content spoofing via attacker-controlled URL in `<a>` | **Possible** — config-editor link fields                                          |
| **CVE-2024-8373**    | content spoofing, similar vector             | **Possible** — same                                                                       |
| **CVE-2025-0716**    | SVG sanitization bypass via innerHTML        | **No** — PWM doesn't render user SVGs in AngularJS templates                              |
| **CVE-2025-4690**    | ReDoS in `ngSanitize` via crafted input      | **Yes (limited)** — authenticated DoS-self only (helpdesk operator can DoS their own browser session) |

Reality: most of these are low-impact in PWM's specific surface (post-auth, server-side validated input, no SVG rendering, no `$resource`). But the **vendor reputation problem is the real bug** — anyone running a PCI/SOC2/FedRAMP scan against a PWM deployment lights up red regardless of exploitability, and there's nothing customers can do about it because the JS lives inside a JAR.

---

## Upgrade-path options and risks

There's no AngularJS upgrade — **1.8.3 is the last release ever** (EOL 2021-12-31). Every path is a migration.

### Option A — Community LTS fork (e.g. `@angular-js/core` / CodeLabs LTS / `angular-eol`)

- **Effort:** hours-to-days. Drop-in NPM swap. Keep all existing controller code.
- **Risk:** Low — these forks publish to the same Angular 1 API surface.
- **Drawbacks:**
  - Still triggers CVE scanners that key off CPE rather than version (PCI scanners often flag any "AngularJS" string regardless of fork).
  - Maintenance quality varies wildly — `@angular-js/core` is decent; many forks are abandoned.
  - **Doesn't address textangular** (also EOL, also flagged) — would need a separate WYSIWYG swap.
- **Verdict:** Buys 12–18 months but doesn't actually close the audit finding.

### Option B — Migrate to modern Angular (v17/v18)

- **Effort:** **3–6 months** of focused work for a sole dev; ~1–2 months with a team.
- **Risk:** High. Every controller, template, directive, service, and route becomes a Component, template, Directive class, Injectable, and Router config. `ng-if/ng-repeat` → `@if/@for`. `$scope` → component class fields. `$http` → `HttpClient`. `ui-router` → Angular Router.
- **Drawbacks:**
  - **MicroFocus `ng-ias` / `ux-ias` libraries are Angular 1-only** — they'd have to be replaced entirely. These provide PWM's design-system styling (icons, form fields, dialogs). Need a substitute: Angular Material, NG-Zorro, or a custom component library.
  - **textangular has no Angular 17 equivalent** — would migrate to `tinymce`, `quill`, `ngx-editor`, or `ProseMirror`.
  - **Webpack 4 → modern build tooling** (Angular CLI uses esbuild/Vite). The custom `webpack.config.js` would be retired.
  - **The 20 HTML templates with `ng-*` attributes** all need conversion.
- **Verdict:** The right long-term answer but a major project.

### Option C — Rip out AngularJS, replace 4 modules with vanilla TS / Lit / Vue / React

- **Effort:** Similar to Option B but with the freedom to pick the simplest stack per module.
- **Risk:** High in flux; low once stabilized.
- **Drawbacks:** PWM's JSP-embedded pattern (a single component instance dropped into a JSP) is unusual for modern SPA frameworks. **Web Components / Lit are a natural fit** — each module becomes a custom element that the JSP loads as a single bundle, no framework runtime needed for the host page.
- **Verdict:** Possibly the cleanest if you're going to commit to a rewrite anyway.

### Option D — Stay on 1.8.3 and document the known-issue

- **Effort:** zero
- **Drawback:** doesn't close the audit finding
- **Verdict:** This is what `2.0.8` is currently doing.

---

## What "the upgrade may cause" — concrete breakage per path

| Path                                    | Specific risks to test in UI |
| --------------------------------------- | ---------------------------- |
| **A (LTS fork)**                        | • textangular still flagged — config-editor description fields may need replacement WYSIWYG.<br>• `@microfocus/ng-ias` peerDep `angular: >=1.2.0` may not accept the fork's package name — needs `package-lock.json` resolution check.<br>• Theme/styling: `ux-ias.css` may rely on Angular's `ng-cloak`/`ng-hide` selectors that the fork still emits. |
| **B (modern Angular)**                  | • **Loss of all `ng-ias` form/dialog/icon components** — config editor's settings dialogs, helpdesk's verification flow, peoplesearch's user-card UI all use ng-ias widgets. Each needs a replacement.<br>• **textangular's allowed-tag whitelist** drove server-side validation of the email-template field — replacement editor must produce identical output or the backend `Validator` needs updating.<br>• **Routing changes**: `ui-router` state names (`'config.settings.<setting>'`) might be referenced in JSP deep links — verify with grep.<br>• **Bootstrap**: each JSP loads its module via `ng-app="<module>.module"` — modern Angular bootstraps via `bootstrapApplication()` which doesn't match the attribute. The JSP integration point changes. |
| **C (Lit / Web Components)**            | Same as B for replacing ng-ias and textangular, plus you carry the burden of building a small design system. The win is no framework lock-in. |

---

## UI test matrix — what to validate before any PR

Independent of which path is chosen, these are the high-risk UI surfaces to manually test on the current 1.8.3 codebase to establish a baseline, then re-test after the upgrade:

1. **Config editor settings panels** — every input type (string, multi-string, form table, action lists, locale-bundle, profile lists, certificate import, file upload, password value).
2. **Config editor email templates** — textangular WYSIWYG round-trip: load → edit → save → reload, with bold/italic/links/lists.
3. **Helpdesk full flow** — user search, user-detail card, password set/clear, account unlock, OTP reset, response clear, verification challenge.
4. **PeopleSearch** — search by attribute, org chart, photo display, attribute panel with macros.
5. **Change-password ng panel** — strength meter, password suggestions modal, "show password" toggle.
6. **All locales** — ng-translate uses 1.x; some upgrades break placeholder syntax.
7. **All themes** (`pwm` default + any custom) — `ng-cloak` selectors may behave differently.
8. **PostMessage / parent-frame communication** — peoplesearch can run framed; if `vendor.js` bundle init order changes, framing may break.

---

## Recommended sequencing

1. **Re-enable Node analyzer in OWASP plugin** so this is visible in CI going forward. One-line `pom.xml` edit, no behavior change otherwise.
2. **Capture baseline UI screenshots / video** of the four module flows above on the current 2.0.8 build.
3. **Spike Option A** (community LTS fork) in a throwaway branch — pure NPM dependency swap, rebuild `vendor.js`, redeploy, run through the UI matrix. If it works, this is a 6–12 month bridge while Option B is planned.
4. **Defer Option B planning** until #730 + #731 + #732 are merged and the maintainer is back from blocked-on-debugging state. A migration of this size needs an upstream decision, not a contributor PR.

---

## When ready to test a candidate fix

Starting points:

- `client/angular/package.json` — swap `"angular"`, `"angular-aria"`, `"angular-sanitize"` to a community LTS package
- `client/angular/webpack.config.js` — likely unchanged for Option A
- Rebuild `pwm-client-*.jar` (requires Node — the `npm install` step is the one that currently fails locally if Node is missing)
- Drop the rebuilt JAR into a deployed WAR
- Walk the UI test matrix above

When the UI has been validated on a chosen approach, this analysis can be promoted to a PR description.

---

## Progress (Option B — modern Angular migration)

Branch: `issue-729-angular-migration` off `v2_0`.

### Foundation (complete)

- New Maven module **`client-modern/`** scaffolded, mirroring the existing `client/` pattern but producing a separate `pwm-client-modern-<version>.jar`. Uses `frontend-maven-plugin` to download Node 20.18.0 / npm 10.8.2 into `client-modern/angular/.node/` so the build is hermetic.
- Angular 19 workspace at **`client-modern/angular/`** with `@angular/elements` for Web-Component output. One Angular CLI "project" per PWM module, configured to build with the new `application` builder (esbuild under the hood).
- **Post-build script** `scripts/postbuild.mjs` collapses each module's `polyfills.js` + `main.js` into a single `dist/<module>.js` that the JSP loads with one `<script>` tag.
- **`client-modern/README.md`** documents the JSP integration pattern (drop a `<pwm-<module>>` custom element where the legacy `ng-controller` attribute lived; load one self-contained bundle per module) and the recipe for adding the next module to the workspace.

### First migrated module: `changepassword` (complete, pending UI test)

Smallest legacy module (81-line controller, one HTML template). Migrated to:

- `client-modern/angular/projects/changepassword/src/change-password.component.ts` — host component, exposed as `<pwm-changepassword>` custom element.
- `client-modern/angular/projects/changepassword/src/password-suggestions.component.{ts,html,scss}` — the random-password grid that mounts into the legacy `PWM_MAIN.showDialog` dialog body via Angular 14+ `createComponent()` (replaces the AngularJS `$compile` / `$templateCache` dance).
- `client-modern/angular/projects/changepassword/src/pwm-globals.d.ts` — typed declarations for the existing `window.PWM_MAIN` / `window.PWM_CHANGEPW` JavaScript helpers that PWM's non-AngularJS pages already use; the migrated component delegates to them so the dialog chrome and AJAX endpoint contracts stay identical during the multi-module migration window.

### Build verified locally

```
$ cd client-modern/angular && npm run build
Initial chunk files | Names         |  Raw size | Estimated transfer size
main.js             | main          | 118.69 kB |                35.34 kB
polyfills.js        | polyfills     |  34.59 kB |                11.33 kB
Application bundle generation complete. [1.468 seconds]
[postbuild]   wrote dist/changepassword.js (153325 bytes)
```

153 KB raw / ~47 KB gzipped per module, comparable to the legacy `vendor.js` + `changepassword.ng.js` pair this replaces — and most of the 153 KB is the Angular runtime + zone.js that subsequent migrated modules will not re-emit (each module ships its own runtime today; a shared runtime split-bundle is a later optimization).

### Next steps (in order)

1. **Wire `<pwm-changepassword>` into `webapp/.../changepassword.jsp`** behind a build-time toggle so the existing AngularJS path remains the default until the new path is validated. (Not yet done — keeping the change scoped to the foundation + first module.)
2. **Deploy the WAR with both bundles** and walk the change-password "More" / random-suggestion flow end-to-end against a live PWM backend. Capture before/after screenshots; verify password copy-to-form, "More", "Cancel", and accessibility (the `aria-label` on the trigger icon is new in this version — verify the legacy JSP still has its `title` attribute too).
3. **Migrate `configeditor`** next. Smallest of the three remaining; the controller is 48 lines but the textangular WYSIWYG dependency needs replacing (`ngx-editor` or `quill` are the most likely candidates).
4. **Migrate `helpdesk`** then **`peoplesearch`** — both depend on the `@microfocus/ng-ias` design-system library which is Angular 1-only. The migration of these two requires a replacement design system (Angular Material is the safest default).
5. **Remove the `client/` Maven module** from the reactor once all four JSPs are flipped and the migration window is closed.
6. **Re-enable the OWASP Node analyzer** (`pom.xml` — `nodeAnalyzerEnabled` / `yarnAuditAnalyzerEnabled`) once the AngularJS dependency is gone, so this class of finding stays visible in CI.

### Files added on this branch

```
client-modern/
├── README.md
├── pom.xml
└── angular/
    ├── .gitignore
    ├── angular.json
    ├── package.json
    ├── package-lock.json          (generated by npm install; committed)
    ├── tsconfig.json
    ├── tsconfig.app.json
    ├── tsconfig.spec.json
    ├── scripts/
    │   └── postbuild.mjs
    └── projects/
        └── changepassword/
            └── src/
                ├── main.ts
                ├── change-password.component.ts
                ├── password-suggestions.component.{ts,html,scss}
                └── pwm-globals.d.ts
```

### What still needs to happen on this branch before a PR

- Modify a JSP (likely `changepassword.jsp`) to load the new bundle and add the `<pwm-changepassword>` tag. Currently the new module builds, but is not yet referenced by the webapp — so nothing changes for end users.
- Hand-test the deployed WAR against a live PWM backend per the UI test matrix above.
- Re-verify gzipped bundle size deltas and update this section with the actual numbers.
- Decide whether to add `client-modern` to the parent reactor `pom.xml`. Today it is buildable standalone with `mvn -pl client-modern -am package`; adding it to the reactor is one line but couples it to the existing `client/` module's build health.

