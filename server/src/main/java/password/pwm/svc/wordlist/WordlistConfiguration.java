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

package password.pwm.svc.wordlist;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import password.pwm.AppAttribute;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
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

    @Builder.Default
    private final Collection<String> commentPrefixes = new ArrayList<>();

    private final TimeDuration autoImportRecheckDuration;
    private final TimeDuration importDurationGoal;
    private final int importMinTransactions;
    private final int importMaxTransactions;
    private final long importMaxChars;
    private final long importMinFreeSpace;

    private final TimeDuration inspectorFrequency;

    static WordlistConfiguration fromConfiguration(
            final Configuration configuration,
            final WordlistType type
    )
    {
        switch ( type )
        {
            case SEEDLIST:
            {
                return commonBuilder( configuration, type ).toBuilder()
                        .autoImportUrl( readAutoImportUrl( configuration, PwmSetting.SEEDLIST_FILENAME ) )
                        .metaDataAppAttribute( AppAttribute.SEEDLIST_METADATA )
                        .builtInWordlistLocationProperty( AppProperty.SEEDLIST_BUILTIN_PATH )
                        .db( LocalDB.DB.SEEDLIST_WORDS )
                        .wordlistFilenameSetting( PwmSetting.SEEDLIST_FILENAME )
                        .build();
            }

            case WORDLIST:
            {
                return commonBuilder( configuration, type ).toBuilder()
                        .caseSensitive( configuration.readSettingAsBoolean( PwmSetting.WORDLIST_CASE_SENSITIVE )  )
                        .checkSize( (int) configuration.readSettingAsLong( PwmSetting.PASSWORD_WORDLIST_WORDSIZE ) )
                        .autoImportUrl( readAutoImportUrl( configuration, PwmSetting.WORDLIST_FILENAME ) )
                        .metaDataAppAttribute( AppAttribute.WORDLIST_METADATA )
                        .builtInWordlistLocationProperty( AppProperty.WORDLIST_BUILTIN_PATH )
                        .db( LocalDB.DB.WORDLIST_WORDS )
                        .wordlistFilenameSetting( PwmSetting.WORDLIST_FILENAME )
                        .build();
            }

            default:
                JavaHelper.unhandledSwitchStatement( type );
        }

        throw new IllegalStateException( "unreachable switch statement" );
    }

    private static WordlistConfiguration commonBuilder(
            final Configuration configuration,
            final WordlistType type
    )
    {
        return WordlistConfiguration.builder()
                .commentPrefixes( StringUtil.splitAndTrim( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_LINE_COMMENTS ), ";;;" ) )
                .testMode( Boolean.parseBoolean( configuration.readAppProperty( AppProperty.WORDLIST_TEST_MODE ) ) )
                .minWordSize( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MIN ) ) )
                .maxWordSize( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MAX ) ) )
                .autoImportRecheckDuration( TimeDuration.of(
                        Long.parseLong( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_AUTO_IMPORT_RECHECK_SECONDS ) ),
                        TimeDuration.Unit.SECONDS ) )
                .importDurationGoal( TimeDuration.of(
                        Long.parseLong( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_DURATION_GOAL_MS ) ),
                        TimeDuration.Unit.MILLISECONDS ) )
                .importMinTransactions( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_MIN_TRANSACTIONS ) ) )
                .importMaxTransactions( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_MAX_TRANSACTIONS ) ) )
                .importMaxChars( JavaHelper.silentParseLong( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_MAX_CHARS_TRANSACTIONS ), 10_1024_1024 ) )
                .inspectorFrequency( TimeDuration.of(
                        Long.parseLong( configuration.readAppProperty( AppProperty.WORDLIST_INSPECTOR_FREQUENCY_SECONDS ) ),
                        TimeDuration.Unit.SECONDS ) )
                .importMinFreeSpace( JavaHelper.silentParseLong( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_MIN_FREE_SPACE ), 100_000_000 ) )
                .build();
    }

    private static String readAutoImportUrl(
            final Configuration configuration,
            final PwmSetting wordlistFileSetting
    )
    {
        final String inputUrl = configuration.readSettingAsString( wordlistFileSetting );

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
    private final transient Supplier<String> configHash = new LazySupplier<>( () ->
    {
        try
        {
            return SecureEngine.hash( JsonUtil.serialize( WordlistConfiguration.this ), HASH_ALGORITHM );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new IllegalStateException( "unexpected error generating wordlist-config hash: " + e.getMessage() );
        }
    } );

    String configHash( )
    {
        return configHash.get();
    }

}
