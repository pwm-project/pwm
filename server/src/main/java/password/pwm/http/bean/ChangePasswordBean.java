/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http.bean;

import com.google.gson.annotations.SerializedName;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.ldap.PasswordChangeProgressChecker;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author Jason D. Rivard
 */
public class ChangePasswordBean extends PwmSessionBean
{

    @SerializedName( "ap" )
    private boolean agreementPassed;

    @SerializedName( "cpr" )
    private boolean currentPasswordRequired;

    @SerializedName( "cpp" )
    private boolean currentPasswordPassed;

    @SerializedName( "fp" )
    private boolean formPassed;

    @SerializedName( "acp" )
    private boolean allChecksPassed;

    @SerializedName( "n" )
    private boolean nextAllowedTimePassed;

    @SerializedName( "wp" )
    private boolean warnPassed;

    @SerializedName( "pt" )
    private PasswordChangeProgressChecker.ProgressTracker changeProgressTracker;

    @SerializedName( "mc" )
    private Instant changePasswordMaxCompletion;

    public boolean isAgreementPassed( )
    {
        return agreementPassed;
    }

    public void setAgreementPassed( final boolean agreementPassed )
    {
        this.agreementPassed = agreementPassed;
    }

    public boolean isCurrentPasswordRequired( )
    {
        return currentPasswordRequired;
    }

    public void setCurrentPasswordRequired( final boolean currentPasswordRequired )
    {
        this.currentPasswordRequired = currentPasswordRequired;
    }

    public boolean isCurrentPasswordPassed( )
    {
        return currentPasswordPassed;
    }

    public void setCurrentPasswordPassed( final boolean currentPasswordPassed )
    {
        this.currentPasswordPassed = currentPasswordPassed;
    }

    public boolean isFormPassed( )
    {
        return formPassed;
    }

    public void setFormPassed( final boolean formPassed )
    {
        this.formPassed = formPassed;
    }

    public boolean isAllChecksPassed( )
    {
        return allChecksPassed;
    }

    public void setAllChecksPassed( final boolean allChecksPassed )
    {
        this.allChecksPassed = allChecksPassed;
    }

    public PasswordChangeProgressChecker.ProgressTracker getChangeProgressTracker( )
    {
        return changeProgressTracker;
    }

    public void setChangeProgressTracker( final PasswordChangeProgressChecker.ProgressTracker changeProgressTracker )
    {
        this.changeProgressTracker = changeProgressTracker;
    }

    public Instant getChangePasswordMaxCompletion( )
    {
        return changePasswordMaxCompletion;
    }

    public void setChangePasswordMaxCompletion( final Instant changePasswordMaxCompletion )
    {
        this.changePasswordMaxCompletion = changePasswordMaxCompletion;
    }

    public boolean isNextAllowedTimePassed( )
    {
        return nextAllowedTimePassed;
    }

    public void setNextAllowedTimePassed( final boolean nextAllowedTimePassed )
    {
        this.nextAllowedTimePassed = nextAllowedTimePassed;
    }

    public boolean isWarnPassed( )
    {
        return warnPassed;
    }

    public void setWarnPassed( final boolean warnPassed )
    {
        this.warnPassed = warnPassed;
    }

    @Override
    public Type getType( )
    {
        return Type.AUTHENTICATED;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.unmodifiableSet( EnumSet.of( SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE, SessionBeanMode.CRYPTREQUEST ) );
    }
}

