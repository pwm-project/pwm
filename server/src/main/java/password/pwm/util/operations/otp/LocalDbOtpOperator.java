/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package password.pwm.util.operations.otp;

import com.novell.ldapchai.ChaiUser;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class LocalDbOtpOperator extends AbstractOtpOperator
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDbOtpOperator.class );
    private final LocalDB localDB;

    public LocalDbOtpOperator( final PwmApplication pwmApplication )
    {
        this.localDB = pwmApplication.getLocalDB();
        setPwmApplication( pwmApplication );
    }

    @Override
    public OTPUserRecord readOtpUserConfiguration(
            final UserIdentity theUser,
            final String userGUID
    )
            throws PwmUnrecoverableException
    {
        LOGGER.trace( () -> String.format( "Enter: readOtpUserConfiguration(%s, %s)", theUser, userGUID ) );
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_MISSING_GUID, "cannot save otp to localDB, user does not have a GUID" ) );
        }

        if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
        {
            final String errorMsg = "LocalDB is not available, unable to write user otp";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        OTPUserRecord otpConfig = null;
        try
        {
            final Configuration config = this.getPwmApplication().getConfig();
            String value = localDB.get( LocalDB.DB.OTP_SECRET, userGUID );
            if ( value != null && value.length() > 0 )
            {
                if ( config.readSettingAsBoolean( PwmSetting.OTP_SECRET_ENCRYPT ) )
                {
                    value = decryptAttributeValue( value );
                }
                if ( value != null )
                {
                    otpConfig = decomposeOtpAttribute( value );
                }
                if ( otpConfig != null )
                {
                    final OTPUserRecord finalRecord = otpConfig;
                    LOGGER.debug( () -> "found user OTP secret in LocalDB: " + finalRecord.toString() );
                }
            }
        }
        catch ( final LocalDBException e )
        {
            final String errorMsg = "unexpected LocalDB error reading otp: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        catch ( final PwmOperationalException e )
        {
            final String errorMsg = "unexpected error reading otp: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        return otpConfig;
    }

    @Override
    public void writeOtpUserConfiguration(
            final PwmRequest pwmRequest,
            final UserIdentity theUser,
            final String userGUID,
            final OTPUserRecord otpConfig
    )
            throws PwmUnrecoverableException
    {
        LOGGER.trace( pwmRequest, () -> String.format( "Enter: writeOtpUserConfiguration(%s, %s, %s)", theUser, userGUID, otpConfig ) );
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_MISSING_GUID, "cannot save otp to localDB, user does not have a pwmGUID" ) );
        }

        if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
        {
            final String errorMsg = "LocalDB is not available, unable to write user otp";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        try
        {
            final Configuration config = this.getPwmApplication().getConfig();
            String value = composeOtpAttribute( otpConfig );
            if ( config.readSettingAsBoolean( PwmSetting.OTP_SECRET_ENCRYPT ) )
            {
                LOGGER.debug( pwmRequest, () -> "Encrypting OTP secret for storage" );
                value = encryptAttributeValue( value );
            }

            localDB.put( LocalDB.DB.OTP_SECRET, userGUID, value );
            LOGGER.info( pwmRequest, () -> "saved OTP secret for user in LocalDB" );
        }
        catch ( final LocalDBException ex )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, "unexpected LocalDB error saving otp to localDB: " + ex.getMessage() );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( ex );
            throw pwmOE;
        }
        catch ( final PwmOperationalException ex )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving otp to localDB: " + ex.getMessage() );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( ex );
            throw pwmOE;
        }
    }

    @Override
    public void clearOtpUserConfiguration(
            final PwmRequest pwmRequest,
            final UserIdentity theUser,
            final ChaiUser chaiUser,
            final String userGUID
    )
            throws PwmUnrecoverableException
    {
        LOGGER.trace( pwmRequest, () -> String.format( "Enter: clearOtpUserConfiguration(%s, %s)", theUser, userGUID ) );
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_MISSING_GUID, "cannot save otp to localDB, user does not have a pwmGUID" ) );
        }

        if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
        {
            final String errorMsg = "LocalDB is not available, unable to write user OTP";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        try
        {
            localDB.remove( LocalDB.DB.OTP_SECRET, userGUID );
            LOGGER.info( pwmRequest, () -> "cleared OTP secret for user in LocalDB" );
        }
        catch ( final LocalDBException ex )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving otp to localDB: " + ex.getMessage() );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( ex );
            throw pwmOE;
        }
    }

    @Override
    public void close( )
    {
        // No operation
    }

}
