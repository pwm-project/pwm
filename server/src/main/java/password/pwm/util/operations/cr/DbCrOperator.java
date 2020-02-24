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

package password.pwm.util.operations.cr;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiCrFactory;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmApplication;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.logging.PwmLogger;


public class DbCrOperator implements CrOperator
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( DbCrOperator.class );

    final PwmApplication pwmApplication;

    public DbCrOperator( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
    }

    public void close( )
    {
    }

    public ResponseSet readResponseSet(
            final ChaiUser theUser,
            final UserIdentity userIdentity,
            final String userGUID
    )
            throws PwmUnrecoverableException
    {
        if ( userGUID == null || userGUID.length() < 1 )
        {
            final String errorMsg = "user " + theUser.getEntryDN() + " does not have a guid, unable to search for responses in remote database";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_GUID, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        try
        {
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseService().getAccessor();
            final String responseStringBlob = databaseAccessor.get( DatabaseTable.PWM_RESPONSES, userGUID );
            if ( responseStringBlob != null && responseStringBlob.length() > 0 )
            {
                final ResponseSet userResponseSet = ChaiResponseSet.parseChaiResponseSetXML( responseStringBlob, theUser );
                LOGGER.debug( () -> "found responses for " + theUser.getEntryDN() + " in remote database: " + userResponseSet.toString() );
                return userResponseSet;
            }
            else
            {
                LOGGER.trace( () -> "user guid for " + theUser.getEntryDN() + " not found in remote database (key=" + userGUID + ")" );
            }
        }
        catch ( final ChaiValidationException e )
        {
            final String errorMsg = "unexpected error reading responses for " + theUser.getEntryDN() + " from remote database: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        catch ( final PwmOperationalException e )
        {
            final String errorMsg = "unexpected error reading responses for " + theUser.getEntryDN() + " from remote database: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( e.getErrorInformation().getError(), errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        return null;
    }

    public ResponseInfoBean readResponseInfo( final ChaiUser theUser, final UserIdentity userIdentity, final String userGUID )
            throws PwmUnrecoverableException
    {
        try
        {
            final ResponseSet responseSet = readResponseSet( theUser, userIdentity, userGUID );
            return responseSet == null ? null : CrOperators.convertToNoAnswerInfoBean( responseSet, DataStorageMethod.DB );
        }
        catch ( final ChaiException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_RESPONSES_NORESPONSES,
                    "unexpected error reading response info for " + theUser.getEntryDN() + ", error: " + e.getMessage()
            ) );
        }
    }

    public void clearResponses( final UserIdentity userIdentity, final ChaiUser theUser, final String userGUID )
            throws PwmUnrecoverableException
    {
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_MISSING_GUID,
                    "cannot clear responses to remote database, user " + theUser.getEntryDN() + " does not have a guid"
            ) );
        }

        try
        {
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseService().getAccessor();
            databaseAccessor.remove( DatabaseTable.PWM_RESPONSES, userGUID );
            LOGGER.info( () -> "cleared responses for user " + theUser.getEntryDN() + " in remote database" );
        }
        catch ( final DatabaseException e )
        {
            final ErrorInformation errorInfo = new ErrorInformation(
                    PwmError.ERROR_CLEARING_RESPONSES,
                    "unexpected error clearing responses for " + theUser.getEntryDN() + " in remote database, error: " + e.getMessage()
            );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( e );
            throw pwmOE;
        }
    }

    @Override
    public void writeResponses(
            final UserIdentity userIdentity,
            final ChaiUser theUser,
            final String userGUID,
            final ResponseInfoBean responseInfoBean
    )
            throws PwmUnrecoverableException
    {
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_MISSING_GUID,
                    "cannot save responses to remote database, user " + theUser.getEntryDN() + " does not have a guid" )
            );
        }

        LOGGER.trace( () -> "attempting to save responses for " + theUser.getEntryDN() + " in remote database (key=" + userGUID + ")" );

        try
        {
            final ChaiResponseSet responseSet = ChaiCrFactory.newChaiResponseSet(
                    responseInfoBean.getCrMap(),
                    responseInfoBean.getHelpdeskCrMap(),
                    responseInfoBean.getLocale(),
                    responseInfoBean.getMinRandoms(),
                    theUser.getChaiProvider().getChaiConfiguration(),
                    responseInfoBean.getCsIdentifier()
            );

            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseService().getAccessor();
            databaseAccessor.put( DatabaseTable.PWM_RESPONSES, userGUID, responseSet.stringValue() );
            LOGGER.info( () -> "saved responses for " + theUser.getEntryDN() + " in remote database (key=" + userGUID + ")" );
        }
        catch ( final ChaiException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( final DatabaseException e )
        {
            final ErrorInformation errorInfo = new ErrorInformation(
                    PwmError.ERROR_WRITING_RESPONSES,
                    "unexpected error saving responses for " + theUser.getEntryDN() + " in remote database: " + e.getMessage()
            );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            LOGGER.error( () -> errorInfo.toDebugStr() );
            pwmOE.initCause( e );
            throw pwmOE;
        }
    }
}
