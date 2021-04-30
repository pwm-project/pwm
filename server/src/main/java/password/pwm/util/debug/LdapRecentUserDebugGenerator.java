/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.util.debug;

import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.UserIdentity;
import password.pwm.http.servlet.admin.UserDebugDataBean;
import password.pwm.http.servlet.admin.UserDebugDataReader;
import password.pwm.util.java.JsonUtil;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class LdapRecentUserDebugGenerator implements DomainItemGenerator
{
    @Override
    public String getFilename()
    {
        return "recentUserDebugData.json";
    }

    @Override
    public void outputItem( final DomainDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
    {
        final PwmDomain pwmDomain = debugItemInput.getPwmDomain();
        final List<UserIdentity> recentUsers = pwmDomain.getPwmApplication().getSessionTrackService().getRecentLogins();
        final List<UserDebugDataBean> recentDebugBeans = new ArrayList<>();

        for ( final UserIdentity userIdentity : recentUsers )
        {
            if ( Objects.equals( userIdentity.getDomainID(), pwmDomain.getDomainID() ) )
            {
                final UserDebugDataBean dataBean = UserDebugDataReader.readUserDebugData(
                        pwmDomain,
                        debugItemInput.getLocale(),
                        debugItemInput.getSessionLabel(),
                        userIdentity
                );
                recentDebugBeans.add( dataBean );
            }
        }

        outputStream.write( JsonUtil.serializeCollection( recentDebugBeans, JsonUtil.Flag.PrettyPrint ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
    }
}
