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

package password.pwm.svc.sms;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.svc.email.EmailServer;
import password.pwm.svc.email.EmailServerUtil;
import password.pwm.util.java.JavaHelper;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class EmailQueueManagerTest
{
    @Test
    public void testConvertEmailItemToMessage() throws MessagingException, IOException
    {
        final AppConfig config = Mockito.mock( AppConfig.class );
        Mockito.when( config.readAppProperty( AppProperty.SMTP_SUBJECT_ENCODING_CHARSET ) ).thenReturn( "UTF8" );

        final EmailItemBean emailItemBean = new EmailItemBean(
                "fred@flintstones.tv, barney@flintstones.tv",
                "bedrock-admin@flintstones.tv",
                "Test Subject",
                "bodyPlain",
                "bodyHtml" );

        final EmailServer emailServer = EmailServer.builder()
                .javaMailProps( new Properties() )
                .build();

        final List<Message> messages = EmailServerUtil.convertEmailItemToMessages( emailItemBean, config, emailServer, SessionLabel.TEST_SESSION_LABEL );
        Assert.assertEquals( 2, messages.size() );

        {
            final Message message = messages.get( 0 );
            Assert.assertEquals( new InternetAddress( "fred@flintstones.tv" ), message.getRecipients( Message.RecipientType.TO )[0] );
            Assert.assertEquals( new InternetAddress( "bedrock-admin@flintstones.tv" ), message.getFrom()[0] );
            Assert.assertEquals( "Test Subject", message.getSubject() );
            final String content = JavaHelper.copyToString( message.getInputStream(), PwmConstants.DEFAULT_CHARSET, Integer.MAX_VALUE )
                    .orElse( "" );
            Assert.assertTrue( content.contains( "bodyPlain" ) );
            Assert.assertTrue( content.contains( "bodyHtml" ) );
        }

        {
            final Message message = messages.get( 1 );
            Assert.assertEquals( new InternetAddress( "barney@flintstones.tv" ), message.getRecipients( Message.RecipientType.TO )[0] );
            Assert.assertEquals( new InternetAddress( "bedrock-admin@flintstones.tv" ), message.getFrom()[0] );
            Assert.assertEquals( "Test Subject", message.getSubject() );
            final String content = JavaHelper.copyToString( message.getInputStream(), PwmConstants.DEFAULT_CHARSET, Integer.MAX_VALUE )
                    .orElse( "" );
            Assert.assertTrue( content.contains( "bodyPlain" ) );
            Assert.assertTrue( content.contains( "bodyHtml" ) );
        }
    }
}
