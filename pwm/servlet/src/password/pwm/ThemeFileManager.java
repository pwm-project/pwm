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

package password.pwm;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.util.PwmLogger;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.util.Helper;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.HashMap;

import javax.servlet.ServletContext;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class ThemeFileManager implements PwmService {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ThemeFileManager.class);

    private PwmApplication theApplication;

    private STATUS status = PwmService.STATUS.NEW;
    
    private HashMap<String,String> cache = new HashMap<String,String>(100, (float) 0.75); 

// --------------------------- CONSTRUCTORS ---------------------------

    public ThemeFileManager() {
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface PwmService ---------------------

    public void init(final PwmApplication pwmApplication) throws PwmException {
        this.theApplication = pwmApplication;
        status = PwmService.STATUS.OPEN;
    }

    public STATUS status() {
        return status;
    }

    public void close() {
        status = PwmService.STATUS.CLOSED;
        clearCache();
        LOGGER.debug("closed");
    }

    public List<HealthRecord> healthCheck() {
		return null;
    }

// -------------------------- OTHER METHODS --------------------------

	/**
	 * Clears the cache. Could be called by a periodic task.
	 */
	public void clearCache() {
		LOGGER.info("Clearing theme file cache");
		cache.clear();
	}
	
	public void putCache(final String standardPath, final String themedPath) {
		LOGGER.trace("Adding file "+standardPath+" to cache");
		cache.put(standardPath, themedPath);
	}

	public String themedPath(final String path, ServletContext ctx) {
		LOGGER.trace("Finding themed file for "+path);
		if (path != null) {
			if (path.startsWith(PwmConstants.URL_THEMES)) {
				LOGGER.debug(path+" is themed file already");
				return path;
			}
			if (cache.containsKey(path)) {
				LOGGER.trace("Returning from cached entry");
				return (String) cache.get(path);
			}
			final String theme = theApplication.getConfig().readSettingAsString(PwmSetting.INTERFACE_THEME);
			if (Helper.fileExists(ctx.getRealPath(path))) {
				LOGGER.trace("Looking up themed version for "+path);
				// Find file with full path relative to theme directory
				final String altPath1 = PwmConstants.URL_THEMES+theme+path;
				LOGGER.trace("Trying "+altPath1);
				if (Helper.fileExists(ctx.getRealPath(altPath1))) {
					LOGGER.trace("Found themed version for "+path+": "+altPath1);
					putCache(path, altPath1);
					return altPath1;
				}
				// Find file with simple filename relative to theme directory
				final String altPath2 = PwmConstants.URL_THEMES+theme+"/"+(new File(path)).getName();
				LOGGER.trace("Trying "+altPath2);
				if (Helper.fileExists(ctx.getRealPath(altPath2))) {
					LOGGER.trace("Found themed version for "+path+": "+altPath2);
					putCache(path, altPath2);
					return altPath2;
				}
				LOGGER.trace("Found no themed version for "+path+"; returning original");
				putCache(path, path);
				return path;
			} else {
				LOGGER.warn("Original file for "+path+" does not exist; returning null");
				return null;
			}
		}
		return null;
	}
}
