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

package password.pwm.error;

import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.http.PwmSession;

import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

/**
 * An ErrorInformation is a package of error data generated within PWM.  Error information includes an error code
 * (in the form of an {@link PwmError}), additional detailed error information for logging, and string substitutions
 * to use when presenting error messages to users.
 */
public class ErrorInformation implements Serializable
{
    private final PwmError error;
    private final String detailedErrorMsg;
    private final String userStrOverride;
    private final String[] fieldValues;
    private final Instant date = Instant.now();

    // private constructor used for gson de-serialization
    private ErrorInformation( )
    {
        error = PwmError.ERROR_UNKNOWN;
        detailedErrorMsg = null;
        fieldValues = null;
        userStrOverride = null;
    }

    public ErrorInformation( final PwmError error )
    {
        this.error = error == null ? PwmError.ERROR_UNKNOWN : error;
        this.detailedErrorMsg = null;
        this.userStrOverride = null;
        this.fieldValues = new String[ 0 ];
    }

    public ErrorInformation( final PwmError error, final String detailedErrorMsg )
    {
        this.error = error == null ? PwmError.ERROR_UNKNOWN : error;
        this.detailedErrorMsg = detailedErrorMsg;
        this.userStrOverride = null;
        this.fieldValues = new String[ 0 ];
    }

    public ErrorInformation( final PwmError error, final String detailedErrorMsg, final String[] fields )
    {
        this.error = error == null ? PwmError.ERROR_UNKNOWN : error;
        this.detailedErrorMsg = detailedErrorMsg;
        this.userStrOverride = null;
        this.fieldValues = fields == null ? new String[ 0 ] : fields;
    }

    public ErrorInformation( final PwmError error, final String detailedErrorMsg, final String userStrOverride, final String[] fields )
    {
        this.error = error == null ? PwmError.ERROR_UNKNOWN : error;
        this.detailedErrorMsg = detailedErrorMsg;
        this.userStrOverride = userStrOverride;
        this.fieldValues = fields == null ? new String[ 0 ] : fields;
    }

    public String getDetailedErrorMsg( )
    {
        return detailedErrorMsg;
    }

    public PwmError getError( )
    {
        return error;
    }

    public String[] getFieldValues( )
    {
        return fieldValues == null ? null : Arrays.copyOf( fieldValues, fieldValues.length );
    }

    public String toDebugStr( )
    {
        final StringBuilder sb = new StringBuilder();
        sb.append( error.getErrorCode() );
        sb.append( " " );
        sb.append( error.toString() );
        if ( detailedErrorMsg != null && detailedErrorMsg.length() > 0 )
        {
            sb.append( " (" );
            sb.append( detailedErrorMsg );
            sb.append( ( ")" ) );
        }

        if ( fieldValues != null && fieldValues.length > 0 )
        {
            sb.append( " fields: " );
            sb.append( Arrays.toString( fieldValues ) );
        }

        return sb.toString();
    }

    public String toUserStr( final PwmSession pwmSession, final PwmApplication pwmApplication )
    {

        if ( userStrOverride != null )
        {
            return userStrOverride;
        }

        Configuration config = null;
        Locale userLocale = null;

        if ( pwmSession != null && pwmApplication.getConfig() != null )
        {
            config = pwmApplication.getConfig();
        }

        if ( pwmSession != null )
        {
            userLocale = pwmSession.getSessionStateBean().getLocale();
        }

        return toUserStr( userLocale, config );
    }

    public String toUserStr( final Locale userLocale, final Configuration config )
    {
        if ( userStrOverride != null )
        {
            return userStrOverride;
        }

        if ( this.getError().getErrorCode() == 6000 )
        {
            return detailedErrorMsg;
        }

        return this.getError().getLocalizedMessage( userLocale, config, fieldValues );
    }

    public Instant getDate( )
    {
        return date;
    }

    public ErrorInformation wrapWithNewErrorCode( final PwmError pwmError )
    {
        if ( pwmError == this.getError() )
        {
            return this;
        }
        return new ErrorInformation( pwmError, this.getDetailedErrorMsg() );

    }
}
