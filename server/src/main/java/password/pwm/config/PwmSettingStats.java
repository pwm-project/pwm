/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class PwmSettingStats
{
    public enum SettingStat
    {
        Total,
        hasProfile,
        syntaxCounts,
    }

    public static Map<SettingStat, Object> getStats( )
    {
        final Map<SettingStat, Object> returnObj = new LinkedHashMap<>();
        {
            returnObj.put( SettingStat.Total, PwmSetting.values().length );
        }
        {
            int hasProfile = 0;
            for ( final PwmSetting pwmSetting : PwmSetting.values() )
            {
                if ( pwmSetting.getCategory().hasProfiles() )
                {
                    hasProfile++;
                }
            }
            returnObj.put( SettingStat.hasProfile, hasProfile );
        }
        {
            final Map<PwmSettingSyntax, Integer> syntaxCounts = new LinkedHashMap<>();
            for ( final PwmSettingSyntax syntax : PwmSettingSyntax.values() )
            {
                syntaxCounts.put( syntax, 0 );
            }
            for ( final PwmSetting pwmSetting : PwmSetting.values() )
            {
                syntaxCounts.put( pwmSetting.getSyntax(), syntaxCounts.get( pwmSetting.getSyntax() ) + 1 );
            }
            returnObj.put( SettingStat.syntaxCounts, syntaxCounts );
        }
        return returnObj;
    }
}
