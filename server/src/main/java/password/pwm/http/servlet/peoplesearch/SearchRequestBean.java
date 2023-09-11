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

package password.pwm.http.servlet.peoplesearch;

import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.CollectorUtil;

import java.util.List;
import java.util.Map;

public record SearchRequestBean(
        SearchMode mode,

        String username,
        List<SearchValue> searchValues,
        boolean includeDisplayName
)
{
    public SearchRequestBean(
            final SearchMode mode,
            final String username,
            final List<SearchValue> searchValues,
            final boolean includeDisplayName
    )
    {
        this.mode = mode == null ? SearchMode.simple : mode;
        this.username = username == null ? "" : username;
        this.searchValues = CollectionUtil.stripNulls( searchValues );
        this.includeDisplayName = includeDisplayName;
    }

    public enum SearchMode
    {
        simple,
        advanced,
    }

    public record SearchValue(
            String key,
            String value
    )
    {
    }

    public static Map<String, String> searchValueToMap( final List<SearchValue> input )
    {
        return input.stream()
                .collect( CollectorUtil.toUnmodifiableLinkedMap(
                        SearchValue::key,
                        SearchValue::value
                ) );
    }
}
