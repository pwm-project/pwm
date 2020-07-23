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

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

class WordlistInspector implements Runnable
{
    private final AbstractWordlist rootWordlist;
    private final PwmApplication pwmApplication;
    private final BooleanSupplier cancelFlag;

    WordlistInspector(
            final PwmApplication pwmApplication,
            final AbstractWordlist rootWordlist,
            final BooleanSupplier cancelFlag
    )
    {
        this.pwmApplication = pwmApplication;
        this.rootWordlist = rootWordlist;
        this.cancelFlag = cancelFlag;
    }

    @Override
    public void run()
    {
        try
        {
            checkPopulation();
        }
        catch ( final Exception e )
        {
            getLogger().error( () -> "unexpected error running population worker: " + e.getMessage(), e );
        }
    }

    private void checkPopulation( )
            throws Exception
    {
        if ( cancelFlag.getAsBoolean() )
        {
            return;
        }

        rootWordlist.setActivity( Wordlist.Activity.ReadingWordlistFile );
        final boolean autoImportUrlConfigured = !StringUtil.isEmpty( rootWordlist.getConfiguration().getAutoImportUrl() );
        WordlistStatus existingStatus = rootWordlist.readWordlistStatus();

        if ( checkIfClearIsNeeded( existingStatus, autoImportUrlConfigured ) )
        {
            rootWordlist.clearImpl( Wordlist.Activity.ReadingWordlistFile );
        }

        existingStatus = rootWordlist.readWordlistStatus();

        if ( checkIfExistingOkay( existingStatus, autoImportUrlConfigured ) )
        {
            return;
        }

        if ( cancelFlag.getAsBoolean() )
        {
            return;
        }

        if ( autoImportUrlConfigured )
        {
            try
            {
                checkAutoPopulation( existingStatus );
            }
            catch ( final PwmUnrecoverableException e )
            {
                getLogger().error( () -> "error importing auto-import wordlist: " + e.getMessage() );
                rootWordlist.setAutoImportError( e.getErrorInformation() );
            }
        }

        existingStatus = rootWordlist.readWordlistStatus();

        if ( cancelFlag.getAsBoolean() )
        {
            return;
        }

        boolean needsBuiltInPopulation = false;

        if ( autoImportUrlConfigured
                && rootWordlist.getAutoImportError() != null
                && !existingStatus.isCompleted()
        )
        {
            getLogger().debug( () -> "auto-import did not complete and failed with an error, will (temporarily) import built-in wordlist." );
            needsBuiltInPopulation = true;
        }
        else if ( !autoImportUrlConfigured )
        {
            final WordlistSource source = WordlistSource.forBuiltIn( pwmApplication, rootWordlist.getConfiguration() );

            if ( existingStatus.getSourceType() != WordlistSourceType.Temporary_BuiltIn )
            {
                getLogger().debug( () -> "auto-import is not configured, and existing wordlist is not of type BuiltIn, will reload." );
                needsBuiltInPopulation = true;
            }
            else if ( !existingStatus.isCompleted() )
            {
                getLogger().debug( () -> "existing built-in store was not completed, will re-import" );
                needsBuiltInPopulation = true;
            }
            else
            {
                final WordlistSourceInfo builtInInfo = source.readRemoteWordlistInfo( pwmApplication, cancelFlag, getLogger() );
                if ( !builtInInfo.equals( existingStatus.getRemoteInfo() ) )
                {
                    getLogger().debug( () -> "existing built-in store does not match imported wordlist, will re-import" );
                    needsBuiltInPopulation = true;
                }
            }
        }

        if ( cancelFlag.getAsBoolean() )
        {
            return;
        }

        if ( needsBuiltInPopulation )
        {
            populateBuiltIn( autoImportUrlConfigured ? WordlistSourceType.Temporary_BuiltIn : WordlistSourceType.BuiltIn );
        }
    }

    private boolean checkIfClearIsNeeded(
            final WordlistStatus wordlistStatus,
            final boolean autoImportUrlConfigured
    )
    {
        if ( wordlistStatus == null || wordlistStatus.getSourceType() == null )
        {
            return true;
        }

        if ( wordlistStatus.getVersion() != WordlistStatus.CURRENT_VERSION )
        {
            getLogger().debug( () -> "stored version '" + wordlistStatus.getVersion() + "' is not current version '"
                    + WordlistStatus.CURRENT_VERSION + "', will clear" );
            return true;
        }

        if ( !Objects.equals( wordlistStatus.getConfigHash(), rootWordlist.getConfiguration().configHash() ) )
        {
            getLogger().debug( () -> "stored configuration hash '" + wordlistStatus.getConfigHash()
                    + "' does not match current configuration hash '"
                    + rootWordlist.getConfiguration().configHash() + "', will clear" );
            return true;
        }

        switch ( wordlistStatus.getSourceType() )
        {
            case AutoImport:
            {
                if ( !autoImportUrlConfigured )
                {
                    getLogger().debug( () -> "existing stored list is AutoImport but auto-import is not configured, will clear" );
                    return true;
                }

                final String storedImportUrl = wordlistStatus.getRemoteInfo().getImportUrl();
                final String configuredUrl = rootWordlist.getConfiguration().getAutoImportUrl();
                if ( !StringUtil.nullSafeEquals( storedImportUrl, configuredUrl ) )
                {
                    getLogger().debug( () -> "auto import url has been modified since import, will clear" );
                    return true;
                }
            }
            break;

            case BuiltIn:
            {
                if ( autoImportUrlConfigured )
                {
                    return false;
                }
            }
            break;

            case Temporary_BuiltIn:
            {

                if ( autoImportUrlConfigured )
                {
                    return false;
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
            throws PwmUnrecoverableException
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

            case Temporary_BuiltIn:
            {
                final WordlistSource testWordlistSource = WordlistSource.forAutoImport( pwmApplication, rootWordlist.getConfiguration() );
                try
                {
                    testWordlistSource.readRemoteWordlistInfo( pwmApplication, cancelFlag, getLogger() );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    rootWordlist.setAutoImportError( e.getErrorInformation() );
                    getLogger().debug( () -> "existing stored list is not type AutoImport but auto-import is configured"
                            + ", however auto-import returns error so will keep existing built-in wordlist; error: " + e.getMessage() );
                    return true;
                }
                getLogger().debug( () -> "existing stored list is not type AutoImport but auto-import is configured, will clear" );
                rootWordlist.clearImpl( Wordlist.Activity.ReadingWordlistFile );
            }
            break;

            case AutoImport:
            {
                final Instant checkTime = wordlistStatus.getCheckDate() == null ? Instant.EPOCH : wordlistStatus.getCheckDate();
                final TimeDuration timeSinceCheck = TimeDuration.fromCurrent( checkTime );
                final TimeDuration recheckDuration = rootWordlist.getConfiguration().getAutoImportRecheckDuration();
                if ( wordlistStatus.isCompleted() && timeSinceCheck.isShorterThan( recheckDuration ) && autoImportUrlConfigured )
                {
                    /*
                    getLogger().debug( "existing completed wordlist is "
                            + timeSinceCompletion.asCompactString() + " old, which is less than recheck interval of "
                            + recheckDuration.asCompactString() + ", skipping recheck" );
                     */
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
        final WordlistSource source = WordlistSource.forAutoImport( pwmApplication, rootWordlist.getConfiguration() );
        final WordlistSourceInfo remoteInfo = source.readRemoteWordlistInfo( pwmApplication, cancelFlag, getLogger() );

        boolean needsAutoImport = false;
        if ( remoteInfo == null )
        {
            getLogger().warn( () -> "can't read remote wordlist data from url " + rootWordlist.getConfiguration().getAutoImportUrl() );
        }
        else
        {
            if ( !remoteInfo.equals( existingStatus.getRemoteInfo() ) )
            {
                getLogger().debug( () -> "auto-import url remote hash does not equal currently stored hash, will start auto-import" );
                needsAutoImport = true;
            }
            else if ( !existingStatus.isCompleted() )
            {
                getLogger().debug( () -> "auto-import did not previously complete, will continue previous import" );
                needsAutoImport = true;
            }

            if ( needsAutoImport )
            {
                populateAutoImport( remoteInfo );
            }
        }

        rootWordlist.writeWordlistStatus(
                rootWordlist.readWordlistStatus().toBuilder()
                        .checkDate( Instant.now() ).build() );
    }

    private void populateBuiltIn( final WordlistSourceType wordlistSourceType )
            throws IOException, PwmUnrecoverableException
    {
        final WordlistSource wordlistSource = WordlistSource.forBuiltIn( pwmApplication, rootWordlist.getConfiguration() );
        final WordlistSourceInfo wordlistSourceInfo = wordlistSource.readRemoteWordlistInfo( pwmApplication, cancelFlag, getLogger() );
        final WordlistImporter wordlistImporter = new WordlistImporter(
                wordlistSourceInfo,
                wordlistSource.getZipWordlistReader(),
                wordlistSourceType,
                rootWordlist,
                cancelFlag );
        wordlistImporter.run();
    }


    private void populateAutoImport( final WordlistSourceInfo wordlistSourceInfo )
            throws IOException, PwmUnrecoverableException
    {
        rootWordlist.setAutoImportError( null );
        final WordlistSource wordlistSource = WordlistSource.forAutoImport( pwmApplication, rootWordlist.getConfiguration() );
        final WordlistImporter wordlistImporter = new WordlistImporter(
                wordlistSourceInfo,
                wordlistSource.getZipWordlistReader(),
                WordlistSourceType.AutoImport,
                rootWordlist,
                cancelFlag );
        wordlistImporter.run();
        rootWordlist.setAutoImportError( wordlistImporter.getExitError() );
    }

    private PwmLogger getLogger()
    {
        return this.rootWordlist.getLogger();
    }
}
