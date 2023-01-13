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

package password.pwm.ldap.search;

import password.pwm.bean.UserIdentity;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.CollectorUtil;
import password.pwm.util.java.StringUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UserSearchResults
{
    public static final String JSON_KEY_USER_KEY = "userKey";
    public static final String JSON_KEY_ID = "id";

    private final Map<String, String> headerAttributeMap;
    private final Map<UserIdentity, Map<String, String>> results;
    private final boolean sizeExceeded;

    public UserSearchResults(
            final Map<String, String> headerAttributeMap,
            final Map<UserIdentity, Map<String, String>> results,
            final boolean sizeExceeded
    )
    {
        this.headerAttributeMap = headerAttributeMap == null ? Collections.emptyMap() : Map.copyOf( headerAttributeMap );
        this.results = Map.copyOf( defaultSort( results, headerAttributeMap ) );
        this.sizeExceeded = sizeExceeded;
    }

    private static Map<UserIdentity, Map<String, String>> defaultSort(
            final Map<UserIdentity, Map<String, String>> results,
            final Map<String, String> headerAttributeMap
    )
    {
        if ( CollectionUtil.isEmpty( headerAttributeMap ) || CollectionUtil.isEmpty( results ) )
        {
            return results;
        }

        final String sortAttribute = headerAttributeMap.keySet().iterator().next();

        final UserIdentitySearchResultComparator comparator
                = new UserIdentitySearchResultComparator( results, sortAttribute );

        return Collections.unmodifiableMap( results.keySet().stream()
                .sorted( comparator )
                .collect( CollectorUtil.toLinkedMap(
                        Function.identity(),
                        userIdentity -> CollectionUtil.stripNulls( results.get( userIdentity ) )
                ) ) );
    }

    public Map<String, String> getHeaderAttributeMap( )
    {
        return headerAttributeMap;
    }

    public Map<UserIdentity, Map<String, String>> getResults( )
    {
        return results;
    }

    public boolean isSizeExceeded( )
    {
        return sizeExceeded;
    }

    public List<Map<String, Object>> resultsAsJsonOutput(
            final Function<UserIdentity, String> identityEncoder,
            final UserIdentity ignoreUser
    )
    {
        final AtomicInteger idCounter = new AtomicInteger();

        final Function<UserIdentity, Map<String, Object>> makeRowMap = userIdentity ->
        {
            final Map<String, Object> rowMap = headerAttributeMap.keySet().stream()
                    .collect( CollectorUtil.toLinkedMap(
                            Function.identity(),
                            attribute -> attributeValue( userIdentity, attribute ) ) );

            rowMap.put( JSON_KEY_USER_KEY, identityEncoder.apply( userIdentity ) );
            rowMap.put( JSON_KEY_ID, idCounter.getAndIncrement() );
            return Map.copyOf( rowMap );
        };

        return this.getResults().keySet().stream()
                .filter( Objects::nonNull )
                .filter( userIdentity -> ignoreUser == null || !ignoreUser.equals( userIdentity ) )
                .map( makeRowMap )
                .collect( Collectors.toUnmodifiableList() );
    }

    private String attributeValue( final UserIdentity userIdentity, final String attributeName )
    {
        if ( userIdentity == null || StringUtil.isEmpty( attributeName ) )
        {
            return "";
        }

        return results.getOrDefault( userIdentity, Collections.emptyMap() ).getOrDefault( attributeName, "" );
    }


    public static Map<String, String> fromFormConfiguration( final List<FormConfiguration> formItems, final Locale locale )
    {
        return formItems.stream().collect( CollectorUtil.toUnmodifiableLinkedMap(
                FormConfiguration::getName,
                formItem -> formItem.getLabel( locale ) ) );
    }

    private static class UserIdentitySearchResultComparator implements Comparator<UserIdentity>
    {
        private final Map<UserIdentity, Map<String, String>> results;
        private final String sortAttribute;

        UserIdentitySearchResultComparator(
                final Map<UserIdentity, Map<String, String>> results,
                final String sortAttribute
        )
        {
            this.results = results;
            this.sortAttribute = sortAttribute;
        }

        @Override
        public int compare( final UserIdentity o1, final UserIdentity o2 )
        {
            final String s1 = getSortValueByIdentity( o1 );
            final String s2 = getSortValueByIdentity( o2 );
            return s1.compareTo( s2 );
        }

        private String getSortValueByIdentity( final UserIdentity userIdentity )
        {
            final Map<String, String> valueMap = results.get( userIdentity );
            if ( valueMap != null )
            {
                final String sortValue = valueMap.get( sortAttribute );
                if ( sortValue != null )
                {
                    return sortValue;
                }
            }
            return "";
        }
    }
}
