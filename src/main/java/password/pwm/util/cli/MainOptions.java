/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.util.cli;

import password.pwm.PwmEnvironment;
import password.pwm.util.logging.PwmLogLevel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MainOptions implements Serializable {
    final static private String OPT_DEBUG_LEVEL = "-debugLevel";
    final static private  String OPT_APP_PATH = "-applicationPath";
    final static private  String OPT_APP_FLAGS= "-applicationFlags";
    final static private  String OPT_FORCE = "-force";


    private PwmLogLevel pwmLogLevel = null;
    private File applicationPath = null;
    private boolean forceFlag = false;
    private Collection<PwmEnvironment.ApplicationFlag> applicationFlags;
    private String[] remainingArguments;

    MainOptions(
            PwmLogLevel pwmLogLevel,
            File applicationPath,
            boolean forceFlag,
            Collection<PwmEnvironment.ApplicationFlag> applicationFlags,
            String[] remainingArguments

    ) {
        this.pwmLogLevel = pwmLogLevel;
        this.applicationPath = applicationPath;
        this.forceFlag = forceFlag;
        this.applicationFlags = applicationFlags;
        this.remainingArguments = remainingArguments;

    }

    public PwmLogLevel getPwmLogLevel() {
        return pwmLogLevel;
    }

    public File getApplicationPath() {
        return applicationPath;
    }

    public boolean isForceFlag() {
        return forceFlag;
    }

    public Collection<PwmEnvironment.ApplicationFlag> getApplicationFlags() {
        return applicationFlags;
    }

    public String[] getRemainingArguments() {
        return remainingArguments;
    }

    public static MainOptions parseMainCommandLineOptions(
            String[] args,
            Writer debugWriter
    ) {


        PwmLogLevel pwmLogLevel = null;
        File applicationPath = null;
        boolean forceFlag = false;
        Collection<PwmEnvironment.ApplicationFlag> applicationFlags = Collections.emptyList();
        String[] remainingArguments;

        final List<String> outputArgs = new ArrayList<>();
        if (args != null) {
            for (final String arg : args) {
                if (arg != null) {
                    if (arg.startsWith(OPT_DEBUG_LEVEL)) {
                        if (arg.length() < OPT_DEBUG_LEVEL.length() + 2) {
                            out(debugWriter, OPT_DEBUG_LEVEL + " option must include level (example: -" + OPT_DEBUG_LEVEL + "=TRACE");
                            System.exit(-1);
                        } else {
                            final String levelStr = arg.substring(OPT_DEBUG_LEVEL.length() + 1, arg.length());
                            try {
                                pwmLogLevel = PwmLogLevel.valueOf(levelStr.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                out(debugWriter, " unknown log level value: " + levelStr);
                                System.exit(-1);
                            }
                        }
                    } else if (arg.startsWith(OPT_APP_PATH)) {
                        if (arg.length() < OPT_APP_PATH.length() + 2) {
                            out(debugWriter, OPT_APP_PATH + " option must include value (example: -" + OPT_APP_PATH + "=/tmp/applicationPath");
                            System.exit(-1);
                        } else {
                            final String pathStr = arg.substring(OPT_APP_PATH.length() + 1, arg.length());
                            applicationPath = new File(pathStr);
                        }
                    } else if (arg.equals(OPT_FORCE)) {
                        forceFlag = true;
                    } else if (arg.startsWith(OPT_APP_FLAGS)) {
                        if (arg.length() < OPT_APP_FLAGS.length() + 2) {
                            out(debugWriter, OPT_APP_FLAGS + " option must include value (example: -" + OPT_APP_FLAGS + "=Flag1,Flag2");
                            System.exit(-1);
                        } else {
                            final String flagStr = arg.substring(OPT_APP_PATH.length() + 1, arg.length());
                            applicationFlags = PwmEnvironment.ParseHelper.parseApplicationFlagValueParameter(flagStr);
                        }
                        outputArgs.add(arg);
                    } else {
                        outputArgs.add(arg);
                    }
                }
            }
        }

        remainingArguments = outputArgs.toArray(new String[outputArgs.size()]);
        return new MainOptions(pwmLogLevel, applicationPath, forceFlag, applicationFlags, remainingArguments);
    }

    static void out(final Writer debugWriter, final CharSequence out) {
        if (debugWriter != null) {
            try {
                debugWriter.append(out);
                debugWriter.append("\n");
                debugWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
