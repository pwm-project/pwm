/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2012 The PWM Project
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

package password.pwm;

import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.*;
import password.pwm.util.stats.Statistic;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.*;

/**
 * This filter (invoked by the container through the web.xml descriptor) rewrites access to
 * files under the resources folder to theme files, if available.
 *
 * @author Menno Pieters
 */
public class ThemeFileFilter implements Filter {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ThemeFileFilter.class);
    
    private FilterConfig filterConfig = null;

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface Filter ---------------------

    public void init(final FilterConfig filterConfig) throws ServletException {
		LOGGER.info("Initializing ThemeFileFilter");
		this.filterConfig = filterConfig;
    }

    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        final HttpServletResponse resp = (HttpServletResponse) servletResponse;
   	    PwmSession pwmSession;
        try {
	        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req.getSession());
	        pwmSession = PwmSession.getPwmSession((HttpServletRequest) req);
	        ThemeFileManager tfm = pwmApplication.getThemeFileManager();

			String path = req.getServletPath();
			String pathInfo = req.getPathInfo();
			String fullPath = (pathInfo==null)?path:path+pathInfo;
			LOGGER.debug("Theme filter triggered for "+fullPath);
			if (path != null && tfm != null) {
				//String newPath = tfm.themedPath(path, req.getSession().getServletContext());
				String newPath = tfm.themedPath(req);
				if (newPath == null || fullPath.equals(newPath) || path.equals(newPath)) {
		    		filterChain.doFilter(req, resp);
				} else {
					LOGGER.debug("New path for "+path+": "+newPath+"; redirecting…");
					resp.sendRedirect(req.getContextPath()+newPath);
	    			filterChain.doFilter(req, resp);
				}
			} else {
	    		filterChain.doFilter(req, resp);
			}
       	} catch (PwmUnrecoverableException e) {
       		LOGGER.error(e.toString());
    		filterChain.doFilter(req, resp);
	    }
    }

    public void destroy() {
		this.filterConfig = null;
    }

}