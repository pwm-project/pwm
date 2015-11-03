/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.svc.event;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.ConfigObjectRecord;
import org.jdom2.CDATA;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Wrapper class to handle user event history.
 *
 * @author Jason D. Rivard
 */
class LdapXmlUserHistory implements UserHistoryStore, Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(LdapXmlUserHistory.class);

    private static final String XML_ATTR_TIMESTAMP = "timestamp";
    private static final String XML_ATTR_TRANSACTION = "eventCode";
    private static final String XML_ATTR_SRC_IP = "srcIP";
    private static final String XML_ATTR_SRC_HOST = "srcHost";
    private static final String XML_NODE_ROOT = "history";
    private static final String XML_NODE_RECORD = "record";

    private static final String COR_RECORD_ID = "0001";

    // -------------------------- STATIC METHODS --------------------------
    private final PwmApplication pwmApplication;

    LdapXmlUserHistory(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public void updateUserHistory(final UserAuditRecord auditRecord)
            throws PwmUnrecoverableException
    {
        try {
            updateUserHistoryImpl(auditRecord);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
        }
    }

    void updateUserHistoryImpl(final UserAuditRecord auditRecord)
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        // user info
        final UserIdentity userIdentity;
        if (auditRecord instanceof HelpdeskAuditRecord && auditRecord.getType() == AuditEvent.Type.HELPDESK) {
            final HelpdeskAuditRecord helpdeskAuditRecord = (HelpdeskAuditRecord)auditRecord;
            userIdentity = new UserIdentity(helpdeskAuditRecord.getTargetDN(),helpdeskAuditRecord.getTargetLdapProfile());
        } else {
            userIdentity = new UserIdentity(auditRecord.getPerpetratorDN(),auditRecord.getPerpetratorLdapProfile());
        }
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);

        // settings
        final String corRecordIdentifer = COR_RECORD_ID;
        final String corAttribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.EVENTS_LDAP_ATTRIBUTE);

        // quit if settings no good;
        if (corAttribute == null || corAttribute.length() < 1) {
            LOGGER.debug("no user event log attribute configured, skipping write of log data");
            return;
        }

        // read current value;
        final StoredHistory storedHistory;
        final ConfigObjectRecord theCor;
        try {
            final List corList = ConfigObjectRecord.readRecordFromLDAP(theUser, corAttribute, corRecordIdentifer, null, null);
            if (!corList.isEmpty()) {
                theCor = (ConfigObjectRecord) corList.get(0);
            } else {
                theCor = ConfigObjectRecord.createNew(theUser, corAttribute, corRecordIdentifer, null, null);
            }

            storedHistory = StoredHistory.fromXml(theCor.getPayload());
        } catch (ChaiOperationException e) {
            LOGGER.error("ldap error writing user event log: " + e.getMessage());
            return;
        }

        // add next record to blob
        final StoredEvent storedEvent = StoredEvent.fromAuditRecord(auditRecord);
        storedHistory.addEvent(storedEvent);

        // trim the blob.
        final int maxUserEvents = (int) pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_LDAP_MAX_EVENTS);
        storedHistory.trim(maxUserEvents);

        // write the blob.
        try {
            theCor.updatePayload(storedHistory.toXml());
        } catch (ChaiOperationException e) {
            LOGGER.error("ldap error writing user event log: " + e.getMessage());
        }
    }

    public List<UserAuditRecord> readUserHistory(final UserInfoBean userInfoBean)
            throws PwmUnrecoverableException
    {
        try {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userInfoBean.getUserIdentity());
            final StoredHistory storedHistory = readUserHistory(pwmApplication, theUser);
            return storedHistory.asAuditRecords(userInfoBean);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
        }
    }

    private StoredHistory readUserHistory(
            final PwmApplication pwmApplication,
            final ChaiUser chaiUser
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final String corRecordIdentifer = COR_RECORD_ID;
        final String corAttribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.EVENTS_LDAP_ATTRIBUTE);

        if (corAttribute == null || corAttribute.length() < 1) {
            LOGGER.trace("no user event log attribute configured, skipping read of log data");
            return new StoredHistory();
        }

        try {
            final List corList = ConfigObjectRecord.readRecordFromLDAP(chaiUser, corAttribute, corRecordIdentifer, null, null);

            if (!corList.isEmpty()) {
                final ConfigObjectRecord theCor = (ConfigObjectRecord) corList.get(0);
                return StoredHistory.fromXml(theCor.getPayload());
            }
        } catch (ChaiOperationException e) {
            LOGGER.error("ldap error reading user event log: " + e.getMessage());
        }
        return new StoredHistory();
    }

    private static class StoredHistory {
        private final LinkedList<StoredEvent> records = new LinkedList<>();

        public void addEvent(final StoredEvent storedEvent) {
            records.add(storedEvent);
        }

        public void trim(final int size) {
            while (records.size() > size) {
                records.removeFirst();
            }
        }

        public List<UserAuditRecord> asAuditRecords(final UserInfoBean userInfoBean) {
            final List<UserAuditRecord> returnList = new LinkedList<>();
            for (final StoredEvent loopEvent : records) {
                returnList.add(loopEvent.asAuditRecord(userInfoBean));
            }
            return Collections.unmodifiableList(returnList);
        }

        public String toXml() {
            final Element rootElement = new Element(XML_NODE_ROOT);

            for (final StoredEvent loopEvent : records) {
                if (loopEvent.getAuditEvent() != null) {
                    final Element hrElement = new Element(XML_NODE_RECORD);
                    hrElement.setAttribute(XML_ATTR_TIMESTAMP, String.valueOf(loopEvent.getTimestamp()));
                    hrElement.setAttribute(XML_ATTR_TRANSACTION, loopEvent.getAuditEvent().getMessage().getKey());
                    if (loopEvent.getSourceAddress() != null && loopEvent.getSourceAddress().length() > 0) {
                        hrElement.setAttribute(XML_ATTR_SRC_IP,loopEvent.getSourceAddress());
                    }
                    if (loopEvent.getSourceHost() != null && loopEvent.getSourceHost().length() > 0) {
                        hrElement.setAttribute(XML_ATTR_SRC_HOST,loopEvent.getSourceHost());
                    }
                    if (loopEvent.getMessage() != null) {
                        hrElement.setContent(new CDATA(loopEvent.getMessage()));
                    }
                    rootElement.addContent(hrElement);
                }
            }

            final Document doc = new Document(rootElement);
            final XMLOutputter outputter = new XMLOutputter();
            outputter.setFormat(Format.getCompactFormat());
            return outputter.outputString(doc);
        }

        public static StoredHistory fromXml(final String input) {
            final StoredHistory returnHistory = new StoredHistory();

            if (input == null || input.length() < 1) {
                return returnHistory;
            }

            try {
                final SAXBuilder builder = new SAXBuilder();
                final Document doc = builder.build(new StringReader(input));
                final Element rootElement = doc.getRootElement();

                for (final Element hrElement : rootElement.getChildren(XML_NODE_RECORD)) {
                    final long timeStamp = hrElement.getAttribute(XML_ATTR_TIMESTAMP).getLongValue();
                    final String transactionCode = hrElement.getAttribute(XML_ATTR_TRANSACTION).getValue();
                    final AuditEvent eventCode = AuditEvent.forKey(transactionCode);
                    final String srcAddr = hrElement.getAttribute(XML_ATTR_SRC_IP) != null ? hrElement.getAttribute(XML_ATTR_SRC_IP).toString() : "";
                    final String srcHost = hrElement.getAttribute(XML_ATTR_SRC_HOST) != null ? hrElement.getAttribute(XML_ATTR_SRC_HOST).toString() : "";
                    final String message = hrElement.getText();
                    final StoredEvent storedEvent = new StoredEvent(eventCode,timeStamp,message,srcAddr,srcHost);
                    returnHistory.addEvent(storedEvent);
                }
            } catch (JDOMException e) {
                LOGGER.error("error parsing user event history record: " + e.getMessage());
            } catch (IOException e) {
                LOGGER.error("error parsing user event history record: " + e.getMessage());
            }
            return returnHistory;
        }
    }

    private static class StoredEvent implements Serializable {
        private AuditEvent auditEvent;
        private long timestamp;
        private String message;
        private String sourceAddress;
        private String sourceHost;


        private StoredEvent(AuditEvent auditEvent, long timestamp, String message, String sourceAddress, String sourceHost) {
            this.auditEvent = auditEvent;
            this.timestamp = timestamp;
            this.message = message;
            this.sourceAddress = sourceAddress;
            this.sourceHost = sourceHost;
        }

        public AuditEvent getAuditEvent() {
            return auditEvent;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getMessage() {
            return message;
        }

        public String getSourceAddress() {
            return sourceAddress;
        }

        public String getSourceHost() {
            return sourceHost;
        }

        public static StoredEvent fromAuditRecord(final UserAuditRecord auditRecord) {
            return new StoredEvent(auditRecord.getEventCode(),auditRecord.getTimestamp().getTime(),auditRecord.getMessage(),auditRecord.getSourceAddress(),auditRecord.getSourceHost());
        }

        public UserAuditRecord asAuditRecord(final UserInfoBean userInfoBean) {
            return UserAuditRecord.create(
                    new Date(this.getTimestamp()),
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

// -------------------------- INNER CLASSES --------------------------

}

