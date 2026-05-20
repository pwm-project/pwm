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

import {
    ApplicationRef,
    ChangeDetectionStrategy,
    Component,
    Injector,
    ViewEncapsulation,
    createComponent,
    inject,
} from '@angular/core';
import { PasswordSuggestionsComponent } from './password-suggestions.component';

/**
 * Modern (Angular 19) replacement for {@code ChangePasswordController} from
 * {@code client/angular/src/modules/changepassword/changepassword.controller.ts}.
 *
 * <p>This component is exported as the {@code <pwm-changepassword>} custom element.
 * It contributes a single click target (the "retweet" icon already rendered by the
 * JSP via {@code <pwm-changepassword-trigger>} below) that opens the existing PWM
 * dialog and populates it with the random-password suggestions grid.</p>
 *
 * <p>The component intentionally does <em>not</em> own the dialog chrome itself -
 * that lives in the legacy {@code PWM_MAIN.showDialog} implementation and is
 * shared with other JSP pages that have not yet been migrated.  When the dialog
 * is opened, we replace its body with a freshly-bootstrapped
 * {@link PasswordSuggestionsComponent} instance using
 * {@link createComponent} (the Angular 14+ replacement for the
 * AngularJS {@code $compile} / {@code $templateCache} dance).</p>
 *
 * <p>Once every page that uses {@code PWM_MAIN.showDialog} has been migrated
 * (helpdesk, peoplesearch, configeditor) the dialog chrome itself can be
 * replaced with a native {@code <dialog>} element or Angular CDK overlay and
 * this bridge will collapse to a single component.</p>
 */
@Component({
    selector: 'pwm-changepassword',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    template: `
        <button type="button"
                class="pwm-icon pwm-icon-retweet icon_button"
                id="autogenerate-icon"
                style="cursor: pointer;"
                [attr.aria-label]="triggerLabel()"
                (click)="doRandomGeneration()">
        </button>
    `,
})
export class ChangePasswordComponent {
    private readonly appRef = inject(ApplicationRef);
    private readonly injector = inject(Injector);

    protected triggerLabel(): string {
        // Matches the visible-tooltip key used by the legacy JSP's title attribute.
        return window.PWM_MAIN.showString('Title_RandomPasswords');
    }

    /**
     * Opens the legacy dialog, then mounts {@link PasswordSuggestionsComponent}
     * into its body once the dialog DOM is in the document.  Mirrors the
     * {@code populateDialog()} behavior from the AngularJS controller.
     */
    protected doRandomGeneration(): void {
        window.PWM_MAIN.showDialog({
            title: window.PWM_MAIN.showString('Title_RandomPasswords'),
            dialogClass: 'narrow',
            text: '',
            showOk: false,
            showClose: true,
            loadFunction: () => this.mountSuggestionsIntoDialog(),
        });
    }

    private mountSuggestionsIntoDialog(): void {
        const host = document.querySelector<HTMLElement>(
            '#dialogPopup .dialogBody, #html5Dialog .dialogBody',
        );
        if (!host) {
            return;
        }

        // Dynamically create and attach an Angular component into a DOM node owned
        // by the legacy dialog.  This is the Angular 14+ API; it replaces the
        // ComponentFactoryResolver / ViewContainerRef pattern previous versions used.
        const componentRef = createComponent(PasswordSuggestionsComponent, {
            environmentInjector: this.appRef.injector,
            elementInjector: this.injector,
            hostElement: host,
        });
        this.appRef.attachView(componentRef.hostView);

        // beginFetchRandoms() fires an AJAX call that asynchronously writes the
        // generated passwords into #randomGen0..#randomGen19 by DOM id - those
        // ids are rendered by PasswordSuggestionsComponent so the contract holds.
        window.PWM_CHANGEPW.beginFetchRandoms({});
    }
}
