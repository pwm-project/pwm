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

package password.pwm.config;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
        final Map<SettingStat, Object> returnObj = new LinkedHashMap<>( SettingStat.values().length );

        returnObj.put( SettingStat.Total, PwmSetting.values().length );

        returnObj.put( SettingStat.hasProfile, EnumSet.allOf( PwmSetting.class ).stream()
                .filter( pwmSetting -> pwmSetting.getCategory().hasProfiles() )
                .count() );

        final Map<PwmSettingSyntax, Integer> syntaxCounts = Arrays.stream( PwmSettingSyntax.values() )
                .collect( Collectors.toMap( syntax -> syntax, syntax -> 0 ) );

        Arrays.stream( PwmSetting.values() ).forEach(
                pwmSetting -> syntaxCounts.compute( pwmSetting.getSyntax(), ( pwmSettingSyntax, integer ) -> integer == null ? 0 : integer + 1 ) );

        returnObj.put( SettingStat.syntaxCounts, syntaxCounts );
        return Map.copyOf( returnObj );
    }
}
