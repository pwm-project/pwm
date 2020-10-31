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

package password.pwm.svc.email;

import lombok.Builder;
import lombok.Value;
import password.pwm.config.option.SmtpServerType;
import password.pwm.error.ErrorInformation;
import password.pwm.util.PasswordData;
import password.pwm.util.java.MovingAverage;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

@Value
@Builder
public class EmailServer
{
    private String id;
    private String host;
    private int port;
    private String username;
    private PasswordData password;
    private Properties javaMailProps;
    private jakarta.mail.Session session;
    private SmtpServerType type;

    private final StatisticCounterBundle<ServerStat> connectionStats = new StatisticCounterBundle<>( ServerStat.class );
    private final MovingAverage averageSendTime = new MovingAverage( TimeDuration.MINUTE );
    private final AtomicReference<ErrorInformation> lastConnectError = new AtomicReference<>();


    enum ServerStat
    {
        sendCount,
        sendFailures,
        newConnections,
        failedConnections,
    }

    public String toDebugString()
    {
        final Map<String, String> debugProps = new LinkedHashMap<>(  );
        debugProps.put( "id", id );
        debugProps.put( "host", host );
        debugProps.put( "type", type.name() );
        debugProps.put( "port", String.valueOf( port ) );
        if ( !StringUtil.isEmpty( username ) )
        {
            debugProps.put( "username", username );
        }
        return StringUtil.mapToString( debugProps );
    }
}
