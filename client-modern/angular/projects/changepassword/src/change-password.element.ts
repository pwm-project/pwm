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

/**
 * Vanilla Web Component replacement for the AngularJS 1.x
 * {@code ChangePasswordController} from
 * {@code client/angular/src/modules/changepassword/changepassword.controller.ts}.
 *
 * <p>The change-password module never needed a framework: all it does is
 * (a) render one click target (the "retweet" icon next to the new-password
 * field) and (b) on click, open the existing PWM dialog and populate it with
 * the random-password suggestions grid.  The bulk of the page is server-
 * rendered JSP, the dialog chrome is the legacy {@code PWM_MAIN.showDialog},
 * and the random-password fetch is the legacy
 * {@code PWM_CHANGEPW.beginFetchRandoms} - none of which need to live inside a
 * framework.  See issue #729 and {@code issue-729-angularjs-eol-analysis.md}.</p>
 *
 * <p>By dropping the Angular 19 runtime + zone.js polyfill, this bundle shrinks
 * from ~153 KB to single-digit KB and has no bootstrap that can fail.  The
 * tradeoff is no template syntax, but the entire interactive surface is so
 * small that the template can be emitted as a string with negligible
 * complexity.</p>
 *
 * <p>Larger PWM modules that genuinely benefit from a framework (helpdesk and
 * peoplesearch) can still pick Lit or Angular when they are migrated; nothing
 * in this approach precludes that.</p>
 */
class ChangePasswordElement extends HTMLElement {
    private rendered = false;

    connectedCallback(): void {
        if (this.rendered) {
            return;
        }
        this.rendered = true;

        // Render by making the host element itself look and act like the legacy
        // <div id="autogenerate-icon" class="pwm-icon pwm-icon-retweet icon_button" ...>
        // that this custom element replaces.  Putting the icon classes on the host
        // (rather than on a child <button>) preserves the legacy
        // `.icon_button { float: right }` layout exactly - a wrapper <button> would
        // bring its own browser-default border / padding / font-size that the JSP
        // page CSS does not anticipate.
        //
        // Keeping id="autogenerate-icon" on the host also satisfies the legacy
        // PWM_MAIN.showTooltip({ id: "autogenerate-icon", ... }) lookup at
        // changepassword.js:378.
        this.id = 'autogenerate-icon';
        this.className = 'pwm-icon pwm-icon-retweet icon_button';
        this.style.cursor = 'pointer';
        this.tabIndex = 0;
        this.setAttribute('role', 'button');
        this.setAttribute('aria-label', this.lookupString('Title_RandomPasswords'));
        this.addEventListener('click', () => this.openSuggestionsDialog());
        this.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                this.openSuggestionsDialog();
            }
        });
    }

    /** Open the legacy PWM dialog and populate it with the suggestions grid. */
    private openSuggestionsDialog(): void {
        const pwmMain = window.PWM_MAIN;
        if (!pwmMain) {
            console.warn('[pwm-changepassword] PWM_MAIN is not loaded yet');
            return;
        }
        pwmMain.showDialog({
            title: this.lookupString('Title_RandomPasswords'),
            dialogClass: 'narrow',
            text: '',
            showOk: false,
            showClose: true,
            loadFunction: () => this.populateDialogBody(),
        });
    }

    /**
     * Replace the dialog body with the 10x2 suggestions grid + More/Cancel
     * buttons, wire up event handlers, then kick off the legacy random fetch
     * which will write generated passwords into #randomGen0..#randomGen19 by
     * DOM id.
     */
    private populateDialogBody(): void {
        const host = document.querySelector<HTMLElement>(
            '#dialogPopup .dialogBody, #html5Dialog .dialogBody',
        );
        if (!host) {
            return;
        }

        host.innerHTML = this.buildBodyHtml();

        host.querySelectorAll<HTMLElement>('.link-randomPasswordValue').forEach((cell) => {
            cell.addEventListener('click', (event) => {
                const target = event.target as HTMLElement | null;
                const password = target?.textContent;
                if (!password) {
                    return;
                }
                // Legacy copyToPasswordFields populates only password1 and then focuses
                // password2 (forcing the user to retype).  Since the user explicitly
                // selected a generated password, populate password2 as well so they do
                // not have to type it twice; dispatch an input event so any legacy
                // strength-meter / confirmation-checkmark listeners on the confirm
                // field re-run with the new value.
                window.PWM_CHANGEPW.copyToPasswordFields(password);
                const confirmField = document.getElementById('password2');
                if (confirmField instanceof HTMLInputElement) {
                    confirmField.value = password;
                    confirmField.dispatchEvent(new Event('input', { bubbles: true }));
                    confirmField.dispatchEvent(new Event('change', { bubbles: true }));
                }
            });
        });

        host.querySelector('#moreRandomsButton')?.addEventListener('click', () => {
            window.PWM_CHANGEPW.beginFetchRandoms({});
        });

        host.querySelector('#cancelRandomsButton')?.addEventListener('click', () => {
            window.PWM_MAIN.closeWaitDialog('dialogPopup');
        });

        window.PWM_CHANGEPW.beginFetchRandoms({});
    }

    private buildBodyHtml(): string {
        const intro = this.escape(this.lookupString('Display_PasswordGeneration'));
        const moreLabel = this.escape(this.lookupString('Button_More'));
        const cancelLabel = this.escape(this.lookupString('Button_Cancel'));

        const rows: string[] = [];
        for (let i = 0; i < 20; i += 2) {
            rows.push(
                `<tr class="noborder">
                    <td class="noborder" style="padding-bottom:5px;" width="20%">
                        <div class="link-randomPasswordValue" id="randomGen${i}" style="visibility:visible;"></div>
                    </td>
                    <td class="noborder" style="padding-bottom:5px;" width="20%">
                        <div class="link-randomPasswordValue" id="randomGen${i + 1}" style="visibility:visible;"></div>
                    </td>
                </tr>`,
            );
        }

        return `
            <div class="dialogBody narrow">
                ${intro}
                <br><br>
                <table class="noborder"><tbody>${rows.join('')}</tbody></table>
                <br><br>
                <table class="noborder"><tbody>
                    <tr class="noborder">
                        <td class="noborder">
                            <button type="button" class="btn" id="moreRandomsButton">
                                <span class="btn-icon pwm-icon pwm-icon-refresh"></span>
                                ${moreLabel}
                            </button>
                        </td>
                        <td class="noborder" style="text-align:right;">
                            <button type="button" class="btn" id="cancelRandomsButton">
                                <span class="btn-icon pwm-icon pwm-icon-times"></span>
                                ${cancelLabel}
                            </button>
                        </td>
                    </tr>
                </tbody></table>
            </div>`;
    }

    /**
     * Localized string lookup with a safe fallback.  PWM_MAIN may not exist
     * during very early page lifecycle phases (eg. if the bundle runs before
     * the legacy changepassword.js is parsed); fall back to the key name so
     * the page still renders.
     */
    private lookupString(key: string): string {
        return window.PWM_MAIN?.showString?.(key) ?? key;
    }

    /** Minimal HTML escape for user-facing strings going into innerHTML. */
    private escape(text: string): string {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

if (!customElements.get('pwm-changepassword')) {
    customElements.define('pwm-changepassword', ChangePasswordElement);
}
