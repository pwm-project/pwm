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
import com.novell.ldapchai.cr.bean.ChallengeSetBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import password.pwm.VerificationMethodSystem;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.RecoveryAction;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.config.value.data.FormConfiguration;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author Jason D. Rivard
 */
@Data
@EqualsAndHashCode( callSuper = false )
public class ForgottenPasswordBean extends PwmSessionBean
{
    private static final long serialVersionUID = 1L;

    @SerializedName( "pr" )
    private String profile;

    @SerializedName( "u" )
    private UserIdentity userIdentity;

    @SerializedName( "pc" )
    private ChallengeSetBean presentableChallengeSet;

    @SerializedName( "l" )
    private Locale userLocale;

    @SerializedName( "a" )
    private List<FormConfiguration> attributeForm;

    @SerializedName( "p" )
    private Progress progress = new Progress();

    @SerializedName( "f" )
    private RecoveryFlags recoveryFlags = new RecoveryFlags();

    @SerializedName( "b" )
    private boolean bogusUser;

    @SerializedName( "g" )
    private boolean agreementPassed;

    @SerializedName( "fp" )
    private String forgottenPasswordProfileID;

    @SerializedName( "lf" )
    private Map<String, String> userSearchValues;

    @Data
    public static class Progress implements Serializable
    {
        private static final long serialVersionUID = 1L;

        @SerializedName( "s" )
        private boolean tokenSent;

        @SerializedName( "p" )
        private boolean allPassed;

        @SerializedName( "m" )
        private final Set<IdentityVerificationMethod> satisfiedMethods = new LinkedHashSet<>();

        @SerializedName( "d" )
        private TokenDestinationItem tokenDestination;

        @SerializedName( "i" )
        private IdentityVerificationMethod inProgressVerificationMethod;

        private transient VerificationMethodSystem remoteRecoveryMethod;

        @SerializedName( "ra" )
        private RecoveryAction executedRecoveryAction;

        public void clearTokenSentStatus( )
        {
            this.setTokenSent( false );
            this.setTokenDestination( null );
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

        public RecoveryFlags( )
        {
            this.requiredAuthMethods = Collections.emptySet();
            this.optionalAuthMethods = Collections.emptySet();
            this.allowWhenLdapIntruderLocked = false;
            this.minimumOptionalAuthMethods = 0;
        }
    }

    @Override
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

