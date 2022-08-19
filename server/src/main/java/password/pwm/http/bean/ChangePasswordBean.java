/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.ldap.PasswordChangeProgressChecker;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author Jason D. Rivard
 */
@Data
@EqualsAndHashCode( callSuper = false )
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

    @Override
    public BeanType getBeanType( )
    {
        return BeanType.AUTHENTICATED;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.unmodifiableSet( EnumSet.of( SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE, SessionBeanMode.CRYPTREQUEST ) );
    }
}

