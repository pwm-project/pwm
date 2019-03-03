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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;

public class JsonAuditFormatterTest
{
    @Test
    public void testCEFFormatting() throws PwmUnrecoverableException
    {
        final String jsonInput = "{\"perpetratorID\":\"per|son\",\"perpetratorDN\":\"cn=per|son,o=org\","
                + "\"perpetratorLdapProfile\":\"default\",\"sourceAddress\":\"2001:DB8:D:B8:35cc::/64\",\"sourceHost\":\"ws31222\","
                + "\"type\":\"USER\",\"eventCode\":\"ACTIVATE_USER\",\"guid\":\"16ee0bf8-b0c9-41d7-8c24-b40110fc727e\","
                + "\"timestamp\":\"2000-01-01T00:00:00Z\",\"message\":\"message pipe|Escape, slash\\\\Escape, equal=Escape, \\nsecondLine\","
                + "\"xdasTaxonomy\":\"XDAS_AE_CREATE_SESSION\",\"xdasOutcome\":\"XDAS_OUT_SUCCESS\"}";

        final UserAuditRecord auditRecord = JsonUtil.deserialize( jsonInput, UserAuditRecord.class );
        final String expectedOutput = PwmConstants.PWM_APP_NAME + " " + jsonInput;
        final AuditFormatter auditFormatter = new JsonAuditFormatter();
        final PwmApplication pwmApplication = Mockito.mock( PwmApplication.class );
        Mockito.when( pwmApplication.getConfig() ).thenReturn( new Configuration( StoredConfigurationImpl.newStoredConfiguration() ) );
        final String output = auditFormatter.convertAuditRecordToMessage( pwmApplication, auditRecord );
        Assert.assertEquals( expectedOutput, output );
    }

}
