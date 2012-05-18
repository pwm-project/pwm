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

package password.pwm.servlet;

import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.io.File;

public class ResourceFileServlet3 extends HttpServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(ResourceFileServlet3.class);

    private File temporaryDirectory;

    @Override
    public void init(ServletConfig config) throws ServletException {
        try {
            final ServletContext servletContext = config.getServletContext();
            final PwmApplication pwmApplication = ContextManager.getContextManager(config.getServletContext()).getPwmApplication();
            final File tempDir = ServletHelper.figureFilepath("pwm-temp","WEB-INF", servletContext);
            if (tempDir.exists() && tempDir.isDirectory()) {

            }
        } catch (Exception e) {
            LOGGER.fatal("fatal error while initializing ResourceFileServlet: " + e.getMessage(),e);
        }
    }

    private static File determineTemporaryDirectory(ServletContext servletContext) throws Exception {
        final File tempDir = ServletHelper.figureFilepath("pwm-temp","WEB-INF", servletContext);
        if (tempDir.exists()) {
            if (tempDir.isDirectory()) {
                clearDirectory(tempDir);
            } else {
                throw new Exception("specified temporary directory already exists as file: " + tempDir.getAbsolutePath());
            }
        } else {
            if (!tempDir.mkdir()) {
                throw new Exception("unable to create temporary directory: " + tempDir.getAbsolutePath());
            }
        }
        return tempDir;
    }

    private static void clearDirectory(File inputFile) {
        for (File file : inputFile.listFiles()) {
            if (file.isDirectory()) {
                clearDirectory(file);
            } else {
                file.delete();
            }
        }
    }

    public static String makeResourcePathNonce(
            final PwmApplication pwmApplication
    )
    {
        if (PwmConstants.RESOURCE_SERVLET_ENABLE_PATH_NONCE) {
            return '/' + PwmConstants.RESOURCE_SERVLET_NONCE_PATH_PREFIX + Long.toString(pwmApplication.getStartupTime().getTime(),36);
        } else {
            return "";
        }
    }

    public static void clearCache(ServletContext servletContext) {
    }
}

