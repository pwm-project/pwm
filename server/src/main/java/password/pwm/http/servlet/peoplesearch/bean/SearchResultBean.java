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

package password.pwm.http.servlet.peoplesearch.bean;

import password.pwm.util.java.CollectionUtil;

import java.util.List;
import java.util.Map;

public record SearchResultBean(
        List<Map<String, Object>> searchResults,
        boolean sizeExceeded,
        String aboutResultMessage,
        boolean fromCache
)
{
    private static final SearchResultBean EMPTY = new SearchResultBean( null, false, null, false );

    public SearchResultBean(
            final List<Map<String, Object>> searchResults,
            final boolean sizeExceeded,
            final String aboutResultMessage,
            final boolean fromCache
    )
    {
        this.searchResults = searchResults == null
                ? List.of()
                : List.copyOf( searchResults.stream()
                        .map( CollectionUtil::stripNulls )
                        .filter( m -> !m.isEmpty() )
                        .toList() );

        this.sizeExceeded = sizeExceeded;
        this.aboutResultMessage = aboutResultMessage;
        this.fromCache = fromCache;
    }

    public static SearchResultBean empty()
    {
        return EMPTY;
    }
}
