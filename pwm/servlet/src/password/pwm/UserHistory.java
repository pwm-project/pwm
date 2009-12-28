/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.ConfigObjectRecord;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;
import org.jdom.CDATA;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.*;

/**
 * Wrapper class to handle user event history.
 *
 * @author Jason D. Rivard
 */
public class UserHistory implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserHistory.class);

    private static final String XML_ATTR_TIMESTAMP = "timestamp";
    private static final String XML_ATTR_TRANSACTION = "eventCode";
    private static final String XML_NODE_ROOT = "history";
    private static final String XML_NODE_RECORD = "record";

    private final LinkedList<Record> records = new LinkedList<Record>();
    private int maxSize;

// -------------------------- STATIC METHODS --------------------------

    public static void updateUserHistory(
            final PwmSession pwmSession,
            final Record.Event eventCode,
            final String message
    )
            throws ChaiUnavailableException, PwmException
    {
        final ChaiUser theUser = pwmSession.getContextManager().getProxyChaiUserActor(pwmSession);
        updateUserHistory(pwmSession, theUser, eventCode, message);
    }

    public static void updateUserHistory(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final Record.Event eventCode,
            final String message
    )
            throws ChaiUnavailableException, PwmException
    {
        final String corRecordIdentifer = "0001";
        final Record record = new Record(eventCode, message);
        final String corAttribute = pwmSession.getConfig().readSettingAsString(PwmSetting.EVENT_LOG_ATTRIBUTE);

        if (corAttribute == null || corAttribute.length() < 1) {
            LOGGER.debug(pwmSession,"no user event log attribute configured, skipping write of log data");
            return;
        }

        try {
            final ConfigObjectRecord theCor;
            final List corList = ConfigObjectRecord.readRecordFromLDAP(theUser, corAttribute, corRecordIdentifer, null, null);
            if (!corList.isEmpty()) {
                theCor = (ConfigObjectRecord) corList.get(0);
            } else {
                theCor = ConfigObjectRecord.createNew(theUser, corAttribute, corRecordIdentifer, null, null);
            }
            final UserHistory history = new UserHistory(pwmSession.getConfig().readSettingAsInt(PwmSetting.EVENT_LOG_MAX_EVENTS_USER), theCor.getPayload());
            history.addEvent(record);
            theCor.updatePayload(history.getCurrentPayload());
            LOGGER.info(pwmSession, "user log event " + eventCode + " written to user " + theUser.getEntryDN() );
        } catch (ChaiOperationException e) {
            LOGGER.error("ldap error writing user event log: " + e.getMessage());
        }
    }

    public void addEvent(final Record record)
    {
        records.add(record);
        trim();
    }

    private void trim()
    {
        while (records.size() > maxSize) {
            records.removeFirst();
        }
    }

    public String getCurrentPayload()
    {
        final Element rootElement = new Element(XML_NODE_ROOT);

        for (final Object record1 : records) {
            final Record record = (Record) record1;
            final Element hrElement = new Element(XML_NODE_RECORD);
            hrElement.setAttribute(XML_ATTR_TIMESTAMP, String.valueOf(record.getTimestamp()));
            hrElement.setAttribute(XML_ATTR_TRANSACTION, record.eventCode.getMessage().getResourceKey());
            if (record.getMessage() != null) {
                hrElement.setContent(new CDATA(record.getMessage()));
            }
            rootElement.addContent(hrElement);
        }

        final Document doc = new Document(rootElement);
        final XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getCompactFormat());

        return outputter.outputString(doc);
    }

    public static UserHistory readUserHistory(final PwmSession pwmSession)
            throws ChaiUnavailableException, PwmException
    {
        final String corRecordIdentifer = "0001";
        final String corAttribute = pwmSession.getConfig().readSettingAsString(PwmSetting.EVENT_LOG_ATTRIBUTE);
        final int maxUserEvents = pwmSession.getConfig().readSettingAsInt(PwmSetting.EVENT_LOG_MAX_EVENTS_USER);

        if (corAttribute == null || corAttribute.length() < 1) {
            LOGGER.trace(pwmSession,"no user event log attribute configured, skipping write of log data");
            return new UserHistory(maxUserEvents);
        }

        try {
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser actor = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
            final List corList = ConfigObjectRecord.readRecordFromLDAP(actor, corAttribute, corRecordIdentifer, null, null);

            if (!corList.isEmpty()) {
                final ConfigObjectRecord theCor = (ConfigObjectRecord) corList.get(0);
                return new UserHistory(maxUserEvents, theCor.getPayload());
            }
        } catch (ChaiOperationException e) {
            LOGGER.error(pwmSession, "ldap error reading user event log: " + e.getMessage());
        }
        return new UserHistory(maxUserEvents);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public UserHistory(final int maxSize)
    {
        this.maxSize = maxSize;
    }

    public UserHistory(final int maxSize, final String xmlPayload)
    {
        this(maxSize);
        parseInputString(xmlPayload);
        trim();
    }

    private void parseInputString(final String input)
    {
        if (input == null || input.length() < 1) {
            return;
        }

        try {
            final SAXBuilder builder = new SAXBuilder();
            final Document doc = builder.build(new StringReader(input));
            final Element rootElement = doc.getRootElement();

            for (final Object o : rootElement.getChildren()) {
                final Element hrElement = (Element) o;
                final long timeStamp = hrElement.getAttribute(XML_ATTR_TIMESTAMP).getLongValue();
                final String transactionCode = hrElement.getAttribute(XML_ATTR_TRANSACTION).getValue();
                final Record.Event eventCode = Record.Event.forKey(transactionCode);
                final String message = hrElement.getText();
                this.addEvent(timeStamp, eventCode, message);
            }
        } catch (JDOMException e) {
            LOGGER.error("error parsing user event history record: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.error("error parsing user event history record: " + e.getMessage());
        }
    }

    public void addEvent(final long timestamp, final Record.Event eventCode, final String message)
    {
        final Record record = new Record(timestamp, eventCode, message);
        this.addEvent(record);
    }

// -------------------------- OTHER METHODS --------------------------

    public SortedSet<Record> getRecords()
    {
        return Collections.unmodifiableSortedSet(new TreeSet<Record>(records));
    }

// -------------------------- INNER CLASSES --------------------------

    /**
     * Sortable list of records.  Since we allow duplicate records with the same timestamp in a Set,
     * this Comparable implementation has a compareTo() that is NOT consistent with equals
     */
    public static class Record implements Comparable, Serializable {
        public enum Event {
            CHANGE_PASSWORD(Message.EVENT_LOG_CHANGE_PASSWORD),
            RECOVER_PASSWORD(Message.EVENT_LOG_RECOVER_PASSWORD),
            SET_RESPONSES(Message.EVENT_LOG_SETUP_RESPONSES),
            ACTIVATE_USER(Message.EVENT_LOG_ACTIVATE_USER),
            UPDATE_ATTRIBUTES(Message.EVENT_UPDATE_ATTRIBUTES),
            INTRUDER_LOCK(Message.EVENT_INTRUDER_LOCKOUT),

            UNKNOWN(Message.ERROR_UNKNOWN);

            final private Message message;

            Event(final Message message) {
                this.message = message;
            }

            public Message getMessage() {
                return message;
            }

            public static Event forKey(final String key) {
                for (final Event e : Event.values()) {
                    if (e.getMessage().getResourceKey().equals(key)) {
                        return e;
                    }
                }

                return UNKNOWN;
            }
        }

        private long timestamp;
        private Event eventCode;
        private String message;

        public Record(final Event eventCode, final String message)
        {
            this(System.currentTimeMillis(), eventCode, message);
        }

        public Record(final long timestamp, final Event eventCode, final String message)
        {
            this.timestamp = timestamp;
            this.eventCode = eventCode;
            this.message = message;
        }

        public long getTimestamp()
        {
            return timestamp;
        }

        public Event getEventCode()
        {
            return eventCode;
        }

        public String getMessage()
        {
            return message;
        }

        public int compareTo(final Object o)
        {
            final Record otherRecord = (Record) o;

            if (otherRecord.equals(this)) {
                return 0;
            }

            if (otherRecord.timestamp < this.timestamp) {
                return -1;
            }

            if (otherRecord.timestamp > this.timestamp) {
                return 1;
            }

            return 0;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean equals(final Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Record record = (Record) o;

            return timestamp == record.timestamp && !(eventCode != null ? !eventCode.equals(record.eventCode) : record.eventCode != null) && !(message != null ? !message.equals(record.message) : record.message != null);
        }

        public int hashCode()
        {
            int result;
            result = (int) (timestamp ^ (timestamp >>> 32));
            result = 29 * result + (eventCode != null ? eventCode.hashCode() : 0);
            result = 29 * result + (message != null ? message.hashCode() : 0);
            return result;
        }
    }
}

