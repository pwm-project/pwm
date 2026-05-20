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

// pwm-globals.d.ts is picked up automatically via tsconfig.app.json's include glob;
// no runtime import is needed because it is a declaration-only file.
import { createApplication } from '@angular/platform-browser';
import { createCustomElement } from '@angular/elements';
import { ChangePasswordComponent } from './change-password.component';

/**
 * Entry point for the change-password modern client bundle.
 *
 * <p>Registers {@code <pwm-changepassword>} as a native custom element so the
 * JSP can drop it into the page exactly where the AngularJS
 * {@code ng-controller="ChangePasswordController as $ctrl"} attribute used to
 * live.  No JSP {@code ng-app}/{@code ng-controller} attributes are required;
 * the custom element bootstraps itself the moment the browser sees the tag.</p>
 */
createApplication({ providers: [] })
    .then((appRef) => {
        const element = createCustomElement(ChangePasswordComponent, {
            injector: appRef.injector,
        });
        // Custom Elements names must contain a hyphen.  Match the selector used
        // in the component decorator so JSP authors only need to remember one tag.
        if (!customElements.get('pwm-changepassword')) {
            customElements.define('pwm-changepassword', element);
        }
    })
    .catch((err: unknown) => {
        // eslint-disable-next-line no-console
        console.error('Failed to bootstrap <pwm-changepassword> custom element', err);
    });
