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

package password.pwm.bean;

import com.novell.ldapchai.cr.Answer;
import com.novell.ldapchai.cr.Challenge;
import password.pwm.config.option.DataStorageMethod;

import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

public class ResponseInfoBean implements Serializable
{
    private final Map<Challenge, String> crMap;
    private final Map<Challenge, String> helpdeskCrMap;
    private final Locale locale;
    private final int minRandoms;
    private final String csIdentifier;
    private final DataStorageMethod dataStorageMethod;
    private final Answer.FormatType formatType;

    private Instant timestamp;

    public ResponseInfoBean(
            final Map<Challenge, String> crMap,
            final Map<Challenge, String> helpdeskCrMap,
            final Locale locale,
            final int minRandoms,
            final String csIdentifier,
            final DataStorageMethod dataSource,
            final Answer.FormatType formatType
    )
    {
        this.crMap = crMap;
        this.helpdeskCrMap = helpdeskCrMap;
        this.locale = locale;
        this.minRandoms = minRandoms;
        this.csIdentifier = csIdentifier;
        this.dataStorageMethod = dataSource;
        this.formatType = formatType;
    }

    public Map<Challenge, String> getCrMap( )
    {
        return crMap;
    }

    public Locale getLocale( )
    {
        return locale;
    }

    public int getMinRandoms( )
    {
        return minRandoms;
    }

    public String getCsIdentifier( )
    {
        return csIdentifier;
    }

    public Map<Challenge, String> getHelpdeskCrMap( )
    {
        return helpdeskCrMap;
    }

    public Instant getTimestamp( )
    {
        return timestamp;
    }

    public void setTimestamp( final Instant timestamp )
    {
        this.timestamp = timestamp;
    }

    public DataStorageMethod getDataStorageMethod( )
    {
        return dataStorageMethod;
    }

    public Answer.FormatType getFormatType( )
    {
        return formatType;
    }
}
