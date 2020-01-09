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
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.logging.PwmLogger;

/**
 * @author mpieters
 */
public class DbOtpOperator extends AbstractOtpOperator
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( DbOtpOperator.class );

    public DbOtpOperator( final PwmApplication pwmApplication )
    {
        super.setPwmApplication( pwmApplication );
    }

    @Override
    public OTPUserRecord readOtpUserConfiguration( final UserIdentity theUser, final String userGUID )
            throws PwmUnrecoverableException
    {
        LOGGER.trace( () -> String.format( "Enter: readOtpUserConfiguration(%s, %s)", theUser, userGUID ) );
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_MISSING_GUID, "cannot save otp to db, user does not have a GUID" ) );
        }

        OTPUserRecord otpConfig = null;
        try
        {
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
            String value = databaseAccessor.get( DatabaseTable.OTP, userGUID );
            if ( value != null && value.length() > 0 )
            {
                if ( getPwmApplication().getConfig().readSettingAsBoolean( PwmSetting.OTP_SECRET_ENCRYPT ) )
                {
                    value = decryptAttributeValue( value );
                }
                if ( value != null )
                {
                    otpConfig = decomposeOtpAttribute( value );
                }
                if ( otpConfig != null )
                {
                    final OTPUserRecord finalOtpConfig = otpConfig;
                    LOGGER.debug( () -> "found user OTP secret in db: " + finalOtpConfig.toString() );
                }
            }
        }
        catch ( final PwmException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
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
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_MISSING_GUID,
                    "cannot save OTP secret to remote database, user " + theUser + " does not have a guid" ) );
        }

        LOGGER.trace( pwmRequest, () -> "attempting to save OTP secret for " + theUser + " in remote database (key=" + userGUID + ")" );

        try
        {
            String value = composeOtpAttribute( otpConfig );
            if ( getPwmApplication().getConfig().readSettingAsBoolean( PwmSetting.OTP_SECRET_ENCRYPT ) )
            {
                LOGGER.debug( pwmRequest, () -> "encrypting OTP secret for storage" );
                value = encryptAttributeValue( value );
            }
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.put( DatabaseTable.OTP, userGUID, value );
            LOGGER.debug( pwmRequest, () -> "saved OTP secret for " + theUser + " in remote database (key=" + userGUID + ")" );
        }
        catch ( final PwmOperationalException ex )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving otp to db: " + ex.getMessage() );
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
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation(
                            PwmError.ERROR_MISSING_GUID,
                            "cannot save OTP secret to remote database, user " + theUser + " does not have a guid"
                    ) );
        }

        LOGGER.trace( () -> "attempting to clear OTP secret for " + theUser + " in remote database (key=" + userGUID + ")" );

        try
        {
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.remove( DatabaseTable.OTP, userGUID );
            LOGGER.info( () -> "cleared OTP secret for " + theUser + " in remote database (key=" + userGUID + ")" );
        }
        catch ( final DatabaseException ex )
        {
            final ErrorInformation errorInfo = new ErrorInformation(
                    PwmError.ERROR_WRITING_OTP_SECRET,
                    "unexpected error saving otp to db: " + ex.getMessage()
            );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( ex );
            throw pwmOE;
        }
    }

    @Override
    public void close( )
    {
    }

}
