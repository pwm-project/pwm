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
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

public class LocalDbCrOperator implements CrOperator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDbCrOperator.class );

    private final LocalDB localDB;

    public LocalDbCrOperator( final LocalDB localDB )
    {
        this.localDB = localDB;
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
            final String errorMsg = "unable to read guid for user " + userIdentity.toString() + ", unable to search for responses in LocalDB";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_GUID, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        if ( localDB == null )
        {
            final String errorMsg = "LocalDB is not available, unable to search for user responses";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        try
        {
            final String responseStringBlob = localDB.get( LocalDB.DB.RESPONSE_STORAGE, userGUID );
            if ( responseStringBlob != null && responseStringBlob.length() > 0 )
            {
                final ResponseSet userResponseSet = ChaiResponseSet.parseChaiResponseSetXML( responseStringBlob, theUser );
                LOGGER.debug( () -> "found user responses in LocalDB: " + userResponseSet.toString() );
                return userResponseSet;
            }
        }
        catch ( final LocalDBException e )
        {
            final String errorMsg = "unexpected LocalDB error reading responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        catch ( final ChaiException e )
        {
            final String errorMsg = "unexpected chai error reading responses from LocalDB: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
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
            return responseSet == null ? null : CrOperators.convertToNoAnswerInfoBean( responseSet, DataStorageMethod.LOCALDB );
        }
        catch ( final ChaiException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES, "unexpected error reading response info " + e.getMessage() ) );
        }
    }

    public void clearResponses( final UserIdentity userIdentity, final ChaiUser theUser, final String userGUID ) throws PwmUnrecoverableException
    {
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_MISSING_GUID, "cannot clear responses to localDB, user does not have a pwmGUID" ) );
        }

        if ( localDB == null )
        {
            final String errorMsg = "LocalDB is not available, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        try
        {
            localDB.remove( LocalDB.DB.RESPONSE_STORAGE, userGUID );
            LOGGER.info( () -> "cleared responses for user " + theUser.getEntryDN() + " in local LocalDB" );
        }
        catch ( final LocalDBException e )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_CLEARING_RESPONSES, "unexpected LocalDB error clearing responses: " + e.getMessage() );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( e );
            throw pwmOE;
        }
    }

    public void writeResponses( final UserIdentity userIdentity, final ChaiUser theUser, final String userGUID, final ResponseInfoBean responseInfoBean )
            throws PwmUnrecoverableException
    {
        if ( userGUID == null || userGUID.length() < 1 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_MISSING_GUID, "cannot save responses to localDB, user does not have a pwmGUID" ) );
        }

        if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
        {
            final String errorMsg = "LocalDB is not available, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

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

            localDB.put( LocalDB.DB.RESPONSE_STORAGE, userGUID, responseSet.stringValue() );
            LOGGER.info( () -> "saved responses for user in LocalDB" );
        }
        catch ( final LocalDBException e )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_RESPONSES, "unexpected LocalDB error saving responses to localDB: " + e.getMessage() );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( e );
            throw pwmOE;
        }
        catch ( final ChaiException e )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses to localDB: " + e.getMessage() );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( e );
            throw pwmOE;
        }
    }
}
