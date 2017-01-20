/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.http.bean;

import password.pwm.config.option.SessionBeanMode;
import password.pwm.ldap.PasswordChangeProgressChecker;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Jason D. Rivard
 */
public class ChangePasswordBean extends PwmSessionBean {
// ------------------------------ FIELDS ------------------------------

    // ------------------------- PUBLIC CONSTANTS -------------------------
    private boolean agreementPassed;
    private boolean currentPasswordRequired;
    private boolean currentPasswordPassed;
    private boolean formPassed;
    private boolean allChecksPassed;
    private boolean nextAllowedTimePassed;
    private boolean warnPassed;

    private PasswordChangeProgressChecker.ProgressTracker changeProgressTracker;
    private Instant changePasswordMaxCompletion;


// --------------------- GETTER / SETTER METHODS ---------------------

    public boolean isAgreementPassed() {
        return agreementPassed;
    }

    public void setAgreementPassed(final boolean agreementPassed) {
        this.agreementPassed = agreementPassed;
    }

    public boolean isCurrentPasswordRequired() {
        return currentPasswordRequired;
    }

    public void setCurrentPasswordRequired(final boolean currentPasswordRequired) {
        this.currentPasswordRequired = currentPasswordRequired;
    }

    public boolean isCurrentPasswordPassed() {
        return currentPasswordPassed;
    }

    public void setCurrentPasswordPassed(final boolean currentPasswordPassed) {
        this.currentPasswordPassed = currentPasswordPassed;
    }

    public boolean isFormPassed() {
        return formPassed;
    }

    public void setFormPassed(final boolean formPassed) {
        this.formPassed = formPassed;
    }

    public boolean isAllChecksPassed() {
        return allChecksPassed;
    }

    public void setAllChecksPassed(final boolean allChecksPassed) {
        this.allChecksPassed = allChecksPassed;
    }

    public PasswordChangeProgressChecker.ProgressTracker getChangeProgressTracker()
    {
        return changeProgressTracker;
    }

    public void setChangeProgressTracker(final PasswordChangeProgressChecker.ProgressTracker changeProgressTracker)
    {
        this.changeProgressTracker = changeProgressTracker;
    }

    public Instant getChangePasswordMaxCompletion()
    {
        return changePasswordMaxCompletion;
    }

    public void setChangePasswordMaxCompletion(final Instant changePasswordMaxCompletion)
    {
        this.changePasswordMaxCompletion = changePasswordMaxCompletion;
    }

    public boolean isNextAllowedTimePassed()
    {
        return nextAllowedTimePassed;
    }

    public void setNextAllowedTimePassed(final boolean nextAllowedTimePassed)
    {
        this.nextAllowedTimePassed = nextAllowedTimePassed;
    }

    public boolean isWarnPassed()
    {
        return warnPassed;
    }

    public void setWarnPassed(final boolean warnPassed)
    {
        this.warnPassed = warnPassed;
    }

    public Type getType() {
        return Type.AUTHENTICATED;
    }

    @Override
    public Set<SessionBeanMode> supportedModes() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE, SessionBeanMode.CRYPTREQUEST)));
    }
}

