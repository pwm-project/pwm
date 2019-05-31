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

package password.pwm.util.queue;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import password.pwm.AppProperty;
import password.pwm.bean.EmailItemBean;
import password.pwm.config.Configuration;
import password.pwm.svc.email.EmailServer;
import password.pwm.svc.email.EmailServerUtil;
import password.pwm.svc.email.EmailService;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class EmailQueueManagerTest
{
    @Test
    public void testConvertEmailItemToMessage() throws MessagingException, IOException
    {
        final EmailService emailService = new EmailService();

        final Configuration config = Mockito.mock( Configuration.class );
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

        final List<Message> messages = EmailServerUtil.convertEmailItemToMessages( emailItemBean, config, emailServer );
        Assert.assertEquals( 2, messages.size() );

        Message message = messages.get( 0 );
        Assert.assertEquals( new InternetAddress( "fred@flintstones.tv" ), message.getRecipients( Message.RecipientType.TO )[0] );
        Assert.assertEquals( new InternetAddress( "bedrock-admin@flintstones.tv" ), message.getFrom()[0] );
        Assert.assertEquals( "Test Subject", message.getSubject() );
        String content = IOUtils.toString( message.getInputStream() );
        Assert.assertTrue( content.contains( "bodyPlain" ) );
        Assert.assertTrue( content.contains( "bodyHtml" ) );

        message = messages.get( 1 );
        Assert.assertEquals( new InternetAddress( "barney@flintstones.tv" ), message.getRecipients( Message.RecipientType.TO )[0] );
        Assert.assertEquals( new InternetAddress( "bedrock-admin@flintstones.tv" ), message.getFrom()[0] );
        Assert.assertEquals( "Test Subject", message.getSubject() );
        content = IOUtils.toString( message.getInputStream() );
        Assert.assertTrue( content.contains( "bodyPlain" ) );
        Assert.assertTrue( content.contains( "bodyHtml" ) );
    }
}
