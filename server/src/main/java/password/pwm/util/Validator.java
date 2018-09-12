/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.util;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.FormNonce;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.util.logging.PwmLogger;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utility class for validating parameters, passwords and user input.
 *
 * @author Jason D. Rivard
 */
public class Validator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( Validator.class );

    public static final String PARAM_CONFIRM_SUFFIX = "_confirm";

    public static void validatePwmFormID( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final String submittedPwmFormID = pwmRequest.readParameterAsString( PwmConstants.PARAM_FORM_ID );

        if ( pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.SECURITY_ENABLE_FORM_NONCE ) )
        {
            final FormNonce formNonce = pwmRequest.getPwmApplication().getSecureService().decryptObject(
                    submittedPwmFormID,
                    FormNonce.class
            );
            if ( formNonce == null )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INVALID_FORMID, "form nonce missing" ) );
            }
            if ( !pwmSession.getLoginInfoBean().getGuid().equals( formNonce.getSessionGUID() ) )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INVALID_FORMID, "form nonce incorrect" ) );
            }
        }
    }

    public static void validatePwmRequestCounter( final PwmRequest pwmRequest )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final boolean enforceRequestSequencing = Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.SECURITY_HTTP_FORCE_REQUEST_SEQUENCING ) );

        if ( enforceRequestSequencing )
        {
            final String requestVerificationKey = String.valueOf( pwmSession.getLoginInfoBean().getReqCounter() );

            final String submittedPwmFormID = pwmRequest.readParameterAsString( PwmConstants.PARAM_FORM_ID );
            if ( submittedPwmFormID == null || submittedPwmFormID.isEmpty() )
            {
                return;
            }

            try
            {
                final FormNonce formNonce = pwmRequest.getPwmApplication().getSecureService().decryptObject(
                        submittedPwmFormID,
                        FormNonce.class
                );
                final String submittedRequestVerificationKey = String.valueOf( formNonce.getReqCounter() );
                if ( !requestVerificationKey.equals( submittedRequestVerificationKey ) )
                {
                    final String debugMsg = "expectedPageID=" + requestVerificationKey
                            + ", submittedPageID=" + submittedRequestVerificationKey
                            + ", url=" + pwmRequest.getURL().toString();

                    throw new PwmOperationalException( PwmError.ERROR_INCORRECT_REQ_SEQUENCE, debugMsg );
                }
            }
            catch ( StringIndexOutOfBoundsException | NumberFormatException e )
            {
                throw new PwmOperationalException( PwmError.ERROR_INCORRECT_REQ_SEQUENCE );
            }
        }
    }


    public static String sanitizeInputValue(
            final Configuration config,
            final String input,
            final int maxLength
    )
    {
        String theString = input == null ? "" : input;

        final int max = ( maxLength < 1 )
                ? 10 * 1024
                : maxLength;

        // strip off any length beyond the specified maxLength.
        if ( theString.length() > max )
        {
            theString = theString.substring( 0, max );
        }

        // strip off any disallowed chars.
        if ( config != null )
        {
            final List<String> disallowedInputs = config.readSettingAsStringArray( PwmSetting.DISALLOWED_HTTP_INPUTS );
            for ( final String testString : disallowedInputs )
            {
                final String newString = theString.replaceAll( testString, "" );
                if ( !newString.equals( theString ) )
                {
                    LOGGER.warn( "removing potentially malicious string values from input, converting '" + input + "' newValue=" + newString + "' pattern='" + testString + "'" );
                    theString = newString;
                }
            }
        }

        return theString;
    }


    public static String sanitizeHeaderValue( final Configuration configuration, final String input )
    {
        if ( input == null )
        {
            return null;
        }

        final String regexStripPatternStr = configuration.readAppProperty( AppProperty.SECURITY_HTTP_STRIP_HEADER_REGEX );
        if ( regexStripPatternStr != null && !regexStripPatternStr.isEmpty() )
        {
            final Pattern pattern = Pattern.compile( regexStripPatternStr );
            final Matcher matcher = pattern.matcher( input );
            final String output = matcher.replaceAll( "" );
            if ( !input.equals( output ) )
            {
                LOGGER.warn( "stripped potentially harmful chars from value: input=" + input + " strippedOutput=" + output );
            }
            return output;
        }
        return input;
    }
}

