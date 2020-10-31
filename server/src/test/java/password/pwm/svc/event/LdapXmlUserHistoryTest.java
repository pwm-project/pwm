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

import org.junit.Assert;
import org.junit.Test;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.SampleDataGenerator;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class LdapXmlUserHistoryTest
{
    @Test
    public void inputParserTest()
    {
        final ResourceBundle bundle = ResourceBundle.getBundle( LdapXmlUserHistoryTest.class.getName() );
        final String xmlValue1 =  bundle.getString( "xmlValue1" );
        final LdapXmlUserHistory.StoredHistory storedHistory = LdapXmlUserHistory.StoredHistory.fromXml( xmlValue1 );

        final List<UserAuditRecord> auditEventList = storedHistory.asAuditRecords( SampleDataGenerator.sampleUserData() );
        //System.out.println( JsonUtil.serializeCollection( auditEventList, JsonUtil.Flag.PrettyPrint ) );

        Assert.assertEquals( 20, auditEventList.size() );
        Assert.assertEquals( 9, auditEventList.stream()
                .filter( ( record ) -> record.getEventCode() == AuditEvent.CHANGE_PASSWORD ).count() );

        {
            final UserAuditRecord record0 = auditEventList.get( 0 );
            Assert.assertEquals( "ort", record0.getSourceHost() );
            Assert.assertEquals( "172.17.2.1", record0.getSourceAddress() );
            Assert.assertEquals( Instant.parse( "2019-07-28T01:14:39.054Z" ), record0.getTimestamp() );
            Assert.assertEquals( AuditEvent.CHANGE_PASSWORD, record0.getEventCode() );
        }

        {
            final UserAuditRecord record7 = auditEventList.get( 7 );
            Assert.assertEquals( "0:0:0:0:0:0:0:1", record7.getSourceHost() );
            Assert.assertEquals( "0:0:0:0:0:0:0:1", record7.getSourceAddress() );
            Assert.assertEquals( Instant.parse( "2020-07-12T02:29:22.347Z" ), record7.getTimestamp() );
            Assert.assertEquals( AuditEvent.AUTHENTICATE, record7.getEventCode() );
        }


    }

    @Test
    public void outputTest() throws PwmUnrecoverableException
    {
        final LdapXmlUserHistory.StoredHistory storedHistory = new LdapXmlUserHistory.StoredHistory();
        storedHistory.addEvent( LdapXmlUserHistory.StoredEvent.fromAuditRecord( UserAuditRecord.builder()
                .timestamp( Instant.parse( "2020-02-27T17:26:30Z" ) )
                .eventCode( AuditEvent.CHANGE_PASSWORD )

                .build() ) );

        final String xmlValue = storedHistory.toXml();
        final XmlFactory xmlFactory = XmlFactory.getFactory();

        final XmlDocument xmlDocument = xmlFactory.parseXml( StringUtil.stringToInputStream( xmlValue ) );
        final Optional<XmlElement> optionalRecordElement = xmlDocument.evaluateXpathToElement( "/history/record" );
        Assert.assertTrue( optionalRecordElement.isPresent() );
        optionalRecordElement.ifPresent( xmlElement ->
        {
            Assert.assertEquals( "EventLog_ChangePassword", xmlElement.getAttributeValue( "eventCode" ) );
            Assert.assertEquals( "1582824390000", xmlElement.getAttributeValue( "timestamp" ) );
        } );
    }
}
