/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2026 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Quill from 'quill';

/**
 * Replacement for the {@code <div text-angular ng-model="htmlText" ta-toolbar="...">}
 * widget that {@code configeditor-settings-email.js} used to inject into the email-
 * template editor dialog.  Replaces the EOL {@code textangular} dependency (which
 * was the source of the v1.3.10 AngularJS fragment flagged in issue #729) with
 * Quill 2.x, a modern actively-maintained WYSIWYG editor.
 *
 * <p>Usage from legacy callers is:</p>
 * <pre>
 *   PWM_MAIN.showDialog({
 *     text: '&lt;pwm-html-editor id="..."&gt;&lt;/pwm-html-editor&gt;',
 *     loadFunction: function() {
 *       document.getElementById('...').value = existingHtml;
 *     },
 *     okAction: function() {
 *       savedHtml = document.getElementById('...').value;
 *     }
 *   });
 * </pre>
 *
 * <p>The element exposes a {@code value} property (HTML string) for both reads and
 * writes, dispatches a bubbling {@code input} event on every change so legacy
 * dirty-tracking code can observe edits, and tears Quill down in
 * {@code disconnectedCallback} so dialogs can be opened and closed repeatedly
 * without leaking listeners.</p>
 */
class HtmlEditorElement extends HTMLElement {
    private quill: Quill | null = null;
    /**
     * Pending value written via the {@code value} setter before Quill has mounted.
     * The first {@code connectedCallback} flushes it into the editor.
     */
    private pendingValue: string | null = null;

    /**
     * Toolbar that approximates the textangular toolbar previously declared inline in
     * configeditor-settings-email.js.  Quill does not have a true HTML source-view button
     * out of the box; if admins need it we can add a "code-block" group later.
     */
    private static readonly TOOLBAR: ReadonlyArray<unknown> = [
        [{ header: [1, 2, 3, 4, 5, 6, false] }],
        ['bold', 'italic', 'underline', 'strike'],
        [{ list: 'ordered' }, { list: 'bullet' }],
        [{ align: [] }],
        [{ indent: '-1' }, { indent: '+1' }],
        ['blockquote'],
        ['link', 'image', 'video'],
        ['clean'],
    ];

    connectedCallback(): void {
        if (this.quill) {
            return;
        }

        // Quill mounts in its own div so we can keep the host element clean (no extra
        // attributes injected by Quill onto our public DOM surface).
        const editorHost = document.createElement('div');
        editorHost.className = 'pwm-html-editor-host';
        this.appendChild(editorHost);

        this.quill = new Quill(editorHost, {
            theme: 'snow',
            placeholder: '',
            modules: {
                toolbar: HtmlEditorElement.TOOLBAR as never,
            },
        });

        if (this.pendingValue !== null) {
            this.writeIntoEditor(this.pendingValue);
            this.pendingValue = null;
        }

        // Bubbling input events let legacy dirty-tracking / save-button-enabling code
        // observe edits without coupling to the Quill instance directly.
        this.quill.on('text-change', () => {
            this.dispatchEvent(new Event('input', { bubbles: true }));
        });
    }

    disconnectedCallback(): void {
        // Quill 2 does not expose a destroy() method; drop our reference and let GC
        // reclaim the editor + toolbar DOM (which sit inside this host element and are
        // about to be removed from the document).
        this.quill = null;
    }

    /** HTML string round-trip - the legacy contract. */
    get value(): string {
        if (!this.quill) {
            return this.pendingValue ?? '';
        }
        return this.quill.root.innerHTML;
    }

    set value(html: string) {
        if (!this.quill) {
            this.pendingValue = html ?? '';
            return;
        }
        this.writeIntoEditor(html ?? '');
    }

    private writeIntoEditor(html: string): void {
        if (!this.quill) {
            return;
        }
        // dangerouslyPasteHTML runs Quill's HTML matchers so structures like <h1>, <ul>,
        // alignment classes etc. are preserved instead of being flattened to text nodes.
        this.quill.clipboard.dangerouslyPasteHTML(html);
    }
}

if (!customElements.get('pwm-html-editor')) {
    customElements.define('pwm-html-editor', HtmlEditorElement);
}
