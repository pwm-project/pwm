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

package password.pwm.util.db;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.FileValue;
import password.pwm.http.bean.ImmutableByteArray;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@Value
@AllArgsConstructor( access = AccessLevel.PRIVATE )
public class DBConfiguration implements Serializable
{
    private final String driverClassname;
    private final String connectionString;
    private final String username;
    private final PasswordData password;
    private final String columnTypeKey;
    private final String columnTypeValue;
    private final ImmutableByteArray jdbcDriver;
    private final Set<JDBCDriverLoader.ClassLoaderStrategy> classLoaderStrategies;
    private final int maxConnections;
    private final int connectionTimeout;
    private final int keyColumnLength;
    private final boolean failOnIndexCreation;

    public ImmutableByteArray getJdbcDriver( )
    {
        return jdbcDriver;
    }

    public boolean isEnabled( )
    {
        return
                !StringUtil.isEmpty( driverClassname )
                        && !StringUtil.isEmpty( connectionString )
                        && !StringUtil.isEmpty( username )
                        && !( password == null );
    }

    static DBConfiguration fromConfiguration( final Configuration config )
    {
        final Map<FileValue.FileInformation, FileValue.FileContent> fileValue = config.readSettingAsFile(
                PwmSetting.DATABASE_JDBC_DRIVER );
        final ImmutableByteArray jdbcDriverBytes;
        if ( fileValue != null && !fileValue.isEmpty() )
        {
            final FileValue.FileContent fileContent = fileValue.values().iterator().next();
            jdbcDriverBytes = fileContent.getContents();
        }
        else
        {
            jdbcDriverBytes = null;
        }

        final String strategyList = config.readAppProperty( AppProperty.DB_JDBC_LOAD_STRATEGY );
        final Set<JDBCDriverLoader.ClassLoaderStrategy> strategies = JavaHelper.readEnumSetFromStringCollection(
                JDBCDriverLoader.ClassLoaderStrategy.class,
                Arrays.asList( strategyList.split( "," ) )
        );

        final int maxConnections = Integer.parseInt( config.readAppProperty( AppProperty.DB_CONNECTIONS_MAX ) );
        final int connectionTimeout = Integer.parseInt( config.readAppProperty( AppProperty.DB_CONNECTIONS_TIMEOUT_MS ) );

        final int keyColumnLength = Integer.parseInt( config.readAppProperty( AppProperty.DB_SCHEMA_KEY_LENGTH ) );

        final boolean haltOnIndexCreateError = Boolean.parseBoolean( config.readAppProperty( AppProperty.DB_INIT_HALT_ON_INDEX_CREATE_ERROR ) );

        return new DBConfiguration(
                config.readSettingAsString( PwmSetting.DATABASE_CLASS ),
                config.readSettingAsString( PwmSetting.DATABASE_URL ),
                config.readSettingAsString( PwmSetting.DATABASE_USERNAME ),
                config.readSettingAsPassword( PwmSetting.DATABASE_PASSWORD ),
                config.readSettingAsString( PwmSetting.DATABASE_COLUMN_TYPE_KEY ),
                config.readSettingAsString( PwmSetting.DATABASE_COLUMN_TYPE_VALUE ),
                jdbcDriverBytes,
                strategies,
                maxConnections,
                connectionTimeout,
                keyColumnLength,
                haltOnIndexCreateError
        );
    }
}
