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

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.time.Instant;
import java.util.function.BooleanSupplier;

class WordlistInspector implements Runnable
{
    private final AbstractWordlist abstractWordlist;
    private final PwmApplication pwmApplication;
    private final BooleanSupplier cancelFlag;

    WordlistInspector(
            final PwmApplication pwmApplication,
            final AbstractWordlist abstractWordlist,
            final BooleanSupplier cancelFlag
    )
    {
        this.pwmApplication = pwmApplication;
        this.abstractWordlist = abstractWordlist;
        this.cancelFlag = cancelFlag;
    }

    @Override
    public void run()
    {
        try
        {
            checkPopulation();
        }
        catch ( Exception e )
        {
            getLogger().error( "unexpected error running population worker: " + e.getMessage(), e );
        }
    }

    private void checkPopulation( )
            throws Exception
    {
        final boolean autoImportUrlConfigured = !StringUtil.isEmpty( abstractWordlist.getConfiguration().getAutoImportUrl() );
        WordlistStatus existingStatus = abstractWordlist.readWordlistStatus();

        if ( checkIfClearIsNeeded( existingStatus, autoImportUrlConfigured ) )
        {
            abstractWordlist.clearImpl();
        }

        existingStatus = abstractWordlist.readWordlistStatus();

        if ( checkIfExistingOkay( existingStatus, autoImportUrlConfigured ) )
        {
            return;
        }

        checkAutoPopulation( existingStatus );

        existingStatus = abstractWordlist.readWordlistStatus();

        if ( !cancelFlag.getAsBoolean() )
        {
            boolean needsBuiltInPopulation = false;
            if ( existingStatus.getSourceType() == WordlistSourceType.AutoImport
                    && !existingStatus.isCompleted()
                    && abstractWordlist.getAutoImportError() != null )
            {
                getLogger().debug( "auto-import did not complete and failed with an error, will (temporarily) import built-in wordlist." );
                needsBuiltInPopulation = true;
            }
            else if ( !autoImportUrlConfigured )
            {
                final WordlistSource source = WordlistSource.forBuiltIn( pwmApplication, abstractWordlist.getConfiguration() );

                if ( existingStatus.getSourceType() != WordlistSourceType.BuiltIn )
                {
                    getLogger().debug( "auto-import is not configured, and existing wordlist is not of type BuiltIn, will reload." );
                    needsBuiltInPopulation = true;
                }
                else if ( !existingStatus.isCompleted() )
                {
                    getLogger().debug( "existing built-in store was not completed, will re-import" );
                    needsBuiltInPopulation = true;
                }
                else
                {
                    final WordlistSourceInfo builtInInfo = source.readRemoteWordlistInfo( cancelFlag );
                    if ( !builtInInfo.equals( existingStatus.getRemoteInfo() ) )
                    {
                        getLogger().debug( "existing built-in store does not match imported wordlist, will re-import" );
                        needsBuiltInPopulation = true;
                    }
                }
            }

            if ( needsBuiltInPopulation )
            {
                populateBuiltIn();
            }
        }
    }

    private boolean checkIfClearIsNeeded(
            final WordlistStatus wordlistStatus,
            final boolean autoImportUrlConfigured
    )
    {
        if ( wordlistStatus == null || wordlistStatus.getRemoteInfo() == null || wordlistStatus.getSourceType() == null )
        {
            return true;
        }

        if ( wordlistStatus.getVersion() != WordlistStatus.CURRENT_VERSION )
        {
            getLogger().debug( "stored version '" + wordlistStatus.getVersion() + "' is not current version '"
                    + WordlistStatus.CURRENT_VERSION + "', will clear" );
            return true;
        }

        switch ( wordlistStatus.getSourceType() )
        {
            case AutoImport:
            {
                if ( !autoImportUrlConfigured )
                {
                    getLogger().debug( "existing stored list is AutoImport but auto-import is not configured, will clear" );
                    return true;
                }

                final String storedImportUrl = wordlistStatus.getRemoteInfo().getImportUrl();
                final String configuredUrl = abstractWordlist.getConfiguration().getAutoImportUrl();
                if ( !StringUtil.nullSafeEquals( storedImportUrl, configuredUrl ) )
                {
                    getLogger().debug( "auto import url has been modified since import, will clear" );
                    return true;
                }
            }
            break;

            case BuiltIn:
            {
                if ( autoImportUrlConfigured )
                {
                    getLogger().debug( "existing stored list is not type AutoImport but auto-import is configured, will clear" );
                    return true;
                }
            }
            break;

            case User:
            {
                if ( !wordlistStatus.isCompleted() )
                {
                    return true;
                }
            }
            break;

            default:
                return false;
        }
        return false;
    }

    private boolean checkIfExistingOkay(
            final WordlistStatus wordlistStatus,
            final boolean autoImportUrlConfigured
    )
    {
        if ( wordlistStatus.getSourceType() == null )
        {
            return false;
        }

        switch ( wordlistStatus.getSourceType() )
        {
            case User:
            {
                if ( wordlistStatus.isCompleted() )
                {
                    return true;
                }
            }
            break;


            case BuiltIn:
            {
                if ( wordlistStatus.isCompleted() && wordlistStatus.getVersion() == WordlistStatus.CURRENT_VERSION && !autoImportUrlConfigured )
                {
                    return true;
                }
            }
            break;

            case AutoImport:
            {
                final Instant storageTime = wordlistStatus.getStoreDate();
                final TimeDuration timeSinceCompletion = TimeDuration.fromCurrent( storageTime );
                final TimeDuration recheckDuration = abstractWordlist.getConfiguration().getAutoImportRecheckDuration();
                if ( wordlistStatus.isCompleted() && timeSinceCompletion.isShorterThan( recheckDuration ) && autoImportUrlConfigured )
                {
                    getLogger().debug( "existing completed wordlist is "
                            + timeSinceCompletion.asCompactString() + " old, which is less than recheck interval of "
                            + recheckDuration.asCompactString() + ", skipping recheck" );
                    return true;
                }
            }
            break;

            default:
                return false;
        }

        return false;
    }

    private void checkAutoPopulation(
            final WordlistStatus existingStatus
    )
            throws IOException, PwmUnrecoverableException
    {
        final WordlistSource source = WordlistSource.forAutoImport( pwmApplication, abstractWordlist.getConfiguration() );
        final WordlistSourceInfo remoteInfo = source.readRemoteWordlistInfo( cancelFlag );

        boolean needsAutoImport = false;
        if ( remoteInfo == null )
        {
            getLogger().warn( "can't read remote wordlist data from url " + abstractWordlist.getConfiguration().getAutoImportUrl() );
        }
        else
        {
            if ( existingStatus.getSourceType() != WordlistSourceType.AutoImport )
            {
                getLogger().debug( "current stored wordlist is from " + existingStatus.getSourceType() + " and auto-import wordlist is configured, will import" );
                needsAutoImport = true;
            }
            else
            {
                if ( !remoteInfo.equals( existingStatus.getRemoteInfo() ) )
                {
                    getLogger().debug( "auto-import url remote hash does not equal currently stored hash, will start auto-import" );
                    needsAutoImport = true;
                }
                else if ( remoteInfo.getBytes() > existingStatus.getBytes() || !existingStatus.isCompleted() )
                {
                    getLogger().debug( "auto-import did not previously complete, will continue previous import" );
                    needsAutoImport = true;
                }
            }

            if ( needsAutoImport )
            {
                populateAutoImport( remoteInfo );
            }
        }
    }

    private void populateBuiltIn()
            throws IOException, PwmUnrecoverableException
    {
        final WordlistSource wordlistSource = WordlistSource.forBuiltIn( pwmApplication, abstractWordlist.getConfiguration() );
        final WordlistSourceInfo wordlistSourceInfo = wordlistSource.readRemoteWordlistInfo( cancelFlag );
        final WordlistImporter wordlistImporter = new WordlistImporter(
                wordlistSourceInfo,
                wordlistSource.getZipWordlistReader(),
                WordlistSourceType.BuiltIn,
                abstractWordlist,
                cancelFlag );
        wordlistImporter.run();
    }


    private void populateAutoImport( final WordlistSourceInfo wordlistSourceInfo )
            throws IOException, PwmUnrecoverableException
    {
        abstractWordlist.setAutoImportError( null );
        final WordlistSource wordlistSource = WordlistSource.forAutoImport( pwmApplication, abstractWordlist.getConfiguration() );
        final WordlistImporter wordlistImporter = new WordlistImporter(
                wordlistSourceInfo,
                wordlistSource.getZipWordlistReader(),
                WordlistSourceType.AutoImport,
                abstractWordlist,
                cancelFlag );
        wordlistImporter.run();
        abstractWordlist.setAutoImportError( wordlistImporter.getExitError() );
    }

    private PwmLogger getLogger()
    {
        return this.abstractWordlist.getLogger();
    }


    boolean needsRunningAgain()
    {
        final WordlistStatus wordlistStatus = abstractWordlist.readWordlistStatus();

        if ( wordlistStatus.isCompleted() )
        {
            return false;
        }

        return true;
    }
}
