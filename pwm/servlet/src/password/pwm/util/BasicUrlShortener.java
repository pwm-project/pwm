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
 
import java.util.Properties;
import password.pwm.util.AbstractUrlShortener;
import password.pwm.PwmApplication;

public class BasicUrlShortener implements AbstractUrlShortener {
	private Properties configuration = null;

	public BasicUrlShortener() {
	}
	
	public BasicUrlShortener(Properties configuration) {
		this.configuration = configuration;
	}
	
	public void setConfiguration(Properties configuration) {
		this.configuration = configuration;
	}

	public Properties getConfiguration() {
		return configuration;
	}
	
	public String shorten(String input, PwmApplication context) {
		/* 
		 * This function does nothing.
		 * Real functionality has to be implemented by extending this class
		 */
		return input;
	}
}
