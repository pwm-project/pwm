/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.servlet.helpdesk;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HelpdeskSearchResultsBean implements Serializable {
    private List<Map<String,Object>> searchResults = new ArrayList<>();
    private boolean sizeExceeded;

    public List<Map<String, Object>> getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List<Map<String, Object>> searchResults) {
        this.searchResults = searchResults;
    }

    public boolean isSizeExceeded() {
        return sizeExceeded;
    }

    public void setSizeExceeded(boolean sizeExceeded) {
        this.sizeExceeded = sizeExceeded;
    }
}
