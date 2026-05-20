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

import { ChangeDetectionStrategy, Component, ViewEncapsulation } from '@angular/core';

/**
 * Modern (Angular 19) replacement for the AngularJS 1.x
 * {@code password-suggestions.html} partial that lived in
 * {@code client/angular/src/modules/changepassword/}.
 *
 * <p>This component renders the 10 x 2 grid of random-password suggestions plus
 * the "More" and "Cancel" action buttons.  The grid cells stay empty until the
 * legacy {@code PWM_CHANGEPW.beginFetchRandoms({})} call - kicked off by the
 * parent - asynchronously populates them via direct DOM writes to
 * {@code #randomGen0}..{@code #randomGen19}.  This preserves the original
 * server-rendered fetch contract so the backend rest endpoint is unchanged.</p>
 *
 * <p>The cells are wired with a single delegated click listener; when the user
 * picks a suggestion the {@code textContent} of the clicked cell is forwarded
 * to {@code PWM_CHANGEPW.copyToPasswordFields()} which is the same path the
 * AngularJS controller used.</p>
 */
@Component({
    selector: 'pwm-password-suggestions',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    templateUrl: './password-suggestions.component.html',
    styleUrl: './password-suggestions.component.scss',
})
export class PasswordSuggestionsComponent {
    /** 0..19 - the two-column layout iterates this list 2 cells per row. */
    protected readonly cellIndexes: readonly number[] = Array.from({ length: 20 }, (_, i) => i);

    protected text(key: string): string {
        return window.PWM_MAIN.showString(key);
    }

    protected onChoosePassword(event: MouseEvent): void {
        const target = event.target as HTMLElement | null;
        if (target?.textContent) {
            window.PWM_CHANGEPW.copyToPasswordFields(target.textContent);
        }
    }

    protected onMoreRandomsButtonClick(): void {
        window.PWM_CHANGEPW.beginFetchRandoms({});
    }

    protected onCancelRandomsButtonClick(): void {
        window.PWM_MAIN.closeWaitDialog('dialogPopup');
    }
}
