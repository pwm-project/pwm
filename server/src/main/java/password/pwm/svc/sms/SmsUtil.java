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

package password.pwm.svc.sms;

import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SmsUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SmsUtil.class );

    private SmsUtil()
    {
    }

    static String smsDataEncode( final String data, final SmsDataEncoding encoding )
    {
        final String normalizedString = data == null ? "" : data;

        return switch ( encoding )
                {
                    case URL -> StringUtil.urlEncode( normalizedString );
                    case CSV -> StringUtil.escapeCsv( normalizedString );
                    case HTML -> StringUtil.escapeHtml( normalizedString );
                    case JAVA -> StringUtil.escapeJava( normalizedString );
                    case JAVASCRIPT -> StringUtil.escapeJS( normalizedString );
                    case XML -> StringUtil.escapeXml( normalizedString );
                    default -> normalizedString;
                };
    }

    static void determineIfResultSuccessful(
            final AppConfig config,
            final int resultCode,
            final String resultBody
    )
            throws PwmOperationalException
    {
        final List<String> resultCodeTests = config.readSettingAsStringArray( PwmSetting.SMS_SUCCESS_RESULT_CODE );
        if ( resultCodeTests != null && !resultCodeTests.isEmpty() )
        {
            final String resultCodeStr = String.valueOf( resultCode );
            if ( !resultCodeTests.contains( resultCodeStr ) )
            {
                throw new PwmOperationalException( new ErrorInformation(
                        PwmError.ERROR_SMS_SEND_ERROR,
                        "response result code " + resultCode + " is not a configured successful result code"
                ) );
            }
        }

        final List<String> regexBodyTests = config.readSettingAsStringArray( PwmSetting.SMS_RESPONSE_OK_REGEX );
        if ( regexBodyTests == null || regexBodyTests.isEmpty() )
        {
            return;
        }

        if ( resultBody == null || resultBody.isEmpty() )
        {
            throw new PwmOperationalException( new ErrorInformation(
                    PwmError.ERROR_SMS_SEND_ERROR,
                    "result has no body but there are configured regex response matches, so send not considered successful"
            ) );
        }

        for ( final String regex : regexBodyTests )
        {
            final Pattern p = Pattern.compile( regex, Pattern.DOTALL );
            final Matcher m = p.matcher( resultBody );
            if ( m.matches() )
            {
                LOGGER.trace( () -> "result body matched configured regex match setting: " + regex );
                return;
            }
        }

        throw new PwmOperationalException( new ErrorInformation(
                PwmError.ERROR_SMS_SEND_ERROR,
                "result body did not matching any configured regex match settings"
        ) );
    }

    static String formatSmsNumber( final AppConfig config, final String smsNumber )
    {
        final SmsNumberFormat format = config.readSettingAsEnum( PwmSetting.SMS_PHONE_NUMBER_FORMAT, SmsNumberFormat.class );

        if ( format == SmsNumberFormat.RAW )
        {
            return smsNumber;
        }

        final long ccLong = config.readSettingAsLong( PwmSetting.SMS_DEFAULT_COUNTRY_CODE );
        String countryCodeNumber = "";
        if ( ccLong > 0 )
        {
            countryCodeNumber = String.valueOf( ccLong );
        }

        String returnValue = smsNumber;

        // Remove (0)
        returnValue = returnValue.replaceAll( "\\(0\\)", "" );

        // Remove leading double zero, replace by plus
        if ( returnValue.startsWith( "00" ) )
        {
            returnValue = "+" + returnValue.substring( 2 );
        }

        // Replace leading zero by country code
        if ( returnValue.startsWith( "0" ) )
        {
            returnValue = countryCodeNumber + returnValue.substring( 1 );
        }

        // Add a leading plus if necessary
        if ( !returnValue.startsWith( "+" ) )
        {
            returnValue = "+" + returnValue;
        }

        // Remove any non-numeric, non-plus characters
        returnValue = returnValue.replaceAll( "[^0-9\\+]", "" );

        // Now the number should be in full international format
        // Let's see if we need to change anything:
        switch ( format )
        {
            case PLAIN:
                // remove plus
                returnValue = returnValue.replaceAll( "^\\+", "" );

                // add country code
                returnValue = countryCodeNumber + returnValue;
                break;
            case PLUS:
                // keep full international format
                break;
            case ZEROS:
                // replace + with 00
                returnValue = "00" + returnValue.substring( 1 );
                break;
            default:
                // keep full international format
                break;
        }
        return returnValue;
    }
}
