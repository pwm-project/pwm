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

package password.pwm;

public enum AppAttribute
{
    INSTANCE_ID( "context_instanceID" ),
    INSTALL_DATE( "DB_KEY_INSTALL_DATE" ),
    CONFIG_HASH( "configurationSettingHash" ),
    LAST_LDAP_ERROR( "lastLdapError" ),
    // TOKEN_COUNTER( "tokenCounter" ), deprecated
    REPORT_STATUS( "reporting.status" ),
    // REPORT_CLEAN_FLAG("reporting.cleanFlag"), deprecated
    SMS_ITEM_COUNTER( "smsQueue.itemCount" ),
    EMAIL_ITEM_COUNTER( "itemQueue.itemCount" ),
    LOCALDB_IMPORT_STATUS( "localDB.import.status" ),
    WORDLIST_METADATA( "wordlist.metadata" ),
    SEEDLIST_METADATA( "seedlist.metadata" ),
    HTTPS_SELF_CERT( "https.selfCert" ),
    CONFIG_LOGIN_HISTORY( "config.loginHistory" ),
    LOCALDB_LOGGER_STORAGE_FORMAT( "localdb.logger.storage.format" ),

    TELEMETRY_LAST_PUBLISH_TIMESTAMP( "telemetry.lastPublish.timestamp" );

    private final String key;

    AppAttribute( final String key )
    {
        this.key = key;
    }

    public String getKey( )
    {
        return key;
    }
}
