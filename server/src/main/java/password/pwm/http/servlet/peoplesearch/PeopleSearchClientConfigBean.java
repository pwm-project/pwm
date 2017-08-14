/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import java.util.Map;

public class PeopleSearchClientConfigBean implements Serializable {

    private Map<String,String> peoplesearch_search_columns;
    private boolean peoplesearch_enablePhoto;
    private boolean peoplesearch_orgChartEnabled;

    public Map<String, String> getPeoplesearch_search_columns() {
        return peoplesearch_search_columns;
    }

    public void setPeoplesearch_search_columns(final Map<String, String> peoplesearch_search_columns) {
        this.peoplesearch_search_columns = peoplesearch_search_columns;
    }

    public boolean isPeoplesearch_enablePhoto() {
        return peoplesearch_enablePhoto;
    }

    public void setPeoplesearch_enablePhoto(final boolean peoplesearch_enablePhoto) {
        this.peoplesearch_enablePhoto = peoplesearch_enablePhoto;
    }

    public boolean isPeoplesearch_orgChartEnabled() {
        return peoplesearch_orgChartEnabled;
    }

    public void setPeoplesearch_orgChartEnabled(final boolean peoplesearch_orgChartEnabled) {
        this.peoplesearch_orgChartEnabled = peoplesearch_orgChartEnabled;
    }
}
