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

package password.pwm.svc.event;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.ConfigObjectRecord;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Wrapper class to handle user event history.
 *
 * @author Jason D. Rivard
 */
class LdapXmlUserHistory implements UserHistoryStore
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapXmlUserHistory.class );

    private static final String XML_ATTR_TIMESTAMP = "timestamp";
    private static final String XML_ATTR_TRANSACTION = "eventCode";
    private static final String XML_ATTR_SRC_IP = "srcIP";
    private static final String XML_ATTR_SRC_HOST = "srcHost";
    private static final String XML_NODE_ROOT = "history";
    private static final String XML_NODE_RECORD = "record";

    private static final String COR_RECORD_ID = "0001";

    private final PwmApplication pwmApplication;

    LdapXmlUserHistory( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
    }

    @Override
    public void updateUserHistory( final SessionLabel sessionLabel, final UserAuditRecord auditRecord )
            throws PwmUnrecoverableException
    {
        try
        {
            updateUserHistoryImpl( auditRecord );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ) );
        }
    }

    private void updateUserHistoryImpl( final UserAuditRecord auditRecord )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        // user info
        final UserIdentity userIdentity;
        if ( auditRecord instanceof HelpdeskAuditRecord && auditRecord.getType() == AuditEvent.Type.HELPDESK )
        {
            final HelpdeskAuditRecord helpdeskAuditRecord = ( HelpdeskAuditRecord ) auditRecord;
            userIdentity = UserIdentity.createUserIdentity( helpdeskAuditRecord.getTargetDN(), helpdeskAuditRecord.getTargetLdapProfile() );
        }
        else
        {
            userIdentity = UserIdentity.createUserIdentity( auditRecord.getPerpetratorDN(), auditRecord.getPerpetratorLdapProfile() );
        }
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );

        // settings
        final String corRecordIdentifer = COR_RECORD_ID;
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        final String corAttribute = ldapProfile.readSettingAsString( PwmSetting.EVENTS_LDAP_ATTRIBUTE );

        // quit if settings no good;
        if ( corAttribute == null || corAttribute.length() < 1 )
        {
            LOGGER.debug( () -> "no user event log attribute configured, skipping write of log data" );
            return;
        }

        // read current value;
        final StoredHistory storedHistory;
        final ConfigObjectRecord theCor;
        final List<ConfigObjectRecord> corList;
        try
        {
            corList = ConfigObjectRecord.readRecordFromLDAP( theUser, corAttribute, corRecordIdentifer, null, null );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "error reading LDAP user event history for user " + userIdentity.toDisplayString() + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            LOGGER.error( errorInformation::toDebugStr, e );
            throw new PwmUnrecoverableException( errorInformation, e );
        }

        try
        {
            if ( !corList.isEmpty() )
            {
                theCor = ( ConfigObjectRecord ) corList.get( 0 );
            }
            else
            {
                theCor = ConfigObjectRecord.createNew( theUser, corAttribute, corRecordIdentifer, null, null );
            }

            storedHistory = StoredHistory.fromXml( theCor.getPayload() );
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "ldap error writing user event log: " + e.getMessage() );
            return;
        }

        // add next record to blob
        final StoredEvent storedEvent = StoredEvent.fromAuditRecord( auditRecord );
        storedHistory.addEvent( storedEvent );

        // trim the blob.
        final int maxUserEvents = ( int ) pwmApplication.getConfig().readSettingAsLong( PwmSetting.EVENTS_LDAP_MAX_EVENTS );
        storedHistory.trim( maxUserEvents );

        // write the blob.
        try
        {
            theCor.updatePayload( storedHistory.toXml() );
        }
        catch ( final ChaiOperationException e )
        {
            LOGGER.error( () -> "ldap error writing user event log: " + e.getMessage() );
        }
    }

    @Override
    public List<UserAuditRecord> readUserHistory( final SessionLabel sessionLabel, final UserInfo userInfo )
            throws PwmUnrecoverableException
    {
        try
        {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userInfo.getUserIdentity() );
            final StoredHistory storedHistory = readUserHistory( pwmApplication, sessionLabel, userInfo.getUserIdentity(), theUser );
            return storedHistory.asAuditRecords( userInfo );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ) );
        }
    }

    private StoredHistory readUserHistory(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser chaiUser
    )
            throws ChaiUnavailableException
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        final String corAttribute = ldapProfile.readSettingAsString( PwmSetting.EVENTS_LDAP_ATTRIBUTE );

        if ( corAttribute == null || corAttribute.length() < 1 )
        {
            LOGGER.trace( sessionLabel, () -> "no user event log attribute configured, skipping read of log data" );
            return new StoredHistory();
        }

        try
        {
            final List<ConfigObjectRecord> corList = ConfigObjectRecord.readRecordFromLDAP( chaiUser, corAttribute, COR_RECORD_ID, null, null );

            if ( !JavaHelper.isEmpty( corList ) )
            {
                final ConfigObjectRecord theCor = corList.get( 0 );
                return StoredHistory.fromXml( theCor.getPayload() );
            }
        }
        catch ( final ChaiOperationException e )
        {
            LOGGER.error( sessionLabel, () -> "ldap error reading user event log: " + e.getMessage() );
        }
        return new StoredHistory();
    }

    public static class StoredHistory
    {
        private final Deque<StoredEvent> records = new ArrayDeque<>();

        void addEvent( final StoredEvent storedEvent )
        {
            records.add( storedEvent );
        }

        public void trim( final int size )
        {
            while ( records.size() > size )
            {
                records.removeFirst();
            }
        }

        List<UserAuditRecord> asAuditRecords( final UserInfo userInfoBean )
        {
            final List<UserAuditRecord> returnList = new ArrayList<>();
            for ( final StoredEvent loopEvent : records )
            {
                returnList.add( loopEvent.asAuditRecord( userInfoBean ) );
            }
            return Collections.unmodifiableList( returnList );
        }

        public String toXml( )
        {
            final XmlFactory xmlFactory = XmlFactory.getFactory();
            final XmlDocument doc = xmlFactory.newDocument( XML_NODE_ROOT );

            for ( final StoredEvent loopEvent : records )
            {
                if ( loopEvent.getAuditEvent() != null )
                {
                    final XmlElement hrElement = xmlFactory.newElement( XML_NODE_RECORD );
                    hrElement.setAttribute( XML_ATTR_TIMESTAMP, String.valueOf( loopEvent.getTimestamp() ) );
                    hrElement.setAttribute( XML_ATTR_TRANSACTION, loopEvent.getAuditEvent().getMessage().getKey() );
                    if ( loopEvent.getSourceAddress() != null && loopEvent.getSourceAddress().length() > 0 )
                    {
                        hrElement.setAttribute( XML_ATTR_SRC_IP, loopEvent.getSourceAddress() );
                    }
                    if ( loopEvent.getSourceHost() != null && loopEvent.getSourceHost().length() > 0 )
                    {
                        hrElement.setAttribute( XML_ATTR_SRC_HOST, loopEvent.getSourceHost() );
                    }
                    if ( loopEvent.getMessage() != null )
                    {
                        hrElement.addText( loopEvent.getMessage() );
                    }
                    doc.getRootElement().addContent( hrElement );
                }
            }

            try ( ByteArrayOutputStream outputStream = new ByteArrayOutputStream() )
            {
                xmlFactory.outputDocument( doc,  outputStream, XmlFactory.OutputFlag.Compact );
                return new String( outputStream.toByteArray(), PwmConstants.DEFAULT_CHARSET );
            }
            catch ( final IOException e )
            {
                throw new IllegalStateException( "error converting xml to string data: " + e.getMessage() );
            }
        }

        public static StoredHistory fromXml( final String input )
        {
            final StoredHistory returnHistory = new StoredHistory();

            if ( StringUtil.isEmpty( input ) )
            {
                return returnHistory;
            }

            try ( InputStream inputStream = new ByteArrayInputStream( input.getBytes( PwmConstants.DEFAULT_CHARSET ) ) )
            {
                final XmlFactory xmlFactory = XmlFactory.getFactory();
                final XmlDocument xmlDocument = xmlFactory.parseXml( inputStream );
                final XmlElement rootElement = xmlDocument.getRootElement();

                for ( final XmlElement hrElement : rootElement.getChildren( XML_NODE_RECORD ) )
                {
                    final String timeStampStr = hrElement.getAttributeValue( XML_ATTR_TIMESTAMP );
                    final long timeStamp = Long.parseLong( timeStampStr );
                    final String transactionCode = hrElement.getAttributeValue( XML_ATTR_TRANSACTION );
                    AuditEvent.forKey( transactionCode ).ifPresent( ( eventCode ) ->
                    {
                        final String srcAddr = hrElement.getAttributeValue( XML_ATTR_SRC_IP ) != null ? hrElement.getAttributeValue( XML_ATTR_SRC_IP ) : "";
                        final String srcHost = hrElement.getAttributeValue( XML_ATTR_SRC_HOST ) != null ? hrElement.getAttributeValue( XML_ATTR_SRC_HOST ) : "";
                        final String message = hrElement.getText();
                        final StoredEvent storedEvent = new StoredEvent( eventCode, timeStamp, message, srcAddr, srcHost );
                        returnHistory.addEvent( storedEvent );
                    } );
                }
            }
            catch ( final PwmUnrecoverableException | IOException e )
            {
                LOGGER.error( () -> "error parsing user event history record: " + e.getMessage() );
            }
            return returnHistory;
        }
    }

    @Value
    public static class StoredEvent implements Serializable
    {
        private final AuditEvent auditEvent;
        private final long timestamp;
        private final String message;
        private final String sourceAddress;
        private final String sourceHost;

        static StoredEvent fromAuditRecord( final UserAuditRecord auditRecord )
        {
            return new StoredEvent(
                    auditRecord.getEventCode(),
                    auditRecord.getTimestamp().toEpochMilli(),
                    auditRecord.getMessage(),
                    auditRecord.getSourceAddress(),
                    auditRecord.getSourceHost()
            );
        }

        UserAuditRecord asAuditRecord( final UserInfo userInfoBean )
        {
            return new UserAuditRecord(
                    Instant.ofEpochMilli( this.getTimestamp() ),
                    this.getAuditEvent(),
                    null,
                    null,
                    userInfoBean.getUserIdentity().getUserDN(),
                    this.getMessage(),
                    this.getSourceAddress(),
                    this.getSourceHost()
            );
        }
    }
}

