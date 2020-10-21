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

package password.pwm.ldap.search;

import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserSearchResults implements Serializable
{
    private final Map<String, String> headerAttributeMap;
    private final Map<UserIdentity, Map<String, String>> results;
    private boolean sizeExceeded;

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
        if ( headerAttributeMap == null || headerAttributeMap.isEmpty() || results == null )
        {
            return results;
        }

        final String sortAttribute = headerAttributeMap.keySet().iterator().next();
        final Comparator<UserIdentity> comparator = new Comparator<UserIdentity>()
        {
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
        };

        final List<UserIdentity> identitySortMap = new ArrayList<>( results.keySet() );
        identitySortMap.sort( comparator );

        final Map<UserIdentity, Map<String, String>> sortedResults = new LinkedHashMap<>();
        for ( final UserIdentity userIdentity : identitySortMap )
        {
            sortedResults.put( userIdentity, results.get( userIdentity ) );
        }
        return sortedResults;
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

    public List<Map<String, Object>> resultsAsJsonOutput( final PwmApplication pwmApplication, final UserIdentity ignoreUser )
            throws PwmUnrecoverableException
    {
        final List<Map<String, Object>> outputList = new ArrayList<>();
        int idCounter = 0;
        for ( final UserIdentity userIdentity : this.getResults().keySet() )
        {
            if ( ignoreUser == null || !ignoreUser.equals( userIdentity ) )
            {
                final Map<String, Object> rowMap = new LinkedHashMap<>();
                for ( final String attribute : this.getHeaderAttributeMap().keySet() )
                {
                    rowMap.put( attribute, this.getResults().get( userIdentity ).get( attribute ) );
                }
                rowMap.put( "userKey", userIdentity.toObfuscatedKey( pwmApplication ) );
                rowMap.put( "id", idCounter );
                outputList.add( rowMap );
                idCounter++;
            }
        }
        return outputList;
    }

    public static Map<String, String> fromFormConfiguration( final List<FormConfiguration> formItems, final Locale locale )
    {
        final Map<String, String> results = new LinkedHashMap<>();
        for ( final FormConfiguration formItem : formItems )
        {
            results.put( formItem.getName(), formItem.getLabel( locale ) );
        }
        return results;
    }
}
