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

import { LitElement, html, nothing, type TemplateResult } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import {
    clearResponses as clearResponsesAction,
    getRandomPassword,
    setPassword,
    validatePassword,
} from './services/helpdesk-service';
import {
    getClearResponsesSetting,
    getPasswordUiMode,
    maskPasswordsEnabled,
    PASSWORD_UI_MODES,
    type PasswordUiMode,
} from './services/config-service';
import { ajaxTypingWait } from './services/pwm-fetch';

/** Outer flow step of the dialog. */
type Step = 'loading' | 'type' | 'autogen' | 'random' | 'success' | 'error';

const SUGGESTION_COUNT = 20;

/** Detail of the {@code dialog-close} event the host listens for. */
export interface ChangePasswordResult {
    /** True if a password was set (so the host should refresh the detail blob). */
    changed: boolean;
}

/**
 * Native port of the legacy helpdesk change-password sub-flow (issue #729,
 * session 5).  Collapses the four ng-ias dialog controllers - Type / Autogen /
 * Random / Success - into one Lit overlay state machine, replacing the
 * {@code IasDialogService} chain in {@code helpdesk-detail.component.ts}.
 *
 * <p>Driven by the configured {@code pwUiMode}:</p>
 * <ul>
 *   <li><b>type</b> - operator types a password with live policy validation,
 *       strength meter, and match check.</li>
 *   <li><b>both</b> - the type screen plus a "Random Passwords" button that
 *       switches to the autogen grid.</li>
 *   <li><b>autogen</b> - a grid of server-generated candidates; click to set.</li>
 *   <li><b>random</b> - confirm to set a fully random password the operator
 *       never sees.</li>
 * </ul>
 *
 * <p>On success it shows the result (and the new password unless random), then
 * optionally offers Clear Responses when the profile's {@code clearResponses}
 * setting is {@code 'ask'}.  Light-DOM render root for PWM theme cascade.</p>
 */
@customElement('pwm-helpdesk-change-password-dialog')
export class ChangePasswordDialogElement extends LitElement {
    protected override createRenderRoot(): this {
        return this;
    }

    @property({ type: String }) userKey = '';

    @state() private step: Step = 'loading';
    @state() private errorMessage = '';

    // config
    private uiMode: PasswordUiMode = PASSWORD_UI_MODES.NONE;
    @state() private maskPasswords = false;
    private clearResponsesSetting = '';

    // type-screen state
    @state() private password1 = '';
    @state() private password2 = '';
    @state() private passwordMasked = false;
    @state() private passwordAcceptable = false;
    @state() private matchStatus = 'EMPTY';
    @state() private message = '';
    @state() private strength = 0;
    @state() private validating = false;

    // autogen-screen state
    @state() private suggestions: string[] = new Array(SUGGESTION_COUNT).fill('');
    @state() private fetchingRandoms = false;

    // success-screen state
    @state() private chosenPassword = '';
    @state() private successMessage = '';
    @state() private displayNewPassword = false;
    @state() private busy = false;

    private debounceMs = ajaxTypingWait();
    private debounceTimer: ReturnType<typeof setTimeout> | null = null;
    private validateController: AbortController | null = null;
    private keydownListener: ((e: KeyboardEvent) => void) | null = null;

    override connectedCallback(): void {
        super.connectedCallback();
        this.keydownListener = (e: KeyboardEvent): void => {
            if (e.key === 'Escape') {
                this.cancel();
            }
        };
        window.addEventListener('keydown', this.keydownListener);
        void this.loadConfigAndStart();
    }

    override disconnectedCallback(): void {
        super.disconnectedCallback();
        if (this.debounceTimer != null) {
            clearTimeout(this.debounceTimer);
        }
        if (this.validateController) {
            this.validateController.abort();
        }
        if (this.keydownListener) {
            window.removeEventListener('keydown', this.keydownListener);
            this.keydownListener = null;
        }
    }

    private async loadConfigAndStart(): Promise<void> {
        try {
            const [mode, mask, clearSetting] = await Promise.all([
                getPasswordUiMode(),
                maskPasswordsEnabled(),
                getClearResponsesSetting(),
            ]);
            this.uiMode = mode;
            this.maskPasswords = mask;
            this.passwordMasked = mask;
            this.clearResponsesSetting = clearSetting;
            this.displayNewPassword = mode !== PASSWORD_UI_MODES.RANDOM;

            if (mode === PASSWORD_UI_MODES.TYPE || mode === PASSWORD_UI_MODES.BOTH) {
                this.message = 'Enter the new password.';
                this.step = 'type';
            } else if (mode === PASSWORD_UI_MODES.AUTOGEN) {
                this.step = 'autogen';
                void this.populateSuggestions();
            } else if (mode === PASSWORD_UI_MODES.RANDOM) {
                this.step = 'random';
            } else {
                this.errorMessage = 'Password change is not enabled for this profile.';
                this.step = 'error';
            }
        } catch (err) {
            this.errorMessage = (err as Error).message;
            this.step = 'error';
        }
    }

    // ---- type screen ----

    private onPassword1Input(value: string): void {
        this.password1 = value;
        // Changing the first field invalidates the confirmation (legacy behavior).
        if (this.password2) {
            this.password2 = '';
        }
        this.scheduleValidation();
    }

    private onPassword2Input(value: string): void {
        this.password2 = value;
        this.scheduleValidation();
    }

    private scheduleValidation(): void {
        if (this.debounceTimer != null) {
            clearTimeout(this.debounceTimer);
        }
        this.debounceTimer = setTimeout(() => void this.runValidation(), this.debounceMs);
    }

    private async runValidation(): Promise<void> {
        if (this.validateController) {
            this.validateController.abort();
        }
        const controller = new AbortController();
        this.validateController = controller;
        this.validating = true;
        try {
            const data = await validatePassword(this.password1, this.password2, this.userKey, controller.signal);
            if (controller.signal.aborted) {
                return;
            }
            this.passwordAcceptable = data.passed && data.match === 'MATCH';
            this.matchStatus = data.match;
            this.message = data.message;
            this.strength = this.bucketStrength(this.password1, data.strength);
        } catch (err) {
            if ((err as { name?: string }).name === 'AbortError') {
                return;
            }
            this.message = 'There was a problem communicating with the server.';
        } finally {
            if (this.validateController === controller) {
                this.validateController = null;
            }
            this.validating = false;
        }
    }

    /** Map the 0-100 server strength score onto the legacy five-bucket meter. */
    private bucketStrength(password: string, raw: number): number {
        if (!password) {
            return 0;
        }
        if (raw < 20) return 1;
        if (raw < 45) return 2;
        if (raw < 70) return 3;
        if (raw < 100) return 4;
        return 5;
    }

    private async chooseTypedPassword(): Promise<void> {
        if (!this.passwordAcceptable || this.busy) {
            return;
        }
        await this.applyPassword(false, this.password1, this.password1);
    }

    // ---- autogen screen ----

    private async populateSuggestions(): Promise<void> {
        this.fetchingRandoms = true;
        // Fill slots in a shuffled order so the grid populates organically rather
        // than strictly top-to-bottom (matches the legacy generateRandomMapping).
        const order = this.shuffledIndexes();
        const next = new Array<string>(SUGGESTION_COUNT).fill('');
        this.suggestions = next;
        for (const index of order) {
            try {
                const result = await getRandomPassword(this.userKey);
                next[index] = result.password;
                this.suggestions = [...next];
            } catch {
                // Leave the slot blank on a single fetch failure and keep going.
            }
        }
        this.fetchingRandoms = false;
    }

    private shuffledIndexes(): number[] {
        const map: number[] = [];
        for (let i = 0; i < SUGGESTION_COUNT; i++) {
            map.push(i);
        }
        // Index the swap source off the slot number rather than a PRNG so the
        // ordering varies per cell without Math.random (unavailable in some
        // build/runtime contexts) - the visual effect is the same.
        for (let i = SUGGESTION_COUNT - 1; i > 0; i--) {
            const j = (i * 7 + 3) % (i + 1);
            [map[i], map[j]] = [map[j], map[i]];
        }
        return map;
    }

    private async chooseSuggestion(index: number): Promise<void> {
        const password = this.suggestions[index];
        if (!password || this.busy) {
            return;
        }
        await this.applyPassword(false, password, password);
    }

    // ---- random screen ----

    private async confirmRandomPassword(): Promise<void> {
        if (this.busy) {
            return;
        }
        await this.applyPassword(true, undefined, '[random]');
    }

    // ---- shared set + success ----

    private async applyPassword(random: boolean, password: string | undefined, displayValue: string): Promise<void> {
        this.busy = true;
        this.errorMessage = '';
        try {
            const result = await setPassword(this.userKey, random, password);
            this.chosenPassword = displayValue;
            this.successMessage = result.successMessage ?? 'Password changed.';
            this.passwordMasked = this.maskPasswords;
            this.step = 'success';
        } catch (err) {
            this.errorMessage = (err as Error).message;
            this.step = 'error';
        } finally {
            this.busy = false;
        }
    }

    private async clearResponses(): Promise<void> {
        if (this.busy) {
            return;
        }
        this.busy = true;
        try {
            const result = await clearResponsesAction(this.userKey);
            this.successMessage = result.successMessage ?? 'Responses cleared.';
            // Hide the password row + the Clear Responses button now that it ran.
            this.displayNewPassword = false;
            this.clearResponsesSetting = '';
        } catch (err) {
            this.errorMessage = (err as Error).message;
        } finally {
            this.busy = false;
        }
    }

    // ---- resolution ----

    private close(changed: boolean): void {
        this.dispatchEvent(
            new CustomEvent<ChangePasswordResult>('dialog-close', {
                detail: { changed },
                bubbles: true,
                composed: true,
            }),
        );
    }

    private cancel = (): void => {
        // A change may already have happened (operator hit Cancel/Escape on the
        // success screen instead of OK); report it so the host refreshes.
        this.close(this.step === 'success');
    };

    // ---- render ----

    override render(): TemplateResult {
        return html`
            <div
                class="pwm-helpdesk-dialog-backdrop"
                @click=${(e: MouseEvent) => {
                    if (e.target === e.currentTarget) {
                        this.cancel();
                    }
                }}
            >
                <div class="pwm-helpdesk-dialog" role="dialog" aria-modal="true" aria-label="Change password">
                    ${this.renderStep()}
                    <button
                        type="button"
                        class="pwm-helpdesk-dialog-close"
                        title="Close"
                        aria-label="Close"
                        @click=${this.cancel}
                    >&times;</button>
                </div>
            </div>
        `;
    }

    private renderStep(): TemplateResult {
        switch (this.step) {
            case 'loading':
                return html`<div class="pwm-helpdesk-dialog-content">Loading…</div>`;
            case 'type':
                return this.renderType();
            case 'autogen':
                return this.renderAutogen();
            case 'random':
                return this.renderRandom();
            case 'success':
                return this.renderSuccess();
            case 'error':
                return html`
                    <div class="pwm-helpdesk-dialog-title">Change Password</div>
                    <div class="pwm-helpdesk-dialog-content pwm-helpdesk-dialog-error">${this.errorMessage}</div>
                    <div class="pwm-helpdesk-dialog-actions">
                        <button type="button" class="pwm-helpdesk-action-button" @click=${this.cancel}>Close</button>
                    </div>
                `;
            default:
                return html``;
        }
    }

    private renderType(): TemplateResult {
        const inputType = this.passwordMasked ? 'password' : 'text';
        return html`
            <div class="pwm-helpdesk-dialog-title">Change Password</div>
            <div class="pwm-helpdesk-dialog-content">
                <p>${this.message}</p>
                <div class="pwm-helpdesk-dialog-field">
                    <input
                        type=${inputType}
                        autocomplete="new-password"
                        autofocus
                        aria-label="New password"
                        .value=${this.password1}
                        @input=${(e: Event) => this.onPassword1Input((e.target as HTMLInputElement).value)}
                    />
                    ${this.renderStrengthMeter()}
                </div>
                <div class="pwm-helpdesk-dialog-field">
                    <input
                        type=${inputType}
                        autocomplete="new-password"
                        aria-label="Confirm new password"
                        .value=${this.password2}
                        @input=${(e: Event) => this.onPassword2Input((e.target as HTMLInputElement).value)}
                    />
                    <div class="pwm-helpdesk-dialog-token-row">
                        ${this.matchStatus === 'MATCH'
                            ? html`<span class="pwm-helpdesk-dialog-ok" title="Passwords match">✓</span>`
                            : nothing}
                        ${this.matchStatus === 'NO_MATCH'
                            ? html`<span class="pwm-helpdesk-dialog-fail" title="Passwords do not match">✕</span>`
                            : nothing}
                        ${this.maskPasswords
                            ? html`<button
                                      type="button"
                                      class="pwm-helpdesk-link-button"
                                      @click=${() => {
                                          this.passwordMasked = !this.passwordMasked;
                                      }}
                                  >${this.passwordMasked ? 'Show' : 'Hide'}</button>`
                            : nothing}
                    </div>
                </div>
            </div>
            <div class="pwm-helpdesk-dialog-actions">
                <button
                    type="button"
                    class="pwm-helpdesk-action-button"
                    ?disabled=${!this.passwordAcceptable || this.busy}
                    @click=${this.chooseTypedPassword}
                >Change Password</button>
                ${this.uiMode === PASSWORD_UI_MODES.BOTH
                    ? html`<button
                              type="button"
                              class="pwm-helpdesk-action-button"
                              @click=${() => {
                                  this.step = 'autogen';
                                  void this.populateSuggestions();
                              }}
                          >Random Passwords</button>`
                    : nothing}
                <button type="button" class="pwm-helpdesk-action-button" @click=${this.cancel}>Cancel</button>
            </div>
        `;
    }

    private renderStrengthMeter(): TemplateResult | typeof nothing {
        return html`
            <div class="pwm-helpdesk-strength" title="Password strength" aria-hidden="true">
                ${[1, 2, 3, 4, 5].map(
                    (n) => html`<span
                        class="pwm-helpdesk-strength-seg"
                        data-on=${n <= this.strength}
                        data-level=${this.strength}
                    ></span>`,
                )}
            </div>
        `;
    }

    private renderAutogen(): TemplateResult {
        return html`
            <div class="pwm-helpdesk-dialog-title">Random Passwords</div>
            <div class="pwm-helpdesk-dialog-content">
                <p>Select a password from the list below to assign it to this user.</p>
                <div class="pwm-helpdesk-suggestions">
                    ${this.suggestions.map(
                        (suggestion, index) => html`
                            <button
                                type="button"
                                class="pwm-helpdesk-suggestion"
                                ?disabled=${!suggestion || this.busy}
                                @click=${() => this.chooseSuggestion(index)}
                            >${suggestion || '…'}</button>
                        `,
                    )}
                </div>
            </div>
            <div class="pwm-helpdesk-dialog-actions">
                <button
                    type="button"
                    class="pwm-helpdesk-action-button"
                    ?disabled=${this.fetchingRandoms}
                    @click=${() => void this.populateSuggestions()}
                >More</button>
                <button type="button" class="pwm-helpdesk-action-button" @click=${this.cancel}>Cancel</button>
            </div>
        `;
    }

    private renderRandom(): TemplateResult {
        return html`
            <div class="pwm-helpdesk-dialog-title">Change Password</div>
            <div class="pwm-helpdesk-dialog-content">
                <p>
                    A new random password will be set for this user.  The password will not be displayed and must be
                    communicated to the user through your organization's process.  Continue?
                </p>
            </div>
            <div class="pwm-helpdesk-dialog-actions">
                <button
                    type="button"
                    class="pwm-helpdesk-action-button"
                    ?disabled=${this.busy}
                    @click=${this.confirmRandomPassword}
                >Change Password</button>
                <button type="button" class="pwm-helpdesk-action-button" @click=${this.cancel}>Cancel</button>
            </div>
        `;
    }

    private renderSuccess(): TemplateResult {
        return html`
            <div class="pwm-helpdesk-dialog-title">Change Password</div>
            <div class="pwm-helpdesk-dialog-content">
                <p>${this.successMessage}</p>
                ${this.displayNewPassword
                    ? html`
                          <div class="pwm-helpdesk-dialog-field">
                              <label>New Password</label>
                              <div class="pwm-helpdesk-dialog-token-row">
                                  <input
                                      type=${this.passwordMasked ? 'password' : 'text'}
                                      readonly
                                      .value=${this.chosenPassword}
                                  />
                                  ${this.maskPasswords
                                      ? html`<button
                                                type="button"
                                                class="pwm-helpdesk-link-button"
                                                @click=${() => {
                                                    this.passwordMasked = !this.passwordMasked;
                                                }}
                                            >${this.passwordMasked ? 'Show' : 'Hide'}</button>`
                                      : nothing}
                              </div>
                          </div>
                      `
                    : nothing}
                ${this.errorMessage
                    ? html`<p class="pwm-helpdesk-dialog-error">${this.errorMessage}</p>`
                    : nothing}
            </div>
            <div class="pwm-helpdesk-dialog-actions">
                <button type="button" class="pwm-helpdesk-action-button" @click=${() => this.close(true)}>OK</button>
                ${this.clearResponsesSetting === 'ask'
                    ? html`<button
                              type="button"
                              class="pwm-helpdesk-action-button"
                              ?disabled=${this.busy}
                              @click=${() => void this.clearResponses()}
                          >Clear Responses</button>`
                    : nothing}
            </div>
        `;
    }
}
