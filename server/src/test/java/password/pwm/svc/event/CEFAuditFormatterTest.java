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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.TestHelper;

import java.nio.file.Path;
import java.util.Locale;

public class CEFAuditFormatterTest
{
    @TempDir
    public Path temporaryFolder;

    @BeforeAll
    public static void setUp() throws Exception
    {
        Locale.setDefault( PwmConstants.DEFAULT_LOCALE );
    }

    @Test
    public void testCEFFormatting()
            throws Exception
    {
        final String jsonInput = "{\"perpetratorID\":\"per|son\",\"perpetratorDN\":\"cn=per|son,o=org\","
                + "\"perpetratorLdapProfile\":\"default\",\"sourceAddress\":\"2001:DB8:D:B8:35cc::/64\",\"sourceHost\":\"ws31222\","
                + "\"type\":\"USER\",\"eventCode\":\"ACTIVATE_USER\",\"guid\":\"16ee0bf8-b0c9-41d7-8c24-b40110fc727e\","
                + "\"timestamp\":\"2000-01-01T00:00:00Z\",\"message\":\"message pipe|Escape, slash\\\\Escape, equal=Escape, \\nsecondLine\","
                + "\"xdasTaxonomy\":\"XDAS_AE_CREATE_SESSION\",\"xdasOutcome\":\"XDAS_OUT_SUCCESS\"}";

        final UserAuditRecord auditRecord = JsonFactory.get().deserialize( jsonInput, AuditRecordData.class );

        final String appName = PwmConstants.PWM_APP_NAME;
        final String versionData = PwmConstants.SERVLET_VERSION;
        final String expectedOutput = "CEF:0|" + appName + "|" + appName + "|" + versionData
                + "|ACTIVATE_USER|Activate Account|Medium| type=USER eventCode=ACTIVATE_USER timestamp="
                + "2000-01-01T00:00:00Z"
                + " message=message pipe\\|Escape, slash\\\\Escape, equal\\=Escape, \\nsecondLine"
                + " perpetratorID=per\\|son perpetratorDN=cn\\=per\\|son,o\\=org sourceAddress=2001:DB8:D:B8:35cc::/64 sourceHost=ws31222";

        final CEFAuditFormatter cefAuditFormatter = new CEFAuditFormatter();
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( temporaryFolder.toFile() );
        final String output = cefAuditFormatter.convertAuditRecordToMessage( pwmApplication, auditRecord );
        Assertions.assertEquals( expectedOutput, output );
    }
}
