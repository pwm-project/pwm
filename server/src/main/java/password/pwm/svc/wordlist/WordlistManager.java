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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * @author Jason D. Rivard
 */
public class WordlistManager extends AbstractWordlist implements Wordlist
{

    public WordlistManager( )
    {
        logger = PwmLogger.forClass( WordlistManager.class );
    }


    protected Map<String, String> getWriteTxnForValue( final String value )
    {
        final Map<String, String> returnSet = new TreeMap<>();
        final Set<String> chunkedWords = chunkWord( value, this.wordlistConfiguration.getCheckSize() );
        for ( final String word : chunkedWords )
        {
            returnSet.put( word, "" );
        }
        return returnSet;
    }

    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        super.init( pwmApplication );
        final boolean caseSensitive = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.WORDLIST_CASE_SENSITIVE );
        final int checkSize = ( int ) pwmApplication.getConfig().readSettingAsLong( PwmSetting.PASSWORD_WORDLIST_WORDSIZE );
        final String wordlistUrl = readAutoImportUrl();

        final int minSize = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MIN ) );
        final int maxSize = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.WORDLIST_CHAR_LENGTH_MAX ) );

        this.wordlistConfiguration = new WordlistConfiguration( caseSensitive, checkSize, wordlistUrl, minSize, maxSize );
        this.debugLabel = PwmConstants.PWM_APP_NAME + "-Wordlist";
        backgroundStartup();
    }

    @Override
    protected PwmApplication.AppAttribute getMetaDataAppAttribute( )
    {
        return PwmApplication.AppAttribute.WORDLIST_METADATA;
    }

    @Override
    protected PwmSetting getWordlistFileSetting( )
    {
        return PwmSetting.WORDLIST_FILENAME;
    }

    @Override
    protected LocalDB.DB getWordlistDB( )
    {
        return LocalDB.DB.WORDLIST_WORDS;
    }

    @Override
    protected AppProperty getBuiltInWordlistLocationProperty( )
    {
        return AppProperty.WORDLIST_BUILTIN_PATH;
    }


}
