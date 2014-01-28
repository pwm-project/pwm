/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util.macro;

import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserInfoBean;
import password.pwm.ldap.UserDataReader;
import password.pwm.util.Helper;
import password.pwm.ws.server.rest.RestStatusServer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * External macro @External1:<value>@ where 1 is incremental configuration item.
 */
class ExternalRestMacro extends AbstractMacro {
    private PwmApplication pwmApplication;
    private UserInfoBean userInfoBean;

    private Pattern pattern = null;
    private String url = null;

    public ExternalRestMacro() {
    }

    public ExternalRestMacro(
            final int iteration,
            final String url
    ) {
        pattern = Pattern.compile("@External" + iteration + ":.*@");
        this.url = url;
    }

    public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
        this.pwmApplication = pwmApplication;
        this.userInfoBean = userInfoBean;

    }
    public Pattern getRegExPattern()
    {
        return pattern;
    }

    public String replaceValue(
            String matchValue
    )
    {
        final String inputString = matchValue.substring(11,matchValue.length() -1);
        final HashMap<String,Object> sendData = new HashMap<String, Object>();
        if (userInfoBean != null) {
            final RestStatusServer.JsonStatusData jsonStatusData = RestStatusServer.JsonStatusData.fromUserInfoBean(userInfoBean,pwmApplication.getConfig(), PwmConstants.DEFAULT_LOCALE);
            sendData.put("userInfo",jsonStatusData);
        }
        sendData.put("input",inputString);

        final HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-Type", "application/json");
        final HttpResponse httpResponse;
        try {
            final StringEntity stringEntity = new StringEntity(Helper.getGson().toJson(sendData));
            stringEntity.setContentType("application/json");
            httpPost.setEntity(new StringEntity(Helper.getGson().toJson(sendData)));
            httpResponse = Helper.getHttpClient(pwmApplication.getConfig()).execute(httpPost);
            final String responseBody = EntityUtils.toString(httpResponse.getEntity());
            final Map<String,Object> responseMap = Helper.getGson().fromJson(responseBody,new TypeToken<Map<String, Object>>() {}.getType());
            if (responseMap.containsKey("output")) {
                return responseMap.get("output").toString();
            } else {
                return "";
            }
        } catch (IOException e) {
            throw new IllegalStateException("http response error code: " + e.getMessage());
        }
    }
}
