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

package password.pwm.http.servlet.helpdesk.data;

import password.pwm.http.servlet.peoplesearch.SearchRequestBean;
import password.pwm.util.java.CollectionUtil;

import java.util.List;

public record HelpdeskSearchRequest(
        SearchRequestBean.SearchMode mode,
        String username,
        List<SearchRequestBean.SearchValue> searchValues

)
{
    public HelpdeskSearchRequest(
            final SearchRequestBean.SearchMode mode,
            final String username,
            final List<SearchRequestBean.SearchValue> searchValues
    )
    {
        this.mode = mode == null ? SearchRequestBean.SearchMode.simple : mode;
        this.username = username;
        this.searchValues = CollectionUtil.stripNulls( searchValues );
    }
}
