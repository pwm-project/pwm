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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package password.pwm.svc.otp;

import com.novell.ldapchai.ChaiUser;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.svc.db.DatabaseAccessor;
import password.pwm.svc.db.DatabaseException;
import password.pwm.svc.db.DatabaseTable;
import password.pwm.util.logging.PwmLogger;

import java.util.Optional;

/**
 * @author mpieters
 */
public class DbOtpOperator extends AbstractOtpOperator
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( DbOtpOperator.class );

    public DbOtpOperator( final PwmDomain pwmDomain )
    {
        super.setPwmApplication( pwmDomain );
    }

    @Override
    public Optional<OTPUserRecord> readOtpUserConfiguration( final SessionLabel sessionLabel, final UserIdentity theUser, final String userGUID )
            throws PwmUnrecoverableException
    {
        LOGGER.trace( () -> String.format( "Enter: readOtpUserConfiguration(%s, %s)", theUser, userGUID ) );
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_MISSING_GUID, "cannot save otp to db, user does not have a GUID" ) );
        }

        try
        {
            final DatabaseAccessor databaseAccessor = pwmDomain.getPwmApplication().getDatabaseAccessor();
            final Optional<String> strValue = databaseAccessor.get( DatabaseTable.OTP, userGUID );
            if ( strValue.isPresent() )
            {
                if ( getPwmApplication().getConfig().readSettingAsBoolean( PwmSetting.OTP_SECRET_ENCRYPT ) )
                {
                    final String decryptAttributeValue = decryptAttributeValue( strValue.get() );
                    if ( decryptAttributeValue != null )
                    {
                        final OTPUserRecord otpConfig = decomposeOtpAttribute( decryptAttributeValue );
                        if ( otpConfig != null )
                        {
                            LOGGER.debug( sessionLabel, () -> "found user OTP secret in db: " + otpConfig );
                            return Optional.of( otpConfig );
                        }
                    }
                }
            }
        }
        catch ( final PwmException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }
        return Optional.empty();
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
            final DatabaseAccessor databaseAccessor = pwmDomain.getPwmApplication().getDatabaseAccessor();
            databaseAccessor.put( DatabaseTable.OTP, userGUID, value );
            LOGGER.debug( pwmRequest, () -> "saved OTP secret for " + theUser + " in remote database (key=" + userGUID + ")" );
        }
        catch ( final PwmOperationalException ex )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving otp to db: " + ex.getMessage() );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo, ex );
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
            final DatabaseAccessor databaseAccessor = pwmDomain.getPwmApplication().getDatabaseAccessor();
            databaseAccessor.remove( DatabaseTable.OTP, userGUID );
            LOGGER.info( () -> "cleared OTP secret for " + theUser + " in remote database (key=" + userGUID + ")" );
        }
        catch ( final DatabaseException ex )
        {
            final ErrorInformation errorInfo = new ErrorInformation(
                    PwmError.ERROR_WRITING_OTP_SECRET,
                    "unexpected error saving otp to db: " + ex.getMessage()
            );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo, ex );
            throw pwmOE;
        }
    }

    @Override
    public void close( )
    {
    }

}
