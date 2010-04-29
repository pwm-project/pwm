/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.Validator;
import password.pwm.bean.ConfigManagerBean;
import password.pwm.config.NewConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManagerServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigManagerServlet.class);

    private static final int DEFAULT_INPUT_LENGTH = 1024 * 10;

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {

        final String processRequestParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, DEFAULT_INPUT_LENGTH);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();

        if (configManagerBean.getConfiguration() == null) {
            configManagerBean.setConfiguration(NewConfiguration.getDefaultConfiguration());
        }

        if ("generateXml".equalsIgnoreCase(processRequestParam)) {
            //this.doGenerateXmlDoc(req,resp);
            return;
        } else if ("readSetting".equalsIgnoreCase(processRequestParam)) {
            this.readSetting(req,resp);
            return;
        } else if ("writeSetting".equalsIgnoreCase(processRequestParam)) {
            this.writeSetting(req,resp);
            return;
        }

        this.forwardToJSP(req, resp);
    }

    private void readSetting(
            final HttpServletRequest req,
            final HttpServletResponse resp
    ) throws IOException, PwmException {
        Validator.checkFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final NewConfiguration configuration = configManagerBean.getConfiguration();
        final String settingKey = Validator.readStringFromRequest(req,"key",1024 * 10);
        if (settingKey.equals("expirePreTime")) {
            System.out.println("yep!");
        }

        final PwmSetting theSetting = PwmSetting.forKey(settingKey);

        final Map<String,String> returnMap = new HashMap<String,String>();
        returnMap.put("key", theSetting.getKey());
        returnMap.put("value", configuration.readStringSetting(theSetting));
        final String outputString = JSONObject.toJSONString(returnMap);
        resp.setContentType("text/plain;charset=utf-8");
        resp.getWriter().print(outputString);
    }

    private void writeSetting(
            final HttpServletRequest req,
            final HttpServletResponse resp
    ) throws IOException, PwmException {
        Validator.checkFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final NewConfiguration configuration= configManagerBean.getConfiguration();

        final String bodyString = readRequestBody(req);

        System.out.println("received body: " + bodyString);

        final JSONObject srcMap = (JSONObject) JSONValue.parse(bodyString);

        if (srcMap != null) {
            final String key = String.valueOf(srcMap.get("key"));
            final String value = String.valueOf(srcMap.get("value"));

            final PwmSetting setting = PwmSetting.forKey(key);
            configuration.writeStringSetting(setting, value);
        }
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER).forward(req, resp);
    }

    private static String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuffer json = new StringBuffer();
        String line = null;
        try {
            BufferedReader reader = request.getReader();
            while((line = reader.readLine()) != null) {
                json.append(line);
            }
        }
        catch(Exception e) {
            System.out.println("Error reading JSON string: " + e.toString());
        }
        return json.toString();
    }
}
