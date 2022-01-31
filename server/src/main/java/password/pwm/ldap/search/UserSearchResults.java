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

import password.pwm.PwmDomain;
import password.pwm.bean.UserIdentity;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.CollectionUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class UserSearchResults implements Serializable
{
    private final Map<String, String> headerAttributeMap;
    private final Map<UserIdentity, Map<String, String>> results;
    private final boolean sizeExceeded;

    public UserSearchResults( final Map<String, String> headerAttributeMap, final Map<UserIdentity, Map<String, String>> results, final boolean sizeExceeded )
    {
        this.headerAttributeMap = headerAttributeMap;
        this.results = Collections.unmodifiableMap( defaultSort( results, headerAttributeMap ) );
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
                .collect( CollectionUtil.collectorToLinkedMap(
                        Function.identity(),
                        results::get
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
            final PwmDomain pwmDomain,
            final UserIdentity ignoreUser
    )
            throws PwmUnrecoverableException
    {
        final List<Map<String, Object>> outputList = new ArrayList<>( this.getResults().size() );
        int idCounter = 0;
        for ( final UserIdentity userIdentity : this.getResults().keySet() )
        {
            if ( ignoreUser == null || !ignoreUser.equals( userIdentity ) )
            {
                final Map<String, Object> rowMap = new LinkedHashMap<>( this.getHeaderAttributeMap().size() );
                for ( final String attribute : this.getHeaderAttributeMap().keySet() )
                {
                    rowMap.put( attribute, this.getResults().get( userIdentity ).get( attribute ) );
                }
                rowMap.put( "userKey", userIdentity.toObfuscatedKey( pwmDomain.getPwmApplication() ) );
                rowMap.put( "id", idCounter );
                outputList.add( rowMap );
                idCounter++;
            }
        }
        return outputList;
    }

    public static Map<String, String> fromFormConfiguration( final List<FormConfiguration> formItems, final Locale locale )
    {
        return Collections.unmodifiableMap( formItems.stream().collect( CollectionUtil.collectorToLinkedMap(
                FormConfiguration::getName,
                formItem -> formItem.getLabel( locale )
        ) ) );
    }

    private static class UserIdentitySearchResultComparator implements Comparator<UserIdentity>, Serializable
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
