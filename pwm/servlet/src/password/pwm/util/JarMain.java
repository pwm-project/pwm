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

import password.pwm.PwmConstants;

import javax.swing.*;

public class JarMain {
// ----------------------------- CONSTANTS ----------------------------


// ------------------------------ FIELDS ------------------------------

// --------------------------- main() method ---------------------------

    public static void main(final String[] args)
    {
        System.out.println(buildInfoString());

        JOptionPane.showMessageDialog
                (null,
                        buildInfoString(),
                        "About",
                        JOptionPane.INFORMATION_MESSAGE);
    }

    private static String buildInfoString()
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(PwmConstants.BUILD_NAME + " v" + PwmConstants.PWM_VERSION + " (" + PwmConstants.BUILD_TYPE + ")\n");
        sb.append("\n");
        sb.append("Build Information: \n");

        sb.append("build.name=" + PwmConstants.BUILD_NAME + "\n");
        sb.append("build.time=" + PwmConstants.BUILD_TIME + "\n");
        sb.append("build.number=" + PwmConstants.BUILD_NUMBER + "\n");
        sb.append("build.type=" + PwmConstants.BUILD_TYPE + "\n");
        sb.append("build.user=" + PwmConstants.BUILD_USER + "\n");
        sb.append("build.java.version=" + PwmConstants.BUILD_JAVA_VERSION + "\n");
        sb.append("build.java.vendor=" + PwmConstants.BUILD_JAVA_VENDOR + "\n");

        sb.append("\n");
        sb.append("Reference URL: " + PwmConstants.PWM_URL_HOME + "\n");
        sb.append("\n");
        sb.append("source files are included inside jar archive");

        return sb.toString();
    }
}
