/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.svc.wordlist;

import lombok.Builder;
import lombok.Getter;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;

import java.io.Serializable;

@Getter
@Builder
public class WordlistConfiguration implements Serializable
{
    private final boolean caseSensitive;
    private final int checkSize;
    private final String autoImportUrl;
    private final int minSize;
    private final int maxSize;
    private final PwmApplication.AppAttribute metaDataAppAttribute;
    private final AppProperty builtInWordlistLocationProperty;
    private final LocalDB.DB db;
    private final PwmSetting wordlistFilenameSetting;

    private final TimeDuration autoImportRecheckDuration;
    private final TimeDuration importDurationGoal;
    private final int importMinTransactionGoal;
    private final int importMaxTransactionGoal;

    static WordlistConfiguration fromConfiguration(
            final Configuration configuration,
            final WordlistType type
    )
    {
        switch ( type )
        {
            case SEEDLIST:
            {
                return WordlistConfiguration.builder()
                        .autoImportUrl( readAutoImportUrl( configuration, PwmSetting.SEEDLIST_FILENAME ) )
                        .minSize( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MIN ) ) )
                        .maxSize( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MAX ) ) )
                        .metaDataAppAttribute( PwmApplication.AppAttribute.SEEDLIST_METADATA )
                        .builtInWordlistLocationProperty( AppProperty.SEEDLIST_BUILTIN_PATH )
                        .db( LocalDB.DB.SEEDLIST_WORDS )
                        .wordlistFilenameSetting( PwmSetting.SEEDLIST_FILENAME )

                        .minSize( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MIN ) ) )
                        .maxSize( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MAX ) ) )
                        .autoImportRecheckDuration( TimeDuration.of(
                                Long.parseLong( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_AUTO_IMPORT_RECHECK_SECONDS ) ),
                                TimeDuration.Unit.SECONDS ) )
                        .importDurationGoal( TimeDuration.of(
                                Long.parseLong( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_DURATION_GOAL_MS ) ),
                                TimeDuration.Unit.MILLISECONDS ) )
                        .importMinTransactionGoal( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_MIN_TRANSACTION_GOAL ) ) )
                        .importMaxTransactionGoal( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_MAX_TRANSACTION_GOAL ) ) )

                        .build();
            }

            case WORDLIST:
            {
                return WordlistConfiguration.builder()
                        .caseSensitive( configuration.readSettingAsBoolean( PwmSetting.WORDLIST_CASE_SENSITIVE )  )
                        .checkSize( (int) configuration.readSettingAsLong( PwmSetting.PASSWORD_WORDLIST_WORDSIZE ) )
                        .autoImportUrl( readAutoImportUrl( configuration, PwmSetting.WORDLIST_FILENAME ) )
                        .metaDataAppAttribute( PwmApplication.AppAttribute.WORDLIST_METADATA )
                        .builtInWordlistLocationProperty( AppProperty.WORDLIST_BUILTIN_PATH )
                        .db( LocalDB.DB.WORDLIST_WORDS )
                        .wordlistFilenameSetting( PwmSetting.WORDLIST_FILENAME )

                        .minSize( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MIN ) ) )
                        .maxSize( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MAX ) ) )
                        .autoImportRecheckDuration( TimeDuration.of(
                                Long.parseLong( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_AUTO_IMPORT_RECHECK_SECONDS ) ),
                                TimeDuration.Unit.SECONDS ) )
                        .importDurationGoal( TimeDuration.of(
                                Long.parseLong( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_DURATION_GOAL_MS ) ),
                                TimeDuration.Unit.MILLISECONDS ) )
                        .importMinTransactionGoal( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_MIN_TRANSACTION_GOAL ) ) )
                        .importMaxTransactionGoal( Integer.parseInt( configuration.readAppProperty( AppProperty.WORDLIST_IMPORT_MAX_TRANSACTION_GOAL ) ) )

                        .build();
            }

            default:
                JavaHelper.unhandledSwitchStatement( type );
        }

        throw new IllegalStateException( "unreachable switch statement" );
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
}
