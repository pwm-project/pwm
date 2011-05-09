/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm.bean;

import password.pwm.config.FormConfiguration;

import java.io.Serializable;
import java.util.*;

/**
 * @author Jason D. Rivard, Menno Pieters
 */
public class GuestUpdateServletBean implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private String updateUserDN;
    private String guestAdminDN;
    private Integer maxDuration = 0;
    private String namingAttribute;

    private List<FormConfiguration> updateParams;

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getUpdateUserDN() {
        return updateUserDN;
    }

    public void setUpdateUserDN(final String updateUserDN) {
        this.updateUserDN = updateUserDN;
    }

    public String getGuestAdminDN() {
        return guestAdminDN;
    }

    public void setGuestAdminDN(final String guestAdminDN) {
        this.guestAdminDN = guestAdminDN;
    }

    public List<FormConfiguration> getUpdateParams() {
    	if (namingAttribute == null) {
	        return updateParams;
    	}
    	final List<FormConfiguration> filteredUpdateParams = new LinkedList<FormConfiguration>();
    	for (final FormConfiguration formConfiguration : updateParams) {
            final String key = formConfiguration.getAttributeName();
			if (!key.equalsIgnoreCase(namingAttribute)) {
    			filteredUpdateParams.add(formConfiguration);
			}
    	}
    	return filteredUpdateParams;
    }

    public void setUpdateParams(final List<FormConfiguration> updateParams) {
        this.updateParams = updateParams;
    }
    
    public String getNamingAttribute() {
    	return namingAttribute;
    }
    
    public void setNamingAttribute(final String namingAttribute) {
    	this.namingAttribute = namingAttribute;
    }
    
    public void setMaximumDuration(final Integer maxDuration) {
    	this.maxDuration = maxDuration;
    }
    
    public Integer getMaximumDuration() {
    	return maxDuration;
    }

	public void setRemainingDays(Date expiration) {
		Date currdate = new Date();
		Long currmillis = currdate.getTime();
		Long expmillis = expiration.getTime();
		int diff = new Long((expmillis - currmillis)/1000).intValue();
		diff = (diff > 0)?diff:0;
		int days = Math.round(diff/(60*60*24));
		maxDuration = days;
	}

    public Date getExpirationDate() {
    	GregorianCalendar now = new GregorianCalendar();
    	now.set(Calendar.HOUR_OF_DAY, 0);
    	now.set(Calendar.MINUTE, 0);
    	now.set(Calendar.SECOND, 0);
    	now.set(Calendar.MILLISECOND, 0);
    	now.add(Calendar.DATE, Math.abs(this.maxDuration));
    	return now.getTime();
    }
}

