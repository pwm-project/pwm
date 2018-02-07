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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.util.secure.PwmRandom;

import java.lang.reflect.Method;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class SyslogAuditServiceTest {
    @Rule
    public WireMockRule wm = new WireMockRule(wireMockConfig()
            .dynamicPort());

    @Test
    public void test_convertAuditRecordToSyslogMessage() throws Exception {
        {
            final int maxLength = 1024;
            final AuditRecord record = new AuditRecordFactory(Mockito.mock(PwmApplication.class)).createUserAuditRecord(
                    AuditEvent.AUTHENTICATE,
                    new UserIdentity("cn=user,o=org","default"),
                    PwmRandom.getInstance().alphaNumericString(maxLength),
                    "127.0.0.1",
                    "localhost"
            );
            String msg = invokeConvertAuditRecordToSyslogMessage(record, maxLength);
            Assert.assertTrue(msg.length() <= maxLength);
            Assert.assertTrue(msg.length() > maxLength - 100);
        }

        { // msg copied to narrative, so more work for method to do.
            final int maxLength = 1024;
            final AuditRecord record = new AuditRecordFactory(Mockito.mock(PwmApplication.class)).createSystemAuditRecord(
                    AuditEvent.MODIFY_CONFIGURATION,
                    PwmRandom.getInstance().alphaNumericString(maxLength)
            );
            String msg = invokeConvertAuditRecordToSyslogMessage(record, maxLength);
            Assert.assertTrue(msg.length() <= maxLength);
            Assert.assertTrue(msg.length() > maxLength - 100);
        }

        {
            final int maxLength = 2048;
            final AuditRecord record = new AuditRecordFactory(Mockito.mock(PwmApplication.class)).createSystemAuditRecord(
                    AuditEvent.MODIFY_CONFIGURATION,
                    PwmRandom.getInstance().alphaNumericString(maxLength)
            );
            String msg = invokeConvertAuditRecordToSyslogMessage(record, maxLength);
            Assert.assertTrue(msg.length() <= maxLength);
            Assert.assertTrue(msg.length() > maxLength - 100);
        }
    }

    private String invokeConvertAuditRecordToSyslogMessage(final AuditRecord record, final int maxMsgLength)
            throws Exception
    {
        final Method method = SyslogAuditService.class.getDeclaredMethod(
                "convertAuditRecordToSyslogMessage",
                AuditRecord.class,
                Configuration.class
        );
        method.setAccessible(true);
        final Configuration configuration = spy(new Configuration(StoredConfigurationImpl.newStoredConfiguration()));
        when(configuration.readAppProperty(AppProperty.AUDIT_SYSLOG_MAX_MESSAGE_LENGTH)).thenReturn(Integer.toString(maxMsgLength));

        return (String)method.invoke(null, record, configuration);
    }
}
