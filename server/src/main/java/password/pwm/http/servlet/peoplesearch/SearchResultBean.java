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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class SearchResultBean implements Serializable
{
    private List<Map<String, Object>> searchResults = new ArrayList<>();
    private boolean sizeExceeded;
    private String aboutResultMessage;
    private boolean fromCache;

    public List<Map<String, Object>> getSearchResults( )
    {
        return searchResults;
    }

    public void setSearchResults( final List<Map<String, Object>> searchResults )
    {
        this.searchResults = searchResults;
    }

    public boolean isSizeExceeded( )
    {
        return sizeExceeded;
    }

    public void setSizeExceeded( final boolean sizeExceeded )
    {
        this.sizeExceeded = sizeExceeded;
    }

    public String getAboutResultMessage( )
    {
        return aboutResultMessage;
    }

    public void setAboutResultMessage( final String aboutResultMessage )
    {
        this.aboutResultMessage = aboutResultMessage;
    }

    public boolean isFromCache( )
    {
        return fromCache;
    }

    public void setFromCache( final boolean fromCache )
    {
        this.fromCache = fromCache;
    }
}
