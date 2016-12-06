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

package password.pwm.util;

import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.FormNonce;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.state.SessionStateService;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Pattern;

/**
 * A collection of static methods used throughout PWM
 *
 * @author Jason D. Rivard
 */
public class Helper {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(Helper.class);

    // -------------------------- STATIC METHODS --------------------------

    private Helper() {
    }


    public static String formatDiskSize(final long diskSize) {
        final float COUNT = 1000;
        if (diskSize < 1) {
            return "n/a";
        }

        if (diskSize == 0) {
            return "0";
        }

        final NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);

        if (diskSize > COUNT * COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT / COUNT));
            sb.append(" GB");
            return sb.toString();
        }

        if (diskSize > COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT));
            sb.append(" MB");
            return sb.toString();
        }

        return NumberFormat.getInstance().format(diskSize) + " bytes";
    }


    public static String buildPwmFormID(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final SessionStateService sessionStateService = pwmRequest.getPwmApplication().getSessionStateService();
        final String value = sessionStateService.getSessionStateInfo(pwmRequest);
        final FormNonce formID = new FormNonce(
                pwmRequest.getPwmSession().getLoginInfoBean().getGuid(),
                new Date(),
                pwmRequest.getPwmSession().getLoginInfoBean().getReqCounter(),
                value
        );
        return pwmRequest.getPwmApplication().getSecureService().encryptObjectToString(formID);
    }


    public static String makeThreadName(final PwmApplication pwmApplication, final Class theClass) {
        String instanceName = "-";
        if (pwmApplication != null && pwmApplication.getInstanceID() != null) {
            instanceName = pwmApplication.getInstanceID();
        }

        return PwmConstants.PWM_APP_NAME + "-" + instanceName + "-" + theClass.getSimpleName();
    }

    public static void checkUrlAgainstWhitelist(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final String inputURL
    )
            throws PwmOperationalException
    {
        LOGGER.trace(sessionLabel, "beginning test of requested redirect URL: " + inputURL);
        if (inputURL == null || inputURL.isEmpty()) {
            return;
        }

        final URI inputURI;
        try {
            inputURI = URI.create(inputURL);
        } catch (IllegalArgumentException e) {
            LOGGER.error(sessionLabel, "unable to parse requested redirect url '" + inputURL + "', error: " + e.getMessage());
            // dont put input uri in error response
            final String errorMsg = "unable to parse url: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_REDIRECT_ILLEGAL,errorMsg));
        }

        { // check to make sure we werent handed a non-http uri.
            final String scheme = inputURI.getScheme();
            if (scheme != null && !scheme.isEmpty() && !scheme.equalsIgnoreCase("http") && !scheme.equals("https")) {
                final String errorMsg = "unsupported url scheme";
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_REDIRECT_ILLEGAL,errorMsg));
            }
        }

        if (inputURI.getHost() != null && !inputURI.getHost().isEmpty()) { // disallow localhost uri
            try {
                final InetAddress inetAddress = InetAddress.getByName(inputURI.getHost());
                if (inetAddress.isLoopbackAddress()) {
                    final String errorMsg = "redirect to loopback host is not permitted";
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_REDIRECT_ILLEGAL,errorMsg));
                }
            } catch (UnknownHostException e) {
                /* noop */
            }
        }

        final StringBuilder sb = new StringBuilder();
        if (inputURI.getScheme() != null) {
            sb.append(inputURI.getScheme());
            sb.append("://");
        }
        if (inputURI.getHost() != null) {
            sb.append(inputURI.getHost());
        }
        if (inputURI.getPort() != -1) {
            sb.append(":");
            sb.append(inputURI.getPort());
        }
        if (inputURI.getPath() != null) {
            sb.append(inputURI.getPath());
        }

        final String testURI = sb.toString();
        LOGGER.trace(sessionLabel, "preparing to whitelist test parsed and decoded URL: " + testURI);

        final String REGEX_PREFIX = "regex:";
        final List<String> whiteList = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.SECURITY_REDIRECT_WHITELIST);
        for (final String loopFragment : whiteList) {
            if (loopFragment.startsWith(REGEX_PREFIX)) {
                try {
                    final String strPattern = loopFragment.substring(REGEX_PREFIX.length(), loopFragment.length());
                    final Pattern pattern = Pattern.compile(strPattern);
                    if (pattern.matcher(testURI).matches()) {
                        LOGGER.debug(sessionLabel, "positive URL match for regex pattern: " + strPattern);
                        return;
                    } else {
                        LOGGER.trace(sessionLabel, "negative URL match for regex pattern: " + strPattern);
                    }
                } catch (Exception e) {
                    LOGGER.error(sessionLabel, "error while testing URL match for regex pattern: '" + loopFragment + "', error: " + e.getMessage());;
                }

            } else {
                if (testURI.startsWith(loopFragment)) {
                    LOGGER.debug(sessionLabel, "positive URL match for pattern: " + loopFragment);
                    return;
                } else {
                    LOGGER.trace(sessionLabel, "negative URL match for pattern: " + loopFragment);
                }
            }
        }

        final String errorMsg = testURI + " is not a match for any configured redirect whitelist, see setting: " + PwmSetting.SECURITY_REDIRECT_WHITELIST.toMenuLocationDebug(null,PwmConstants.DEFAULT_LOCALE);
        throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_REDIRECT_ILLEGAL,errorMsg));
    }

    public static boolean determineIfDetailErrorMsgShown(final PwmApplication pwmApplication) {
        if (pwmApplication == null) {
            return false;
        }
        final PwmApplicationMode mode = pwmApplication.getApplicationMode();
        if (mode == PwmApplicationMode.CONFIGURATION || mode == PwmApplicationMode.NEW) {
            return true;
        }
        if (mode == PwmApplicationMode.RUNNING) {
            if (pwmApplication.getConfig() != null) {
                if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static CSVPrinter makeCsvPrinter(final OutputStream outputStream)
            throws IOException
    {
        return new CSVPrinter(new OutputStreamWriter(outputStream,PwmConstants.DEFAULT_CHARSET), PwmConstants.DEFAULT_CSV_FORMAT);
    }


    public static Properties newSortedProperties() {
        return new Properties() {
            public synchronized Enumeration<Object> keys() {
                return Collections.enumeration(new TreeSet<>(super.keySet()));
            }
        };
    }

    public static ThreadFactory makePwmThreadFactory(final String namePrefix, final boolean daemon) {
        return new ThreadFactory() {
            private final ThreadFactory realThreadFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(final Runnable r) {
                final Thread t = realThreadFactory.newThread(r);
                t.setDaemon(daemon);
                if (namePrefix != null) {
                    final String newName = namePrefix + t.getName();
                    t.setName(newName);
                }
                return t;
            }
        };
    }

}
