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

package password.pwm.svc.event;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;

public class JsonAuditFormatter implements AuditFormatter
{
    @Override
    public String convertAuditRecordToMessage(
            final PwmApplication pwmApplication,
            final AuditRecord auditRecord
    )
            throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmApplication.getConfig();
        final int maxLength = Integer.parseInt( configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_MAX_MESSAGE_LENGTH ) );
        String jsonValue = "";
        final StringBuilder message = new StringBuilder();
        message.append( PwmConstants.PWM_APP_NAME );
        message.append( " " );

        jsonValue = JsonUtil.serialize( auditRecord );

        if ( message.length() + jsonValue.length() <= maxLength )
        {
            message.append( jsonValue );
        }
        else
        {
            final AuditRecord inputRecord = JsonUtil.cloneUsingJson( auditRecord, auditRecord.getClass() );
            inputRecord.message = inputRecord.message == null ? "" : inputRecord.message;
            inputRecord.narrative = inputRecord.narrative == null ? "" : inputRecord.narrative;

            final String truncateMessage = configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_TRUNCATE_MESSAGE );
            final AuditRecord copiedRecord = JsonUtil.cloneUsingJson( auditRecord, auditRecord.getClass() );
            copiedRecord.message = "";
            copiedRecord.narrative = "";
            final int shortenedMessageLength = message.length()
                    + JsonUtil.serialize( copiedRecord ).length()
                    + truncateMessage.length();
            final int maxMessageAndNarrativeLength = maxLength - ( shortenedMessageLength + ( truncateMessage.length() * 2 ) );
            int maxMessageLength = inputRecord.getMessage().length();
            int maxNarrativeLength = inputRecord.getNarrative().length();

            {
                int top = maxMessageAndNarrativeLength;
                while ( maxMessageLength + maxNarrativeLength > maxMessageAndNarrativeLength )
                {
                    top--;
                    maxMessageLength = Math.min( maxMessageLength, top );
                    maxNarrativeLength = Math.min( maxNarrativeLength, top );
                }
            }

            copiedRecord.message = inputRecord.getMessage().length() > maxMessageLength
                    ? inputRecord.message.substring( 0, maxMessageLength ) + truncateMessage
                    : inputRecord.message;

            copiedRecord.narrative = inputRecord.getNarrative().length() > maxNarrativeLength
                    ? inputRecord.narrative.substring( 0, maxNarrativeLength ) + truncateMessage
                    : inputRecord.narrative;

            message.append( JsonUtil.serialize( copiedRecord ) );
        }

        return message.toString();
    }

}
