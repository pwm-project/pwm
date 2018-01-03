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

package password.pwm.config;

import password.pwm.config.value.ActionValue;
import password.pwm.config.value.BooleanValue;
import password.pwm.config.value.ChallengeValue;
import password.pwm.config.value.CustomLinkValue;
import password.pwm.config.value.EmailValue;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.FormValue;
import password.pwm.config.value.LocalizedStringArrayValue;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.NamedSecretValue;
import password.pwm.config.value.NumericValue;
import password.pwm.config.value.OptionListValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.PrivateKeyValue;
import password.pwm.config.value.RemoteWebServiceValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.UserPermissionValue;
import password.pwm.config.value.VerificationMethodValue;
import password.pwm.config.value.X509CertificateValue;

/**
 * Setting syntax definitions.  Each syntax listed here corresponds to some type of native Java object.  The factory specified includes
 * methods for marshaling to and from XML and JSON formats.  For user-facing syntactical differences in format, see the
 * {@link PwmSetting#getRegExPattern()} or use a {@link PwmSettingFlag} type.
 */
public enum PwmSettingSyntax
{
    STRING( StringValue.factory() ),
    USER_PERMISSION( UserPermissionValue.factory() ),
    STRING_ARRAY( StringArrayValue.factory() ),
    TEXT_AREA( StringValue.factory() ),
    LOCALIZED_STRING( LocalizedStringValue.factory() ),
    LOCALIZED_TEXT_AREA( LocalizedStringValue.factory() ),
    LOCALIZED_STRING_ARRAY( LocalizedStringArrayValue.factory() ),
    PASSWORD( PasswordValue.factory() ),
    NUMERIC( NumericValue.factory() ),
    DURATION( NumericValue.factory() ),
    BOOLEAN( BooleanValue.factory() ),
    SELECT( StringValue.factory() ),
    FORM( FormValue.factory() ),
    ACTION( ActionValue.factory() ),
    EMAIL( EmailValue.factory() ),
    X509CERT( X509CertificateValue.factory() ),
    CHALLENGE( ChallengeValue.factory() ),
    OPTIONLIST( OptionListValue.factory() ),
    FILE( FileValue.factory() ),
    PROFILE( StringArrayValue.factory() ),
    VERIFICATION_METHOD( VerificationMethodValue.factory() ),
    PRIVATE_KEY( PrivateKeyValue.factory() ),
    NAMED_SECRET( NamedSecretValue.factory() ),
    CUSTOMLINKS( CustomLinkValue.factory() ),
    REMOTE_WEB_SERVICE( RemoteWebServiceValue.factory() ),;

    private StoredValue.StoredValueFactory storedValueImpl;

    PwmSettingSyntax( final StoredValue.StoredValueFactory storedValueImpl )
    {
        this.storedValueImpl = storedValueImpl;
    }

    public StoredValue.StoredValueFactory getStoredValueImpl( )
    {
        return storedValueImpl;
    }
}
