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

import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.health.HealthRecord;
import password.pwm.util.json.JsonProvider;
import password.pwm.util.json.JsonFactory;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class HealthDebugItemGenerator implements AppItemGenerator
{
    @Value
    private static class HealthDebugInfo
    {
        private final HealthRecord healthRecord;
        private final String message;
    }

    @Override
    public String getFilename()
    {
        return "health.json";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
    {
        final Locale locale = PwmConstants.DEFAULT_LOCALE;
        final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
        final Set<HealthRecord> records = pwmApplication.getHealthMonitor().getHealthRecords();

        final List<HealthDebugInfo> outputInfos = new ArrayList<>();
        records.forEach( healthRecord -> outputInfos.add( new HealthDebugInfo( healthRecord, healthRecord.getDetail( locale,
                debugItemInput.getObfuscatedAppConfig() ) ) ) );
        final String recordJson = JsonFactory.get().serializeCollection( outputInfos, JsonProvider.Flag.PrettyPrint );
        outputStream.write( recordJson.getBytes( PwmConstants.DEFAULT_CHARSET ) );
    }
}
