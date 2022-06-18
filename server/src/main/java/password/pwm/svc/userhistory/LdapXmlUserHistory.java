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

package password.pwm.svc.userhistory;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.ConfigObjectRecord;
import lombok.Value;
import org.jrivard.xmlchai.AccessMode;
import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlDocument;
import org.jrivard.xmlchai.XmlElement;
import org.jrivard.xmlchai.XmlFactory;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.user.UserInfo;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditEventType;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
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
public class LdapXmlUserHistory implements UserHistoryStore
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapXmlUserHistory.class );

    private static final String XML_ATTR_TIMESTAMP = "timestamp";
    private static final String XML_ATTR_TRANSACTION = "eventCode";
    private static final String XML_ATTR_SRC_IP = "srcIP";
    private static final String XML_ATTR_SRC_HOST = "srcHost";
    private static final String XML_NODE_ROOT = "history";
    private static final String XML_NODE_RECORD = "record";

    private static final String COR_RECORD_ID = "0001";

    private final PwmDomain pwmDomain;

    LdapXmlUserHistory( final PwmDomain pwmDomain )
    {
        this.pwmDomain = pwmDomain;
    }

    @Override
    public void updateUserHistory( final SessionLabel sessionLabel, final UserAuditRecord auditRecord )
            throws PwmUnrecoverableException
    {
        try
        {
            updateUserHistoryImpl( sessionLabel, auditRecord );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ).orElse( PwmError.ERROR_INTERNAL ) );
        }
    }

    private void updateUserHistoryImpl( final SessionLabel sessionLabel, final UserAuditRecord auditRecord )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        // user info
        final UserIdentity userIdentity;
        if ( auditRecord instanceof HelpdeskAuditRecord && auditRecord.getType() == AuditEventType.HELPDESK )
        {
            final HelpdeskAuditRecord helpdeskAuditRecord = ( HelpdeskAuditRecord ) auditRecord;
            userIdentity = UserIdentity.create( helpdeskAuditRecord.getTargetDN(), helpdeskAuditRecord.getTargetLdapProfile(), auditRecord.getDomain() );
        }
        else
        {
            userIdentity = UserIdentity.create( auditRecord.getPerpetratorDN(), auditRecord.getPerpetratorLdapProfile(), auditRecord.getDomain() );
        }
        final ChaiUser theUser = pwmDomain.getProxiedChaiUser( sessionLabel, userIdentity );

        // settings
        final String corRecordIdentifier = COR_RECORD_ID;
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmDomain.getPwmApplication().getConfig() );
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
            corList = ConfigObjectRecord.readRecordFromLDAP( theUser, corAttribute, corRecordIdentifier, null, null );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "error reading LDAP user event history for user " + userIdentity.toDisplayString() + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            LOGGER.error( sessionLabel, errorInformation::toDebugStr, e );
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
                theCor = ConfigObjectRecord.createNew( theUser, corAttribute, corRecordIdentifier, null, null );
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
        final int maxUserEvents = ( int ) pwmDomain.getConfig().readSettingAsLong( PwmSetting.EVENTS_LDAP_MAX_EVENTS );
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
            final ChaiUser theUser = pwmDomain.getProxiedChaiUser( sessionLabel, userInfo.getUserIdentity() );
            final StoredHistory storedHistory = readUserHistory( pwmDomain, sessionLabel, userInfo.getUserIdentity(), theUser );
            return storedHistory.asAuditRecords( AuditRecordFactory.make( sessionLabel, pwmDomain ), userInfo );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ).orElse( PwmError.ERROR_INTERNAL ) );
        }
    }

    private StoredHistory readUserHistory(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser chaiUser
    )
            throws ChaiUnavailableException
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmDomain.getPwmApplication().getConfig() );
        final String corAttribute = ldapProfile.readSettingAsString( PwmSetting.EVENTS_LDAP_ATTRIBUTE );

        if ( corAttribute == null || corAttribute.length() < 1 )
        {
            LOGGER.trace( sessionLabel, () -> "no user event log attribute configured, skipping read of log data" );
            return new StoredHistory();
        }

        try
        {
            final List<ConfigObjectRecord> corList = ConfigObjectRecord.readRecordFromLDAP( chaiUser, corAttribute, COR_RECORD_ID, null, null );

            if ( !CollectionUtil.isEmpty( corList ) )
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

        public void addEvent( final StoredEvent storedEvent )
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

        public List<UserAuditRecord> asAuditRecords( final AuditRecordFactory auditRecordFactory, final UserInfo userInfoBean )
        {
            final List<UserAuditRecord> returnList = new ArrayList<>( records.size() );
            for ( final StoredEvent loopEvent : records )
            {
                returnList.add( auditRecordFactory.fromStoredRecord( loopEvent, userInfoBean ) );
            }
            return Collections.unmodifiableList( returnList );
        }

        public String toXml( )
        {
            final XmlFactory xmlFactory = XmlChai.getFactory();
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
                        hrElement.setText( loopEvent.getMessage() );
                    }
                    doc.getRootElement().attachElement( hrElement );
                }
            }

            try ( ByteArrayOutputStream outputStream = new ByteArrayOutputStream() )
            {
                xmlFactory.output( doc,  outputStream, XmlFactory.OutputFlag.Compact );
                return outputStream.toString( PwmConstants.DEFAULT_CHARSET );
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
                final XmlFactory xmlFactory = XmlChai.getFactory();
                final XmlDocument xmlDocument = xmlFactory.parse( inputStream, AccessMode.IMMUTABLE );
                final XmlElement rootElement = xmlDocument.getRootElement();

                for ( final XmlElement hrElement : rootElement.getChildren( XML_NODE_RECORD ) )
                {
                    hrElement.getAttribute( XML_ATTR_TIMESTAMP ).ifPresent( ( timeStampStr ->
                    {
                        final long timeStamp = Long.parseLong( timeStampStr );
                        hrElement.getAttribute( XML_ATTR_TRANSACTION )
                                .flatMap( AuditEvent::forKey )
                                .ifPresent( ( eventCode ) ->
                        {
                            final String srcAddr = hrElement.getAttribute( XML_ATTR_SRC_IP ).orElse( "" );
                            final String srcHost = hrElement.getAttribute( XML_ATTR_SRC_HOST ).orElse( "" );
                            final String message = hrElement.getText().orElse( "" );
                            final StoredEvent storedEvent = new StoredEvent( eventCode, timeStamp, message, srcAddr, srcHost );
                            returnHistory.addEvent( storedEvent );
                        } );
                    } ) );
                }
            }
            catch ( final IOException e )
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

        public static StoredEvent fromAuditRecord( final UserAuditRecord auditRecord )
        {
            return new StoredEvent(
                    auditRecord.getEventCode(),
                    auditRecord.getTimestamp().toEpochMilli(),
                    auditRecord.getMessage(),
                    auditRecord.getSourceAddress(),
                    auditRecord.getSourceHost()
            );
        }
    }
}

