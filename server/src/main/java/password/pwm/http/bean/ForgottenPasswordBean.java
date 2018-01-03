/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import com.google.gson.annotations.SerializedName;
import com.novell.ldapchai.cr.ChallengeSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import password.pwm.VerificationMethodSystem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.config.value.data.FormConfiguration;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author Jason D. Rivard
 */
@Data
public class ForgottenPasswordBean extends PwmSessionBean
{

    @SerializedName( "u" )
    private UserIdentity userIdentity;

    @SerializedName( "pc" )
    private ChallengeSet presentableChallengeSet;

    @SerializedName( "l" )
    private Locale userLocale;

    @SerializedName( "a" )
    private List<FormConfiguration> attributeForm;

    @SerializedName( "p" )
    private Progress progress = new Progress();

    @SerializedName( "f" )
    private RecoveryFlags recoveryFlags = new RecoveryFlags();

    @SerializedName( "fp" )
    private String forgottenPasswordProfileID;

    @Data
    public static class Progress implements Serializable
    {
        @SerializedName( "s" )
        private boolean tokenSent;

        @SerializedName( "p" )
        private boolean allPassed;

        @SerializedName( "m" )
        private final Set<IdentityVerificationMethod> satisfiedMethods = new LinkedHashSet<>();

        @SerializedName( "c" )
        private MessageSendMethod tokenSendChoice;

        @SerializedName( "a" )
        private String tokenSentAddress;

        @SerializedName( "i" )
        private IdentityVerificationMethod inProgressVerificationMethod;

        private transient VerificationMethodSystem remoteRecoveryMethod;

        public void clearTokenSentStatus( )
        {
            this.setTokenSent( false );
            this.setTokenSentAddress( null );
            this.setTokenSendChoice( null );
        }
    }

    @Value
    @AllArgsConstructor
    public static class RecoveryFlags implements Serializable
    {
        @SerializedName( "a" )
        private final boolean allowWhenLdapIntruderLocked;

        @SerializedName( "r" )
        private final Set<IdentityVerificationMethod> requiredAuthMethods;

        @SerializedName( "o" )
        private final Set<IdentityVerificationMethod> optionalAuthMethods;

        @SerializedName( "m" )
        private final int minimumOptionalAuthMethods;

        @SerializedName( "t" )
        private final MessageSendMethod tokenSendMethod;

        public RecoveryFlags( )
        {
            this.requiredAuthMethods = Collections.emptySet();
            this.optionalAuthMethods = Collections.emptySet();
            this.allowWhenLdapIntruderLocked = false;
            this.minimumOptionalAuthMethods = 0;
            this.tokenSendMethod = MessageSendMethod.NONE;
        }
    }

    public Type getType( )
    {
        return Type.PUBLIC;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        //return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE, SessionBeanMode.CRYPTREQUEST)));
        return Collections.singleton( SessionBeanMode.LOCAL );
    }
}

