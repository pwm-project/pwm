/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2026 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

/**
 * Post-build step: collapse each `dist/<module>/` Angular bundle into a single
 * `dist/<module>.js` file that JSPs can load with one `<script>` tag.
 *
 * The Angular CLI's `application` builder emits a small `polyfills.js` (zone.js)
 * and a `main.js` per project.  For a custom-element drop-in we want one
 * self-contained script: this script reads the two outputs in dependency order
 * (polyfills first), concatenates them, and writes the result as a sibling of
 * the project subdirectory so Maven's resources plugin can copy it directly
 * into the webjar.
 *
 * Outputs (relative to angular/dist):
 *   dist/changepassword.js          ← consumed by the JSP
 *   dist/changepassword.js.map      ← if source maps are emitted
 *   dist/<module>/main.js           ← intermediate, ignored by the webjar copy
 *   dist/<module>/polyfills.js      ← intermediate, ignored by the webjar copy
 */

import { promises as fs } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const distDir = join(here, '..', 'dist');

const modules = await listModuleDirectories(distDir);
if (modules.length === 0) {
    console.warn(`[postbuild] no module subdirectories found under ${distDir} - nothing to do`);
    process.exit(0);
}

for (const moduleName of modules) {
    await bundleModule(moduleName);
}

console.log(`[postbuild] bundled ${modules.length} module(s): ${modules.join(', ')}`);

async function listModuleDirectories(root) {
    let entries;
    try {
        entries = await fs.readdir(root, { withFileTypes: true });
    } catch (err) {
        if (err.code === 'ENOENT') {
            return [];
        }
        throw err;
    }
    return entries.filter((e) => e.isDirectory()).map((e) => e.name);
}

async function bundleModule(moduleName) {
    const moduleDir = join(distDir, moduleName);
    const polyfills = await readIfPresent(join(moduleDir, 'polyfills.js'));
    const main = await readIfPresent(join(moduleDir, 'main.js'));

    if (!main) {
        throw new Error(`[postbuild] ${moduleName}: expected dist/${moduleName}/main.js, none found`);
    }

    // Polyfills must come first so zone.js patches globals before the component bootstraps.
    const combined = [
        `/* PWM client-modern bundle: ${moduleName} */`,
        polyfills ?? '',
        main,
    ]
        .filter(Boolean)
        .join('\n');

    const outFile = join(distDir, `${moduleName}.js`);
    await fs.writeFile(outFile, combined, 'utf8');
    console.log(`[postbuild]   wrote ${outFile} (${combined.length} bytes)`);
}

async function readIfPresent(path) {
    try {
        return await fs.readFile(path, 'utf8');
    } catch (err) {
        if (err.code === 'ENOENT') {
            return null;
        }
        throw err;
    }
}
