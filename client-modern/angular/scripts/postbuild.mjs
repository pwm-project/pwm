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
 * The Angular CLI's `application` builder emits up to three files per project:
 *
 *   dist/<module>/polyfills.js   (only present if `polyfills` is non-empty)
 *   dist/<module>/main.js        (always - the application code)
 *   dist/<module>/styles.css     (only present if the project declares styles)
 *
 * For a custom-element drop-in we want a single self-contained script.  This
 * script reads the three outputs in dependency order:
 *
 *   1. polyfills (must run first - eg. zone.js patches globals)
 *   2. styles wrapped in a runtime `<style>` injection (must be in the DOM
 *      before any element that depends on them attaches)
 *   3. main (the application code that defines the custom element)
 *
 * ...concatenates them, and writes the result as a sibling of the project
 * subdirectory so Maven's resources plugin can copy it directly into the
 * webjar.
 *
 * Outputs (relative to angular/dist):
 *   dist/<module>.js                 ← consumed by the JSP
 *   dist/<module>/{main,polyfills,styles}.{js,css}   ← intermediates, ignored
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
    const styles = await readIfPresent(join(moduleDir, 'styles.css'));
    const main = await readIfPresent(join(moduleDir, 'main.js'));

    if (!main) {
        throw new Error(`[postbuild] ${moduleName}: expected dist/${moduleName}/main.js, none found`);
    }

    const parts = [`/* PWM client-modern bundle: ${moduleName} */`];
    if (polyfills) {
        parts.push(polyfills);
    }
    if (styles) {
        parts.push(stylesInjector(moduleName, styles));
    }
    parts.push(main);

    const combined = parts.join('\n');
    const outFile = join(distDir, `${moduleName}.js`);
    await fs.writeFile(outFile, combined, 'utf8');
    console.log(`[postbuild]   wrote ${outFile} (${combined.length} bytes)`);
}

/**
 * Emit a self-executing snippet that injects the bundled CSS into document.head
 * as a single <style> element on first run.  The IIFE is idempotent (won't
 * re-inject if the script is loaded twice) and uses JSON.stringify so any
 * characters in the CSS that would be problematic in a JS string literal are
 * safely escaped.
 */
function stylesInjector(moduleName, css) {
    const marker = `pwm-client-modern:${moduleName}:styles`;
    return [
        '(function(){',
        `  if (document.head.querySelector('style[data-pwm-bundle=${JSON.stringify(marker).slice(1, -1)}]')) return;`,
        '  var s = document.createElement("style");',
        `  s.setAttribute("data-pwm-bundle", ${JSON.stringify(marker)});`,
        `  s.textContent = ${JSON.stringify(css)};`,
        '  document.head.appendChild(s);',
        '})();',
    ].join('\n');
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
