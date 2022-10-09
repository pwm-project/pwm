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

package password.pwm.svc.wordlist;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import password.pwm.AppAttribute;
import password.pwm.AppProperty;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

@Value
@Builder( toBuilder = true )
public class WordlistConfiguration implements Serializable
{
    private static final long serialVersionUID = 1L;

    static final int STREAM_BUFFER_SIZE = 1_1024_1024;
    static final PwmHashAlgorithm HASH_ALGORITHM = PwmHashAlgorithm.SHA256;

    private final boolean caseSensitive;
    private final int checkSize;
    private final String autoImportUrl;
    private final int minWordSize;
    private final int maxWordSize;
    private final AppAttribute metaDataAppAttribute;
    private final AppProperty builtInWordlistLocationProperty;
    private final LocalDB.DB db;
    private final PwmSetting wordlistFilenameSetting;
    private final boolean testMode;
    private final int warmupLookups;

    @Builder.Default
    private final Collection<String> commentPrefixes = new ArrayList<>();

    private final TimeDuration autoImportRecheckDuration;
    private final TimeDuration importDurationGoal;
    private final TimeDuration bucketCheckLogWarningTimeout;

    private final TimeDuration importPauseDuration;
    private final TimeDuration importPauseFrequency;

    private final int importMinTransactions;
    private final int importMaxTransactions;
    private final long importMaxChars;
    private final long importMinFreeSpace;

    private final TimeDuration inspectorFrequency;

    static WordlistConfiguration fromConfiguration(
            final AppConfig appConfig,
            final WordlistType type
    )
    {
        switch ( type )
        {
            case WORDLIST:
            {
                return commonBuilder( appConfig ).toBuilder()
                        .caseSensitive( appConfig.readSettingAsBoolean( PwmSetting.WORDLIST_CASE_SENSITIVE )  )
                        .checkSize( (int) appConfig.readSettingAsLong( PwmSetting.PASSWORD_WORDLIST_WORDSIZE ) )
                        .autoImportUrl( readAutoImportUrl( appConfig, PwmSetting.WORDLIST_FILENAME ) )
                        .metaDataAppAttribute( AppAttribute.WORDLIST_METADATA )
                        .builtInWordlistLocationProperty( AppProperty.WORDLIST_BUILTIN_PATH )
                        .db( LocalDB.DB.WORDLIST_WORDS )
                        .wordlistFilenameSetting( PwmSetting.WORDLIST_FILENAME )
                        .build();
            }

            default:
                PwmUtil.unhandledSwitchStatement( type );
        }

        throw new IllegalStateException( "unreachable switch statement" );
    }

    private static WordlistConfiguration commonBuilder(
            final AppConfig appConfig
    )
    {
        return WordlistConfiguration.builder()
                .commentPrefixes( StringUtil.splitAndTrim( appConfig.readAppProperty( AppProperty.WORDLIST_IMPORT_LINE_COMMENTS ), ";;;" ) )
                .testMode( Boolean.parseBoolean( appConfig.readAppProperty( AppProperty.WORDLIST_TEST_MODE ) ) )
                .minWordSize( Integer.parseInt( appConfig.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MIN ) ) )
                .maxWordSize( Integer.parseInt( appConfig.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MAX ) ) )
                .warmupLookups( Integer.parseInt( appConfig.readAppProperty( AppProperty.WORDLIST_WARMUP_COUNT ) ) )
                .bucketCheckLogWarningTimeout( appConfig.readDurationAppProperty( AppProperty.WORDLIST_BUCKET_CHECK_WARNING_TIMEOUT_MS ) )
                .autoImportRecheckDuration( appConfig.readDurationAppProperty( AppProperty.WORDLIST_IMPORT_AUTO_IMPORT_RECHECK_SECONDS ) )
                .importDurationGoal( appConfig.readDurationAppProperty( AppProperty.WORDLIST_IMPORT_DURATION_GOAL_MS ) )
                .importMinTransactions( Integer.parseInt( appConfig.readAppProperty( AppProperty.WORDLIST_IMPORT_MIN_TRANSACTIONS ) ) )
                .importMaxTransactions( Integer.parseInt( appConfig.readAppProperty( AppProperty.WORDLIST_IMPORT_MAX_TRANSACTIONS ) ) )
                .importMaxChars( JavaHelper.silentParseLong( appConfig.readAppProperty( AppProperty.WORDLIST_IMPORT_MAX_CHARS_TRANSACTIONS ), 10_1024_1024 ) )
                .inspectorFrequency( appConfig.readDurationAppProperty( AppProperty.WORDLIST_INSPECTOR_FREQUENCY_SECONDS ) )
                .importMinFreeSpace( JavaHelper.silentParseLong( appConfig.readAppProperty( AppProperty.WORDLIST_IMPORT_MIN_FREE_SPACE ), 100_000_000 ) )
                .importPauseDuration( appConfig.readDurationAppProperty( AppProperty.WORDLIST_IMPORT_PAUSE_DURATION_MS ) )
                .importPauseFrequency( appConfig.readDurationAppProperty( AppProperty.WORDLIST_IMPORT_PAUSE_FREQUENCY_MS ) )
                .build();
    }

    private static String readAutoImportUrl(
            final AppConfig appConfig,
            final PwmSetting wordlistFileSetting
    )
    {
        final String inputUrl = appConfig.readSettingAsString( wordlistFileSetting );

        if ( StringUtil.isEmpty( inputUrl ) )
        {
            return null;
        }

        if ( !inputUrl.startsWith( "http:" ) && !inputUrl.startsWith( "https:" ) && !inputUrl.startsWith( "file:" ) )
        {
            return "file:" + inputUrl;
        }

        return inputUrl;
    }

    @Getter( AccessLevel.PRIVATE )
    private final transient Supplier<String> configHash = LazySupplier.create( () ->
            SecureEngine.hash( JsonFactory.get().serialize( WordlistConfiguration.this ), HASH_ALGORITHM ) );

    public boolean isAutoImportUrlConfigured()
    {
        return StringUtil.notEmpty( getAutoImportUrl() );
    }

    String configHash( )
    {
        return configHash.get();
    }

}
