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

package password.pwm.http.servlet.peoplesearch;

import lombok.Builder;
import lombok.Value;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.util.ArrayList;
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

    public static Map<String, String> searchValueToMap( final List<SearchValue> input )
    {
        final Map<String, String> returnMap = new LinkedHashMap<>();
        for ( final SearchValue searchValue : input )
        {
            returnMap.put( searchValue.getKey(), searchValue.getValue() );
        }
        return Collections.unmodifiableMap( returnMap );
    }

    public List<SearchValue> nonEmptySearchValues()
    {
        return filterNonEmptySearchValues( getSearchValues() );
    }

    public static List<SearchValue> filterNonEmptySearchValues( final List<SearchValue> input )
    {
        final List<SearchValue> returnList = input == null
                ? new ArrayList<>()
                : new ArrayList<>( input );

        returnList.removeIf( searchValue -> StringUtil.isEmpty( searchValue.getKey() )
                || StringUtil.isEmpty( searchValue.getValue() ) );

        return Collections.unmodifiableList( returnList );
    }
}
