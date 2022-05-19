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

package password.pwm.util.debug;

import org.apache.commons.csv.CSVPrinter;
import password.pwm.config.DomainConfig;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.LDAPPermissionCalculator;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.StringUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

class LDAPPermissionItemGenerator implements DomainItemGenerator
{
    @Override
    public String getFilename()
    {
        return "ldapPermissionSuggestions.csv";
    }

    @Override
    public void outputItem( final DomainDebugItemInput debugItemInput, final OutputStream outputStream )
            throws IOException, PwmUnrecoverableException
    {

        final DomainConfig domainConfig = debugItemInput.getObfuscatedDomainConfig();
        final LDAPPermissionCalculator ldapPermissionCalculator = new LDAPPermissionCalculator( domainConfig );

        final CSVPrinter csvPrinter = MiscUtil.makeCsvPrinter( outputStream );
        {
            final List<String> headerRow = new ArrayList<>();
            headerRow.add( "Attribute" );
            headerRow.add( "Actor" );
            headerRow.add( "Access" );
            headerRow.add( "Setting" );
            headerRow.add( "Profile" );
            csvPrinter.printComment( StringUtil.join( headerRow, "," ) );
        }

        for ( final LDAPPermissionCalculator.PermissionRecord record : ldapPermissionCalculator.getPermissionRecords() )
        {
            final List<String> dataRow = new ArrayList<>();
            dataRow.add( record.getAttribute() );
            dataRow.add( record.getActor() == null ? "" : record.getActor().toString() );
            dataRow.add( record.getAccess() == null ? "" : record.getAccess().toString() );
            dataRow.add( record.getPwmSetting() == null ? "" : record.getPwmSetting().getKey() );
            dataRow.add( record.getProfile() == null ? "" : record.getProfile() );
            csvPrinter.printRecord( dataRow );
        }
        csvPrinter.flush();
    }
}
