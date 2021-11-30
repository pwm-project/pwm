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

package password.pwm.svc.event;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.AppConfig;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.json.JsonFactory;

public class JsonAuditFormatter implements AuditFormatter
{
    @Override
    public String convertAuditRecordToMessage(
            final PwmApplication pwmApplication,
            final AuditRecord auditRecord
    )
            throws PwmUnrecoverableException
    {
        final AppConfig appConfig = pwmApplication.getConfig();
        final int maxLength = Integer.parseInt( appConfig.readAppProperty( AppProperty.AUDIT_SYSLOG_MAX_MESSAGE_LENGTH ) );
        String jsonValue = "";
        final StringBuilder message = new StringBuilder();
        message.append( PwmConstants.PWM_APP_NAME );
        message.append( " " );

        jsonValue = JsonFactory.get().serialize( auditRecord );

        if ( message.length() + jsonValue.length() <= maxLength )
        {
            message.append( jsonValue );
        }
        else
        {
            final AuditRecordData inputRecord = ( ( AuditRecordData ) auditRecord ).toBuilder()
                    .message( auditRecord.getMessage() == null ? "" : auditRecord.getMessage() )
                    .narrative( auditRecord.getNarrative() == null ? "" : auditRecord.getNarrative() )
                    .build();

            final String truncateMessage = appConfig.readAppProperty( AppProperty.AUDIT_SYSLOG_TRUNCATE_MESSAGE );
            final AuditRecordData.AuditRecordDataBuilder copiedRecord = ( ( AuditRecordData ) auditRecord ).toBuilder();
            copiedRecord.message( "" );
            copiedRecord.narrative( "" );
            final int shortenedMessageLength = message.length()
                    + JsonFactory.get().serialize( copiedRecord.build() ).length()
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

            copiedRecord.message( inputRecord.getMessage().length() > maxMessageLength
                    ? inputRecord.getMessage().substring( 0, maxMessageLength ) + truncateMessage
                    : inputRecord.getMessage() );

            copiedRecord.narrative( inputRecord.getNarrative().length() > maxNarrativeLength
                    ? inputRecord.getNarrative().substring( 0, maxNarrativeLength ) + truncateMessage
                    : inputRecord.getNarrative() );

            message.append( JsonFactory.get().serialize( copiedRecord.build() ) );
        }

        return message.toString();
    }

}
