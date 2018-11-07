package password.pwm.svc.wordlist;

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.time.Instant;
import java.util.function.BooleanSupplier;

class PopulationWorker implements Runnable
{
    private final AbstractWordlist abstractWordlist;
    private final PwmApplication pwmApplication;
    private final BooleanSupplier cancelFlag;

    PopulationWorker(
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

        {
            final WordlistStatus existingStatus = abstractWordlist.readWordlistStatus();

            getLogger().trace( "examining existing wordlist" );

            if ( checkIfExistingOkay( existingStatus ) )
            {
                return;
            }

            if ( autoImportUrlConfigured )
            {
                checkAutoPopulation( existingStatus );
            }
        }

        if ( !cancelFlag.getAsBoolean() )
        {
            boolean needsBuiltInPopulation = false;
            final WordlistStatus existingStatus = abstractWordlist.readWordlistStatus();
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

    private boolean checkIfExistingOkay( final WordlistStatus wordlistStatus )
    {
        if ( wordlistStatus.isCompleted() && wordlistStatus.getSourceType() == WordlistSourceType.User )
        {
            getLogger().debug( "existing user-imported wordlist will not be updated" );
            return true;
        }


        if ( wordlistStatus.isCompleted() && wordlistStatus.getSourceType() == WordlistSourceType.AutoImport )
        {
            final Instant storeDate = wordlistStatus.getStoreDate();
            final TimeDuration recheckDuration = TimeDuration.of( 3, TimeDuration.Unit.DAYS );
            if ( TimeDuration.fromCurrent( storeDate ).isLongerThan( recheckDuration ) )
            {
                getLogger().debug( "existing auto-imported wordlist is older than " + recheckDuration.asCompactString() + ", will ignore" );
                return true;
            }
        }

        return false;
    }

    private void checkAutoPopulation( final WordlistStatus existingStatus ) throws IOException, PwmUnrecoverableException
    {
            final WordlistSource source = WordlistSource.forAutoImport( pwmApplication, abstractWordlist.getConfiguration() );
            final WordlistSourceInfo remoteInfo = source.readRemoteWordlistInfo( cancelFlag );

            boolean needsAutoImport = false;
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
}
