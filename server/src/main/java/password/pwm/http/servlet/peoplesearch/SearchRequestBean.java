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

package password.pwm.http.servlet.peoplesearch;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class SearchRequestBean implements Serializable
{
    @Builder.Default
    private SearchMode mode = SearchMode.simple;

    private String username;
    private List<SearchValue> searchValues;
    private boolean includeDisplayName;

    public enum SearchMode
    {
        simple,
        advanced,
    }

    @Value
    public static class SearchValue implements Serializable
    {
        private String key;
        private String value;
    }

    static Map<String, String> searchValueToMap( final List<SearchValue> input )
    {
        final Map<String, String> returnMap = new LinkedHashMap<>();
        for ( final SearchValue searchValue : input )
        {
            returnMap.put( searchValue.getKey(), searchValue.getValue() );
        }
        return Collections.unmodifiableMap( returnMap );
    }
}
