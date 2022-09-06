/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.util.logging;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;

import java.util.List;
import java.util.function.Function;

@Value
@Builder( access = AccessLevel.PRIVATE )
public class PwmLogSettings
{
    @Builder.Default
    private PwmLogLevel localDbLevel = PwmLogLevel.TRACE;

    @Builder.Default
    private PwmLogLevel stdoutLevel = PwmLogLevel.TRACE;

    @Builder.Default
    private PwmLogLevel fileLevel = PwmLogLevel.TRACE;

    @Builder.Default
    private boolean healthLoggingEnabled = false;

    @Builder.Default
    private boolean runtimeLoggingEnabled = false;

    @Builder.Default
    private LogOutputMode logOutputMode = LogOutputMode.traditional;

    @Builder.Default
    private List<String> loggingPackages = List.of( "ROOT" );

    enum LogOutputMode
    {
        traditional( new PwmLogUtil.TraditionalMsgFunction() ),
        json( new PwmLogUtil.JsonMsgFunction() ),;

        private final Function<PwmLogMessage, String> messageSupplier;

        LogOutputMode( final Function<PwmLogMessage, String> messageSupplier )
        {
            this.messageSupplier = messageSupplier;
        }

        public Function<PwmLogMessage, String> getMessageSupplier()
        {
            return messageSupplier;
        }
    }

    public static PwmLogSettings defaultSettings()
    {
        return PwmLogSettings.builder().build();
    }

    public static PwmLogSettings fromAppConfig( final AppConfig appConfig )
    {
        final LogOutputMode logOutputMode =
                JavaHelper.readEnumFromString( LogOutputMode.class, appConfig.readAppProperty( AppProperty.LOGGING_OUTPUT_MODE ) )
                        .orElse( LogOutputMode.traditional );

        final boolean healthLoggingEnabled = Boolean.parseBoolean( appConfig.readAppProperty( AppProperty.LOGGING_OUTPUT_HEALTHCHECK ) );

        final boolean runtimeLoggingEnabled = Boolean.parseBoolean( appConfig.readAppProperty( AppProperty.LOGGING_OUTPUT_RUNTIME ) );

        final List<String> loggingPackages = List.copyOf(
                StringUtil.splitAndTrim(
                        appConfig.readAppProperty( AppProperty.LOGGING_PACKAGE_LIST ), "," ) );

        return PwmLogSettings.builder()
                .localDbLevel( appConfig.readSettingAsEnum( PwmSetting.EVENTS_LOCALDB_LOG_LEVEL, PwmLogLevel.class ) )
                .fileLevel( appConfig.readSettingAsEnum( PwmSetting.EVENTS_FILE_LEVEL, PwmLogLevel.class ) )
                .stdoutLevel( appConfig.readSettingAsEnum( PwmSetting.EVENTS_JAVA_STDOUT_LEVEL, PwmLogLevel.class ) )
                .healthLoggingEnabled( healthLoggingEnabled )
                .runtimeLoggingEnabled( runtimeLoggingEnabled )
                .logOutputMode( logOutputMode )
                .loggingPackages( loggingPackages )
                .build();
    }

    PwmLogLevel calculateLowestLevel()
    {
        return PwmLogLevel.lowestLevel( List.of( localDbLevel, stdoutLevel, fileLevel ) );
    }
}
