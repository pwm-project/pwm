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
 * Type declarations for the legacy PWM JavaScript globals exposed by the JSP layer.
 * These are NOT part of this Angular bundle - they are loaded earlier in the page by
 * the existing `pwmHelper.js` / `changepassword.js` scripts that PWM's JSPs already
 * ship.  The migrated Angular component continues to delegate to them so that the
 * server-rendered form contract, dialog look-and-feel, and AJAX endpoints all
 * remain unchanged.
 */

declare global {
    /**
     * Page-wide helper namespace.  Provides localized string lookup and the legacy
     * dialog implementation that other JSPs rely on.  We keep using it during the
     * AngularJS -> Angular migration so the dialog visual style remains identical
     * across pages that have not yet been migrated.
     */
    interface PwmMain {
        showString(key: string): string;
        showDialog(opts: {
            title: string;
            text?: string;
            dialogClass?: string;
            showOk?: boolean;
            showClose?: boolean;
            loadFunction?: () => void;
        }): void;
        closeWaitDialog(id: string): void;
    }

    /**
     * Change-password-page-specific helpers.  Used to fetch random passwords from the
     * server and to populate the new-password / confirm-password fields when the user
     * picks a suggestion.
     */
    interface PwmChangePw {
        beginFetchRandoms(opts: Record<string, unknown>): void;
        copyToPasswordFields(password: string): void;
    }

    interface Window {
        PWM_MAIN: PwmMain;
        PWM_CHANGEPW: PwmChangePw;
    }
}

export {};
