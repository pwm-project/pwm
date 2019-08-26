/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.bean;

import password.pwm.bean.pub.PublicUserInfoBean;

import java.io.Serializable;
import java.util.Map;

public class RemoteVerificationRequestBean implements Serializable
{
    private String responseSessionID;
    private PublicUserInfoBean userInfo;
    private Map<String, String> userResponses;

    public String getResponseSessionID( )
    {
        return responseSessionID;
    }

    public void setResponseSessionID( final String responseSessionID )
    {
        this.responseSessionID = responseSessionID;
    }

    public PublicUserInfoBean getUserInfo( )
    {
        return userInfo;
    }

    public void setUserInfo( final PublicUserInfoBean userInfo )
    {
        this.userInfo = userInfo;
    }

    public Map<String, String> getUserResponses( )
    {
        return userResponses;
    }

    public void setUserResponses( final Map<String, String> userResponses )
    {
        this.userResponses = userResponses;
    }
}
