package password.pwm.util.queue;

import java.io.IOException;
import java.util.List;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mockito.Mockito;

import password.pwm.AppProperty;
import password.pwm.bean.EmailItemBean;
import password.pwm.config.Configuration;

public class EmailQueueManagerTest {
    @Test
    public void testConvertEmailItemToMessage() throws MessagingException, IOException {
        EmailQueueManager emailQueueManager = new EmailQueueManager();

        Configuration config = Mockito.mock(Configuration.class);
        Mockito.when(config.readAppProperty(AppProperty.SMTP_SUBJECT_ENCODING_CHARSET)).thenReturn("UTF8");

        EmailItemBean emailItemBean = new EmailItemBean("fred@flintstones.tv, barney@flintstones.tv", "bedrock-admin@flintstones.tv", "Test Subject", "bodyPlain", "bodyHtml");

        List<Message> messages = emailQueueManager.convertEmailItemToMessages(emailItemBean, config);
        Assert.assertEquals(2, messages.size());

        Message message = messages.get(0);
        Assert.assertEquals(new InternetAddress("fred@flintstones.tv"), message.getRecipients(Message.RecipientType.TO)[0]);
        Assert.assertEquals(new InternetAddress("bedrock-admin@flintstones.tv"), message.getFrom()[0]);
        Assert.assertEquals("Test Subject", message.getSubject());
        String content = IOUtils.toString(message.getInputStream());
        Assert.assertTrue(content.contains("bodyPlain"));
        Assert.assertTrue(content.contains("bodyHtml"));

        message = messages.get(1);
        Assert.assertEquals(new InternetAddress("barney@flintstones.tv"), message.getRecipients(Message.RecipientType.TO)[0]);
        Assert.assertEquals(new InternetAddress("bedrock-admin@flintstones.tv"), message.getFrom()[0]);
        Assert.assertEquals("Test Subject", message.getSubject());
        content = IOUtils.toString(message.getInputStream());
        Assert.assertTrue(content.contains("bodyPlain"));
        Assert.assertTrue(content.contains("bodyHtml"));
    }
}
