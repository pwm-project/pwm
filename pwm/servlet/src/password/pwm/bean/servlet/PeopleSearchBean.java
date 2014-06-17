/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.bean.servlet;

import password.pwm.bean.PwmSessionBean;
import password.pwm.ldap.UserSearchEngine;

import java.util.Collections;
import java.util.Map;

public class PeopleSearchBean implements PwmSessionBean {
    private String searchString;
    private Map<String,String> searchColumnHeaders = Collections.emptyMap();
    private UserSearchEngine.UserSearchResults searchDetails;

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public Map<String, String> getSearchColumnHeaders()
    {
        return searchColumnHeaders;
    }

    public void setSearchColumnHeaders(Map<String, String> searchColumnHeaders)
    {
        this.searchColumnHeaders = searchColumnHeaders;
    }

    public UserSearchEngine.UserSearchResults getSearchDetails() {
        return searchDetails;
    }

    public void setSearchDetails(UserSearchEngine.UserSearchResults searchDetails) {
        this.searchDetails = searchDetails;
    }
}
