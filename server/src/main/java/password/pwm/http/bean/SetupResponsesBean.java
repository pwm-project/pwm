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

package password.pwm.http.bean;

import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import lombok.Data;
import lombok.EqualsAndHashCode;
import password.pwm.config.option.SessionBeanMode;

import java.io.Serializable;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode( callSuper = false )
public class SetupResponsesBean extends PwmSessionBean
{
    private boolean hasExistingResponses;
    private SetupData responseData;
    private SetupData helpdeskResponseData;
    private boolean responsesSatisfied;
    private boolean helpdeskResponsesSatisfied;
    private boolean confirmed;
    private Locale userLocale;

    public Type getType( )
    {
        return Type.AUTHENTICATED;
    }

    @Data
    @EqualsAndHashCode( callSuper = false )
    public static class SetupData implements Serializable
    {
        private ChallengeSet challengeSet;
        private Map<String, Challenge> indexedChallenges = Collections.emptyMap();
        private boolean simpleMode;
        private int minRandomSetup;
        private Map<Challenge, String> responseMap = Collections.emptyMap();
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.singleton( SessionBeanMode.LOCAL );
    }
}
