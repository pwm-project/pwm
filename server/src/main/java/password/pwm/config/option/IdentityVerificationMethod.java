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

package password.pwm.config.option;

import password.pwm.config.Configuration;
import password.pwm.i18n.Display;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum IdentityVerificationMethod implements Serializable, ConfigurationOption
{
    PREVIOUS_AUTH( false, Display.Field_VerificationMethodPreviousAuth, Display.Description_VerificationMethodPreviousAuth ),
    ATTRIBUTES( true, Display.Field_VerificationMethodAttributes, Display.Description_VerificationMethodAttributes ),
    CHALLENGE_RESPONSES( true, Display.Field_VerificationMethodChallengeResponses, Display.Description_VerificationMethodChallengeResponses ),
    TOKEN( true, Display.Field_VerificationMethodToken, Display.Description_VerificationMethodToken ),
    OTP( true, Display.Field_VerificationMethodOTP, Display.Description_VerificationMethodOTP ),
    REMOTE_RESPONSES( false, Display.Field_VerificationMethodRemoteResponses, Display.Description_VerificationMethodRemoteResponses ),
    OAUTH( true, Display.Field_VerificationMethodOAuth, Display.Description_VerificationMethodOAuth ),;

    private final transient boolean userSelectable;
    private final transient Display labelKey;
    private final transient Display descriptionKey;

    IdentityVerificationMethod( final boolean userSelectable, final Display labelKey, final Display descriptionKey )
    {
        this.userSelectable = userSelectable;
        this.labelKey = labelKey;
        this.descriptionKey = descriptionKey;
    }

    public boolean isUserSelectable( )
    {
        return userSelectable;
    }

    public String getLabel( final Configuration configuration, final Locale locale )
    {
        return Display.getLocalizedMessage( locale, this.labelKey, configuration );
    }

    public String getDescription( final Configuration configuration, final Locale locale )
    {
        return Display.getLocalizedMessage( locale, this.descriptionKey, configuration );
    }

    public static IdentityVerificationMethod[] availableValues( )
    {
        final List<IdentityVerificationMethod> values = new ArrayList<>( Arrays.asList( IdentityVerificationMethod.values() ) );
        return values.toArray( new IdentityVerificationMethod[0] );
    }
}
