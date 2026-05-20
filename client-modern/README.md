# pwm-client-modern

Modern (Angular 19 + `@angular/elements`) replacement for PWM's EOL AngularJS 1.8.3
client. Tracks issue [#729](https://github.com/pwm-project/pwm/issues/729) — see
`../issue-729-angularjs-eol-analysis.md` for the full assessment that motivated
this module.

This module **coexists** with the legacy `client/` module during the migration.
JSP pages can be migrated one at a time; until every page is migrated, both
bundles ship in the WAR.

## Migration status

| Module           | Legacy (`client/`)         | Modern (`client-modern/`)         |
| ---------------- | -------------------------- | --------------------------------- |
| `changepassword` | AngularJS 1.8.3 controller | ✅ Angular 19 custom element     |
| `configeditor`   | AngularJS 1.8.3 + textangular | ⏳ pending — needs WYSIWYG swap |
| `helpdesk`       | AngularJS 1.8.3 + ng-ias   | ⏳ pending — needs `ng-ias` replacement |
| `peoplesearch`   | AngularJS 1.8.3 + ng-ias   | ⏳ pending — needs `ng-ias` replacement |

## Project layout

```
client-modern/
├── pom.xml                       # Maven module, produces pwm-client-modern-<v>.jar
└── angular/
    ├── angular.json              # CLI workspace - one "project" per module
    ├── package.json              # Angular 19, @angular/elements, zone.js
    ├── tsconfig*.json
    ├── scripts/
    │   └── postbuild.mjs         # Collapses dist/<module>/{polyfills,main}.js into dist/<module>.js
    └── projects/
        └── changepassword/       # First migrated module
            └── src/
                ├── main.ts                                # Registers <pwm-changepassword>
                ├── change-password.component.ts           # Host component
                ├── password-suggestions.component.{ts,html,scss}  # The dialog body
                └── pwm-globals.d.ts                       # Declarations for PWM_MAIN / PWM_CHANGEPW
```

## Building

From `client-modern/angular/`:

```bash
npm install            # one-time
npm run build          # all modules
npm run build:changepassword   # one module
npm run watch          # incremental dev build
```

The Maven build (`mvn -pl client-modern -am package`) drives the same steps via
`frontend-maven-plugin` — it downloads its own Node/npm into `angular/.node/` so
the build is hermetic and does not depend on the host's Node version.

The output is a webjar JAR (`pwm-client-modern-<version>.jar`) containing:

```
META-INF/resources/webjars/pwm-client-modern/<version>/
├── changepassword.js
└── changepassword/        # raw CLI output, kept for source-map debugging only
```

## JSP integration pattern

Each migrated module becomes a **custom element** (Web Component). The JSP drops
the tag where the legacy `ng-controller` attribute lived and loads the bundle
with a single `<script>`:

```jsp
<%-- inside changepassword.jsp --%>
<%-- old: <div ng-app="changepassword.module" ng-controller="ChangePasswordController as $ctrl"> --%>
<%-- new: <pwm-changepassword></pwm-changepassword> drops in next to the form --%>

<pwm-changepassword></pwm-changepassword>

<%-- module bundle --%>
<pwm:script-ref url="/public/resources/webjars/pwm-client-modern/changepassword.js" />
```

Custom Element names must contain a hyphen — that is the entire integration
contract. No `ng-app`, no `ng-controller`, no `vendor.js`, no AngularJS runtime
on the page.

Each bundle is **self-contained**: it ships its own Angular runtime + zone.js.
This is intentional. Pages that have not been migrated continue to load the
legacy `vendor.js` from the old `client/` webjar; they are completely
independent of each other.

## Why `@angular/elements` instead of bootstrapping a full app

PWM's existing architecture renders pages server-side via JSP and drops small
interactive widgets into specific DOM nodes. That maps cleanly onto Custom
Elements: one tag per widget, no client-side routing, no SPA shell, no
`<router-outlet>`, no `index.html` to template.

This pattern also keeps each migrated module independently shippable: the
backend `pwm-server` does not need to know whether a given page has been
migrated yet, the change is purely cosmetic on the JSP side.

## Adding a new module to the workspace

When migrating the next module (say, `configeditor`):

1. Add a new entry to `angular/angular.json` under `projects.` mirroring the
   `changepassword` entry. Update `build.options.browser` to point at the new
   module's `main.ts`.
2. Add `projects/configeditor/src/main.ts` that calls `createCustomElement` for
   the new component, mirroring `projects/changepassword/src/main.ts`.
3. Add the corresponding component(s) under `projects/configeditor/src/`.
4. Add a `build:configeditor` script to `package.json`.
5. The `postbuild.mjs` script auto-discovers any `dist/<module>/` subdirectory,
   so it produces `dist/configeditor.js` with no further changes.
6. Update the migration-status table at the top of this README.
7. Update the JSP to load `pwm-client-modern/configeditor.js` instead of
   `pwm-client/vendor.js` + `pwm-client/configeditor.ng.js`.

## Why we keep using `window.PWM_MAIN` / `window.PWM_CHANGEPW`

The legacy `pwmHelper.js` / `changepassword.js` scripts (which are NOT part of
the AngularJS bundle — they live in `webapp/src/main/webapp/public/resources/js/`)
expose page-wide JavaScript helpers used by every JSP, AngularJS or not. They
own:

- localized string lookup (`PWM_MAIN.showString`)
- the dialog implementation that matches the rest of the PWM UI
- the random-password fetch endpoint contract (`PWM_CHANGEPW.beginFetchRandoms`)
- the password-copy-to-form behavior (`PWM_CHANGEPW.copyToPasswordFields`)

Re-implementing those inside each Angular module would produce a visually
inconsistent UI during the multi-module migration window. Instead, the
component delegates to those globals via `window.PWM_MAIN` / `window.PWM_CHANGEPW`
(typed in `pwm-globals.d.ts`). When the migration is complete and every JSP
uses Angular components, those globals can be replaced with Angular services
in a follow-up change.

## Versions

- Angular 19 (released 2024-11) — picked because it is the most recent version
  with mature standalone components and the new control-flow syntax.
- TypeScript 5.6
- Node 20.x LTS (via `frontend-maven-plugin`); local dev works on Node 20+.
- zone.js 0.15

These can be bumped independently of Angular major versions. The custom-element
contract is stable across Angular major versions.
