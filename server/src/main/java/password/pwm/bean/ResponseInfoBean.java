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
