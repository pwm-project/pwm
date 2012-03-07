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

package password.pwm.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * This class extends HttpServletRequestWrapper and provides the 
 * possibility to update the request path.
 *
 * @author Menno Pieters
 */
public class PwmHttpServletRequest extends HttpServletRequestWrapper {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmHttpServletRequest.class);
    private String altServletPath = null;
	
	public PwmHttpServletRequest(HttpServletRequest request) {
		super(request);
	}
	
	public void setServletPath(String path) {
		this.altServletPath = path;
	}
	
	public String getServletPath() {
		if (altServletPath == null) {
			return super.getServletPath();
		}
		return altServletPath;
	}
	
	public String getRequestURI() {
		return getContextPath()+getServletPath();
	}

	public StringBuffer getRequestURL() {
		final StringBuffer sb = new StringBuffer();
		sb.append(getScheme());
		sb.append("://");
		sb.append(getServerName());
		sb.append(":");
		sb.append(getServerPort());
		sb.append(getContextPath());
		sb.append(getServletPath());
		return sb;
	}
}
