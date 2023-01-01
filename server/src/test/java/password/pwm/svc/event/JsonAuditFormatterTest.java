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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.TestHelper;

import java.nio.file.Path;

public class JsonAuditFormatterTest
{
    @TempDir
    public Path temporaryFolder;

    @Test
    public void testCEFFormatting() throws Exception
    {
        final String jsonInput = "{"
                + "\"type\":\"USER\","
                + "\"eventCode\":\"ACTIVATE_USER\","
                + "\"guid\":\"16ee0bf8-b0c9-41d7-8c24-b40110fc727e\","
                + "\"timestamp\":\"2000-01-01T00:00:00Z\","
                + "\"message\":\"message pipe|Escape, slash\\\\Escape, equal=Escape, \\nsecondLine\","
                + "\"xdasTaxonomy\":\"XDAS_AE_CREATE_SESSION\","
                + "\"xdasOutcome\":\"XDAS_OUT_SUCCESS\","
                + "\"perpetratorID\":\"per|son\","
                + "\"perpetratorDN\":\"cn=per|son,o=org\","
                + "\"perpetratorLdapProfile\":\"default\","
                + "\"sourceAddress\":\"2001:DB8:D:B8:35cc::/64\","
                + "\"sourceHost\":\"ws31222\""
                + "}";

        final UserAuditRecord auditRecord = JsonFactory.get().deserialize( jsonInput, AuditRecordData.class );
        final String expectedOutput = PwmConstants.PWM_APP_NAME + " " + JsonFactory.get().serialize( auditRecord );
        final AuditFormatter auditFormatter = new JsonAuditFormatter();

        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( temporaryFolder );

        final String output = auditFormatter.convertAuditRecordToMessage( pwmApplication, auditRecord );
        Assertions.assertEquals( expectedOutput, output );
    }

}
