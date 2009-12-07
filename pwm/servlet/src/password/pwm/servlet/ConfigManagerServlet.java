/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.error.PwmException;
import password.pwm.Constants;
import password.pwm.Validator;
import password.pwm.config.PwmSetting;
import password.pwm.util.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.text.SimpleDateFormat;

import org.jdom.Element;
import org.jdom.Document;
import org.jdom.Comment;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

public class ConfigManagerServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigManagerServlet.class);

    private static final int DEFAULT_INPUT_LENGTH = 1024;
    private static final String SETTING_PREFIX = "setting_";

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {

        final String processRequestParam = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, DEFAULT_INPUT_LENGTH);

        final Map<String,Map<String,String>> configMap = readConfigMapFromRequest(req);
        req.setAttribute(Constants.REQUEST_CONFIG_MAP, configMap);

        if ("generateXml".equalsIgnoreCase(processRequestParam)) {
            this.doGenerateXmlDoc(req,resp);
            return;
        }

        this.forwardToJSP(req, resp);
    }

    private void doGenerateXmlDoc(
            final HttpServletRequest req,
            final HttpServletResponse resp
    ) throws IOException {
        final Map<PwmSetting, String> valueMap = new HashMap<PwmSetting, String>();
        for (PwmSetting setting : PwmSetting.values()) {
            final String value = Validator.readStringFromRequest(req, SETTING_PREFIX + setting.getKey(), DEFAULT_INPUT_LENGTH);
            valueMap.put(setting, value);
        }

        Element settingsElement = new Element("settings");
        for (PwmSetting setting : PwmSetting.values()) {
            settingsElement.addContent(setting.toXmlElement(valueMap.get(setting)));
        }

        Element pwmConfigElement = new Element("PwmConfiguration");
        pwmConfigElement.addContent(new Comment("WARNING: This configuration file contains sensitive security information, please handle with care!"));
        pwmConfigElement.addContent(new Comment("Configuration file generated for PWM Servlet"));
        pwmConfigElement.addContent(settingsElement);
        pwmConfigElement.setAttribute("version", Constants.PWM_VERSION);
        pwmConfigElement.setAttribute("build", Constants.BUILD_NUMBER);
        pwmConfigElement.setAttribute("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        String asString = outputter.outputString(new Document(pwmConfigElement));

        resp.setContentType("text/xml");
        resp.getOutputStream().write(asString.getBytes());
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_CONFIG_MANAGER).forward(req, resp);
    }

    private Map<String,Map<String,String>> readConfigMapFromRequest(HttpServletRequest request) {
        final Map<String,Map<String,String>> configMap = new HashMap<String,Map<String,String>>();
        @SuppressWarnings({"unchecked"}) final Set<String> parameterNames = new HashSet<String>(request.getParameterMap().keySet());
        for (String paramName : parameterNames) {
            for (final PwmSetting loopSetting: PwmSetting.values()) {
                if (paramName.startsWith(SETTING_PREFIX + loopSetting.getKey())) {
                    final Map<String,String> localizedMap = new HashMap<String,String>();
                    final String defaultValue = Validator.readStringFromRequest(request, SETTING_PREFIX + paramName, DEFAULT_INPUT_LENGTH);
                    localizedMap.put("",defaultValue);
                    if (loopSetting.isLocalizable()) {
                        for (final Locale loopLocale : Locale.getAvailableLocales()) {
                            final String key = SETTING_PREFIX + loopSetting.getKey() + "_" + loopLocale.toString();
                            if (parameterNames.contains(key)) {
                                final String value = Validator.readStringFromRequest(request, key, DEFAULT_INPUT_LENGTH);
                                localizedMap.put(loopLocale.toString(), value);
                            }
                        }
                    }

                    configMap.put(loopSetting.getKey(), localizedMap);
                }
            }

        }

        if (configMap.isEmpty()) {
            LOGGER.debug("initializing default values");
            for (final PwmSetting loopSetting: PwmSetting.values()) {
                final Map<String,String> localizedMap = new HashMap<String,String>();
                localizedMap.put("",loopSetting.getDefaultValue());
                configMap.put(loopSetting.getKey(), localizedMap);
            }
        }

        return configMap;
    }
}
