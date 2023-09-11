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

import org.jrivard.xmlchai.AccessMode;
import org.jrivard.xmlchai.XmlDocument;
import org.jrivard.xmlchai.XmlElement;
import org.jrivard.xmlchai.XmlFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.svc.userhistory.LdapXmlUserHistory;
import password.pwm.util.SampleDataGenerator;
import password.pwm.util.localdb.TestHelper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class LdapXmlUserHistoryTest
{
    @TempDir
    public Path temporaryFolder;

    @Test
    public void inputParserTest()
            throws Exception
    {
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( temporaryFolder );
        final PwmDomain pwmDomain = pwmApplication.domains().get( DomainID.DOMAIN_ID_DEFAULT );
        final ResourceBundle bundle = ResourceBundle.getBundle( LdapXmlUserHistoryTest.class.getName() );
        final String xmlValue1 =  bundle.getString( "xmlValue1" );
        final LdapXmlUserHistory.StoredHistory storedHistory = LdapXmlUserHistory.StoredHistory
                .fromXml( xmlValue1 );

        final List<UserAuditRecord> auditEventList = storedHistory.asAuditRecords(
                AuditRecordFactory.make( SessionLabel.TEST_SESSION_LABEL,  pwmDomain ), SampleDataGenerator.sampleUserData() );
        //System.out.println( JsonUtil.serializeCollection( auditEventList, JsonUtil.Flag.PrettyPrint ) );

        Assertions.assertEquals( 20, auditEventList.size() );
        Assertions.assertEquals( 9, auditEventList.stream()
                .filter( ( record ) -> record.eventCode() == AuditEvent.CHANGE_PASSWORD ).count() );

        {
            final UserAuditRecord record0 = auditEventList.get( 0 );
            Assertions.assertEquals( "ort", record0.sourceHost() );
            Assertions.assertEquals( "172.17.2.1", record0.sourceAddress() );
            Assertions.assertEquals( Instant.parse( "2019-07-28T01:14:39.054Z" ), record0.timestamp() );
            Assertions.assertEquals( AuditEvent.CHANGE_PASSWORD, record0.eventCode() );
        }

        {
            final UserAuditRecord record7 = auditEventList.get( 7 );
            Assertions.assertEquals( "0:0:0:0:0:0:0:1", record7.sourceHost() );
            Assertions.assertEquals( "0:0:0:0:0:0:0:1", record7.sourceAddress() );
            Assertions.assertEquals( Instant.parse( "2020-07-12T02:29:22.347Z" ), record7.timestamp() );
            Assertions.assertEquals( AuditEvent.AUTHENTICATE, record7.eventCode() );
        }


    }

    @Test
    public void outputTest() throws Exception
    {
        final LdapXmlUserHistory.StoredHistory storedHistory = new LdapXmlUserHistory.StoredHistory();
        storedHistory.addEvent( LdapXmlUserHistory.StoredEvent.fromAuditRecord( AuditRecordData.builder()
                .timestamp( Instant.parse( "2020-02-27T17:26:30Z" ) )
                .eventCode( AuditEvent.CHANGE_PASSWORD )

                .build() ) );

        final String xmlValue = storedHistory.toXml();
        final XmlFactory xmlFactory = XmlFactory.getFactory();

        final XmlDocument xmlDocument = xmlFactory.parseString( xmlValue, AccessMode.IMMUTABLE );
        final Optional<XmlElement> optionalRecordElement = xmlDocument.evaluateXpathToElement( "/history/record" );
        Assertions.assertTrue( optionalRecordElement.isPresent() );
        optionalRecordElement.ifPresent( xmlElement ->
        {
            Assertions.assertEquals( "EventLog_ChangePassword", xmlElement.getAttribute( "eventCode" ).orElseThrow() );
            Assertions.assertEquals( "1582824390000", xmlElement.getAttribute( "timestamp" ).orElseThrow() );
        } );
    }
}
