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
import { repeat } from 'lit/directives/repeat.js';
import {
    checkVerification,
    sendVerificationToken,
    validateVerificationData,
    type TokenDestination,
    type VerificationMethod,
    type VerificationOptions,
} from './services/helpdesk-service';
import {
    getVerificationAttributes,
    VERIFICATION_METHOD_LABELS,
    VERIFICATION_METHOD_NAMES,
} from './services/config-service';

/** Outer flow step. */
type DialogStatus = 'wait' | 'select' | 'verify' | 'error';
/** Inner per-attempt status of the active method. */
type VerifyStatus = 'none' | 'wait' | 'passed' | 'failed';

/**
 * Detail of the {@code dialog-result} event the host listens for.  {@code passed}
 * tells the host whether verification succeeded (so a detail view can refresh
 * its gated buttons); {@code proceedToDetails} is set only in the search-page
 * gate flow when the operator chose to continue to the user's detail page.
 */
export interface VerificationResult {
    passed: boolean;
    proceedToDetails: boolean;
}

/**
 * Native port of the legacy {@code VerificationsDialogController} +
 * {@code verifications-dialog.template.html} (issue #729, session 4).  Replaces
 * the {@code IasDialogService} ng-ias modal with a self-contained Lit overlay.
 *
 * <p>Two entry modes, matching the legacy {@code showRequiredOnly} /
 * {@code isDetailsView} locals:</p>
 * <ul>
 *   <li><b>Gate</b> ({@code requiredOnly=true, isDetailsView=false}) - opened
 *       when an operator clicks a search result on a profile that requires
 *       verification.  If verification was already satisfied this session the
 *       dialog resolves immediately with {@code proceedToDetails}.</li>
 *   <li><b>Detail</b> ({@code requiredOnly=false, isDetailsView=true}) - opened
 *       by the detail-page "Verify" button to satisfy optional methods; resolves
 *       with {@code passed} so the host can refresh.</li>
 * </ul>
 *
 * <p>Light-DOM render root so PWM's theme stylesheets cascade in, consistent
 * with {@code <pwm-helpdesk>}.</p>
 */
@customElement('pwm-helpdesk-verification-dialog')
export class VerificationDialogElement extends LitElement {
    protected override createRenderRoot(): this {
        return this;
    }

    @property({ type: String }) userKey = '';
    @property({ type: Boolean }) requiredOnly = false;
    @property({ type: Boolean }) isDetailsView = false;

    @state() private status: DialogStatus = 'wait';
    @state() private verifyStatus: VerifyStatus = 'none';
    @state() private options: VerificationOptions | null = null;
    @state() private availableMethods: Array<{ name: string; label: string }> = [];
    @state() private method: VerificationMethod | '' = '';
    @state() private inputs: Array<{ name: string; label: string }> = [];
    @state() private formData: Record<string, string> = {};
    @state() private tokenDestinationId = '';
    @state() private sendingToken = false;
    @state() private tokenSent = false;
    @state() private errorMessage = '';

    private tokenData = '';
    private abortController: AbortController | null = null;
    private keydownListener: ((e: KeyboardEvent) => void) | null = null;

    override connectedCallback(): void {
        super.connectedCallback();
        this.keydownListener = (e: KeyboardEvent): void => {
            if (e.key === 'Escape') {
                this.cancel();
            }
        };
        window.addEventListener('keydown', this.keydownListener);
        void this.runCheck();
    }

    override disconnectedCallback(): void {
        super.disconnectedCallback();
        if (this.abortController) {
            this.abortController.abort();
            this.abortController = null;
        }
        if (this.keydownListener) {
            window.removeEventListener('keydown', this.keydownListener);
            this.keydownListener = null;
        }
    }

    // ---- flow ----

    private async runCheck(): Promise<void> {
        this.abortController = new AbortController();
        try {
            const response = await checkVerification(this.userKey, this.abortController.signal);
            this.options = response.verificationOptions;
            if (!this.isDetailsView && response.passed) {
                // Already verified this session - skip straight to details.
                this.resolve({ passed: true, proceedToDetails: true });
                return;
            }
            this.status = 'select';
            this.computeAvailableMethods();
        } catch (err) {
            if ((err as { name?: string }).name === 'AbortError') {
                return;
            }
            this.errorMessage = (err as Error).message;
            this.status = 'error';
        }
    }

    private computeAvailableMethods(): void {
        const methods = this.requiredOnly
            ? this.options?.verificationMethods.required
            : this.options?.verificationMethods.optional;
        this.availableMethods = (methods ?? []).map((name) => ({
            name,
            label: VERIFICATION_METHOD_LABELS[name] ?? name,
        }));
    }

    private async selectMethod(method: string): Promise<void> {
        this.method = method as VerificationMethod;
        this.verifyStatus = 'none';
        this.formData = {};
        this.tokenData = '';
        this.tokenSent = false;

        if (method === VERIFICATION_METHOD_NAMES.ATTRIBUTES) {
            const inputs = await getVerificationAttributes();
            this.inputs = inputs;
            // Initialise every field so the backend receives '' rather than null.
            const seeded: Record<string, string> = {};
            for (const input of inputs) {
                seeded[input.name] = '';
            }
            this.formData = seeded;
            this.status = 'verify';
        } else if (method === VERIFICATION_METHOD_NAMES.TOKEN) {
            const first = this.options?.tokenDestinations?.[0];
            this.tokenDestinationId = first ? first.id : '';
            this.status = 'verify';
        } else if (method === VERIFICATION_METHOD_NAMES.OTP) {
            this.status = 'verify';
        }
    }

    private onFieldInput(name: string, value: string): void {
        this.formData = { ...this.formData, [name]: value };
    }

    private onTokenDestinationChanged(id: string): void {
        this.tokenDestinationId = id;
        // Selecting a different destination invalidates any already-sent token.
        this.tokenSent = false;
        this.tokenData = '';
    }

    private async sendToken(): Promise<void> {
        if (!this.tokenDestinationId) {
            return;
        }
        this.sendingToken = true;
        this.tokenSent = false;
        try {
            const response = await sendVerificationToken(this.userKey, this.tokenDestinationId);
            this.tokenData = response.tokenData ?? '';
            this.tokenSent = true;
        } catch (err) {
            this.tokenSent = false;
            this.errorMessage = (err as Error).message;
        } finally {
            this.sendingToken = false;
        }
    }

    private async submitVerification(): Promise<void> {
        if (!this.method) {
            return;
        }
        this.verifyStatus = 'wait';
        const data: Record<string, string> = { ...this.formData };
        if (this.tokenData) {
            data['tokenData'] = this.tokenData;
        }
        try {
            const response = await validateVerificationData(this.userKey, data, this.method);
            this.verifyStatus = response.passed ? 'passed' : 'failed';
        } catch {
            this.verifyStatus = 'failed';
        }
    }

    // ---- resolution ----

    private resolve(result: VerificationResult): void {
        this.dispatchEvent(
            new CustomEvent<VerificationResult>('dialog-result', {
                detail: result,
                bubbles: true,
                composed: true,
            }),
        );
    }

    private cancel = (): void => {
        this.resolve({ passed: this.verifyStatus === 'passed', proceedToDetails: false });
    };

    /** Gate-mode "View Details" - continue to the detail page after passing. */
    private viewDetails = (): void => {
        if (this.verifyStatus === 'passed') {
            this.resolve({ passed: true, proceedToDetails: true });
        }
    };

    /** Detail-mode "OK" - close and let the host refresh gated state. */
    private clickOk = (): void => {
        if (this.verifyStatus === 'passed') {
            this.resolve({ passed: true, proceedToDetails: false });
        }
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
                <div
                    class="pwm-helpdesk-dialog"
                    role="dialog"
                    aria-modal="true"
                    aria-label="Verify identity"
                >
                    ${this.renderBody()}
                    <button
                        type="button"
                        class="pwm-helpdesk-dialog-close"
                        title="Close"
                        aria-label="Close"
                        @click=${this.cancel}
                    >
                        &times;
                    </button>
                </div>
            </div>
        `;
    }

    private renderBody(): TemplateResult {
        switch (this.status) {
            case 'wait':
                return html`<div class="pwm-helpdesk-dialog-content">Loading…</div>`;
            case 'error':
                return html`
                    <div class="pwm-helpdesk-dialog-title">Verification unavailable</div>
                    <div class="pwm-helpdesk-dialog-content pwm-helpdesk-dialog-error">
                        ${this.errorMessage}
                    </div>
                    <div class="pwm-helpdesk-dialog-actions">
                        <button type="button" class="pwm-helpdesk-action-button" @click=${this.cancel}>Close</button>
                    </div>
                `;
            case 'select':
                return this.renderSelect();
            case 'verify':
                return this.renderVerify();
            default:
                return html``;
        }
    }

    private renderSelect(): TemplateResult {
        return html`
            <div class="pwm-helpdesk-dialog-title">Verify Identity</div>
            <div class="pwm-helpdesk-dialog-content">
                ${this.availableMethods.length === 0
                    ? html`<p>No verification methods are available.</p>`
                    : html`
                          <p>Select a method to verify this user's identity.</p>
                          <div class="pwm-helpdesk-dialog-methods">
                              ${this.availableMethods.map(
                                  (m) => html`
                                      <button
                                          type="button"
                                          class="pwm-helpdesk-action-button"
                                          @click=${() => this.selectMethod(m.name)}
                                      >${m.label}</button>
                                  `,
                              )}
                          </div>
                      `}
            </div>
            <div class="pwm-helpdesk-dialog-actions">
                <button type="button" class="pwm-helpdesk-action-button" @click=${this.cancel}>Cancel</button>
            </div>
        `;
    }

    private renderVerify(): TemplateResult {
        return html`
            <div class="pwm-helpdesk-dialog-title">Validate Code</div>
            <div class="pwm-helpdesk-dialog-content">
                <form @submit=${(e: Event) => e.preventDefault()}>
                    ${this.method === VERIFICATION_METHOD_NAMES.ATTRIBUTES ? this.renderAttributesForm() : nothing}
                    ${this.method === VERIFICATION_METHOD_NAMES.TOKEN ? this.renderTokenForm() : nothing}
                    ${this.method === VERIFICATION_METHOD_NAMES.OTP ? this.renderOtpForm() : nothing}

                    <div class="pwm-helpdesk-dialog-verify-row">
                        <button
                            type="button"
                            class="pwm-helpdesk-action-button"
                            ?disabled=${this.verifyStatus === 'passed' || this.verifyStatus === 'wait'}
                            @click=${this.submitVerification}
                        >Verify</button>
                        ${this.verifyStatus === 'wait' ? html`<span class="pwm-helpdesk-dialog-hint">Verifying…</span>` : nothing}
                        ${this.verifyStatus === 'passed'
                            ? html`<span class="pwm-helpdesk-dialog-ok" aria-label="Verified">✓</span>`
                            : nothing}
                        ${this.verifyStatus === 'failed'
                            ? html`<span class="pwm-helpdesk-dialog-fail" aria-label="Failed">✕</span>`
                            : nothing}
                    </div>
                    ${this.verifyStatus === 'failed'
                        ? html`<p class="pwm-helpdesk-dialog-error">Verification was not successful.</p>`
                        : nothing}
                </form>
            </div>
            <div class="pwm-helpdesk-dialog-actions">
                ${this.isDetailsView
                    ? html`<button
                              type="button"
                              class="pwm-helpdesk-action-button"
                              ?disabled=${this.verifyStatus !== 'passed'}
                              @click=${this.clickOk}
                          >OK</button>`
                    : html`<button
                              type="button"
                              class="pwm-helpdesk-action-button"
                              ?disabled=${this.verifyStatus !== 'passed'}
                              @click=${this.viewDetails}
                          >View Details</button>`}
                <button type="button" class="pwm-helpdesk-action-button" @click=${this.cancel}>Cancel</button>
            </div>
        `;
    }

    private renderAttributesForm(): TemplateResult {
        return html`
            ${repeat(
                this.inputs,
                (input) => input.name,
                (input, index) => html`
                    <div class="pwm-helpdesk-dialog-field">
                        <label for=${`pwm-hd-verify-${input.name}`}>${input.label}</label>
                        <input
                            id=${`pwm-hd-verify-${input.name}`}
                            type="text"
                            autocomplete="off"
                            ?autofocus=${index === 0}
                            .value=${this.formData[input.name] ?? ''}
                            @input=${(e: Event) => this.onFieldInput(input.name, (e.target as HTMLInputElement).value)}
                        />
                    </div>
                `,
            )}
        `;
    }

    private renderTokenForm(): TemplateResult {
        const destinations: TokenDestination[] = this.options?.tokenDestinations ?? [];
        return html`
            <div class="pwm-helpdesk-dialog-field">
                <label for="pwm-hd-verify-dest">Send token to</label>
                <div class="pwm-helpdesk-dialog-token-row">
                    <select
                        id="pwm-hd-verify-dest"
                        .value=${this.tokenDestinationId}
                        @change=${(e: Event) => this.onTokenDestinationChanged((e.target as HTMLSelectElement).value)}
                    >
                        ${destinations.map(
                            (dest) => html`<option value=${dest.id}>
                                ${dest.type === 'sms' ? 'SMS:' : 'Email:'} ${dest.display}
                            </option>`,
                        )}
                    </select>
                    <button
                        type="button"
                        class="pwm-helpdesk-action-button"
                        ?disabled=${this.sendingToken || !this.tokenDestinationId}
                        @click=${this.sendToken}
                    >Send Token</button>
                    ${this.sendingToken ? html`<span class="pwm-helpdesk-dialog-hint">Sending…</span>` : nothing}
                    ${this.tokenSent ? html`<span class="pwm-helpdesk-dialog-ok" aria-label="Token sent">✓</span>` : nothing}
                </div>
            </div>
            <div class="pwm-helpdesk-dialog-field">
                <label for="pwm-hd-verify-token">Token</label>
                <input
                    id="pwm-hd-verify-token"
                    type="text"
                    autocomplete="off"
                    .value=${this.formData['code'] ?? ''}
                    @input=${(e: Event) => this.onFieldInput('code', (e.target as HTMLInputElement).value)}
                />
            </div>
        `;
    }

    private renderOtpForm(): TemplateResult {
        return html`
            <p>Enter the one-time-password code from the user's authenticator.</p>
            <div class="pwm-helpdesk-dialog-field">
                <label for="pwm-hd-verify-otp">Code</label>
                <input
                    id="pwm-hd-verify-otp"
                    type="text"
                    autocomplete="off"
                    autofocus
                    .value=${this.formData['code'] ?? ''}
                    @input=${(e: Event) => this.onFieldInput('code', (e.target as HTMLInputElement).value)}
                />
            </div>
        `;
    }
}
