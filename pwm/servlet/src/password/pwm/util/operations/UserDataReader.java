/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.util.operations;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;

import java.io.Serializable;
import java.util.*;

public class UserDataReader implements Serializable {

    private static final Boolean NULL_CACHE_VALUE = Boolean.FALSE;

    private final Map<String,Object> cacheMap = new ConcurrentLinkedHashMap.Builder<String, Object>()
            .maximumWeightedCapacity(100)  // safety limit
            .build();
    private final ChaiUser user;

    public UserDataReader(ChaiUser user) {
        this.user = user;
    }

    public String getUserDN() {
        return this.user.getEntryDN();
    }

    public String readStringAttribute(final String attribute)
            throws ChaiUnavailableException, ChaiOperationException
    {
        return readStringAttribute(attribute, false);
    }

    public String readStringAttribute(final String attribute, boolean ignoreCache)
            throws ChaiUnavailableException, ChaiOperationException
    {
        Map<String,String> results = readStringAttributes(Collections.singletonList(attribute),ignoreCache);
        if (results == null || results.isEmpty()) {
            return null;
        }

        return results.values().iterator().next();
    }

    public Map<String,String> readStringAttributes(final Collection<String> attributes)
            throws ChaiUnavailableException, ChaiOperationException
    {
        return readStringAttributes(attributes, false);
    }

    public Map<String,String> readStringAttributes(final Collection<String> attributes, boolean ignoreCache)
            throws ChaiUnavailableException, ChaiOperationException
    {
        if (user == null || attributes == null || attributes.isEmpty()) {
            return Collections.emptyMap();
        }

        if (ignoreCache) {
            cacheMap.keySet().removeAll(attributes);
        }

        // figure out uncached attributes.
        final List<String> uncachedAttributes = new ArrayList<String>(attributes);
        uncachedAttributes.removeAll(cacheMap.keySet());

        // read uncached attributes into cache
        if (!uncachedAttributes.isEmpty()) {
            final Map<String,String> readData = user.readStringAttributes(new HashSet<String>(uncachedAttributes));
            for (final String attribute : attributes) {
                cacheMap.put(attribute,readData.containsKey(attribute) ? readData.get(attribute) : NULL_CACHE_VALUE);
            }
        }

        // build result data from cache
        final Map<String,String> returnMap = new HashMap<String,String>();
        for (final String attribute : attributes) {
            final Object cachedValue = cacheMap.get(attribute);
            if (cachedValue != null && !NULL_CACHE_VALUE.equals(cachedValue)) {
                returnMap.put(attribute,(String)cachedValue);
            }
        }
        return returnMap;
    }
}
