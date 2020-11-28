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

package password.pwm.health;

import password.pwm.i18n.Health;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

public enum HealthStatus
{
    WARN,
    CAUTION,
    CONFIG,
    GOOD,
    INFO,
    DEBUG,;

    public String getKey( )
    {
        return HealthStatus.class.getSimpleName() + "_" + this.toString();
    }

    public String getDescription( final Locale locale, final password.pwm.config.Configuration config )
    {
        return LocaleHelper.getLocalizedMessage( locale, this.getKey(), config, Health.class );
    }

    public static Optional<HealthStatus> mostSevere( final Collection<HealthStatus> healthStatuses )
    {
        // enumset will sort in natural order, with most severe first.
        final EnumSet<HealthStatus> sortedSet = JavaHelper.copiedEnumSet( healthStatuses, HealthStatus.class );
        if ( sortedSet.isEmpty() )
        {
            return Optional.empty();
        }
        return Optional.of( sortedSet.iterator().next() );
    }
}
