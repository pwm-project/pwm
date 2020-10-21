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

package password.pwm.svc.stats;

import lombok.Value;

import java.util.HashSet;
import java.util.Set;

@Value
class EpsKey
{
    static final String DB_KEY_PREFIX = "EPS-";
    private EpsStatistic epsStatistic;
    private Statistic.EpsDuration epsDuration;

    static Set<EpsKey> allKeys()
    {
        final Set<EpsKey> returnSet = new HashSet<>();
        for ( final EpsStatistic epsStatistic : EpsStatistic.values() )
        {
            for ( final Statistic.EpsDuration epsDuration : Statistic.EpsDuration.values() )
            {
                returnSet.add( new EpsKey( epsStatistic, epsDuration ) );
            }
        }
        return returnSet;
    }
}
