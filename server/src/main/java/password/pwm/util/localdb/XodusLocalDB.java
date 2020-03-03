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

package password.pwm.util.localdb;

import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.InvalidSettingException;
import jetbrains.exodus.bindings.StringBinding;
import jetbrains.exodus.env.Cursor;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.EnvironmentStatistics;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.StoreConfig;
import jetbrains.exodus.env.Transaction;
import jetbrains.exodus.management.Statistics;
import jetbrains.exodus.management.StatisticsItem;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;


public class XodusLocalDB implements LocalDBProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( XodusLocalDB.class );
    private static final TimeDuration STATS_OUTPUT_INTERVAL = TimeDuration.DAY;

    private static final String FILE_SUB_PATH = "xodus";
    private static final String README_FILENAME = "README.TXT";

    private Environment environment;
    private File fileLocation;
    private boolean readOnly;

    private enum Property
    {
        Compression_Enabled( "xodus.compression.enabled" ),
        Compression_MinLength( "xodus.compression.minLength" ),;

        private final String keyName;

        Property( final String keyName )
        {
            this.keyName = keyName;
        }

        public String getKeyName( )
        {
            return keyName;
        }
    }

    private LocalDB.Status status = LocalDB.Status.NEW;

    private final Map<LocalDB.DB, Store> cachedStoreObjects = new HashMap<>();

    private final ConditionalTaskExecutor outputLogExecutor = new ConditionalTaskExecutor(
            ( ) -> outputStats(), new ConditionalTaskExecutor.TimeDurationPredicate( STATS_OUTPUT_INTERVAL ).setNextTimeFromNow( TimeDuration.MINUTE )
    );

    private BindMachine bindMachine = new BindMachine( BindMachine.DEFAULT_ENABLE_COMPRESSION, BindMachine.DEFAULT_MIN_COMPRESSION_LENGTH );


    @Override
    public void init(
            final File dbDirectory,
            final Map<String, String> initParameters,
            final Map<Parameter, String> parameters
    )
            throws LocalDBException
    {
        this.fileLocation = dbDirectory;

        LOGGER.trace( () -> "begin environment open" );
        final Instant startTime = Instant.now();

        final EnvironmentConfig environmentConfig = makeEnvironmentConfig( initParameters );

        if ( Files.exists( getDirtyFile().toPath() ) )
        {
            environmentConfig.setGcUtilizationFromScratch( true );
            LOGGER.warn( () -> "environment not closed cleanly, will re-calculate GC" );
        }
        else
        {
            LOGGER.debug( () -> "environment was closed cleanly" );
        }

        try
        {
            if ( !getDirtyFile().exists() )
            {
                Files.createFile( getDirtyFile().toPath() );
                LOGGER.trace( () -> "created openLock file" );
            }
        }
        catch ( final IOException e )
        {
            LOGGER.error( () -> "error creating openLock file: " + e.getMessage() );
        }

        {
            final boolean compressionEnabled = initParameters.containsKey( Property.Compression_Enabled.getKeyName() )
                    ? Boolean.parseBoolean( initParameters.get( Property.Compression_Enabled.getKeyName() ) )
                    : BindMachine.DEFAULT_ENABLE_COMPRESSION;

            final int compressionMinLength = initParameters.containsKey( Property.Compression_MinLength.getKeyName() )
                    ? Integer.parseInt( initParameters.get( Property.Compression_MinLength.getKeyName() ) )
                    : BindMachine.DEFAULT_MIN_COMPRESSION_LENGTH;

            bindMachine = new BindMachine( compressionEnabled, compressionMinLength );
        }

        readOnly = parameters.containsKey( Parameter.readOnly ) && Boolean.parseBoolean( parameters.get( Parameter.readOnly ) );

        LOGGER.trace( () -> "preparing to open with configuration " + JsonUtil.serializeMap( environmentConfig.getSettings() ) );
        environment = Environments.newInstance( dbDirectory.getAbsolutePath() + File.separator + FILE_SUB_PATH, environmentConfig );

        LOGGER.trace( () -> "environment open (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );

        environment.executeInTransaction( txn ->
        {
            for ( final LocalDB.DB db : LocalDB.DB.values() )
            {
                final Store store = initStore( db, txn );
                cachedStoreObjects.put( db, store );
            }
        } );

        status = LocalDB.Status.OPEN;

        for ( final LocalDB.DB db : LocalDB.DB.values() )
        {
            final long finalSize = this.size( db );
            LOGGER.trace( () -> "opened " + db + " with " + finalSize + " records" );
        }

        outputReadme( new File( dbDirectory.getPath() + File.separator + FILE_SUB_PATH + File.separator + README_FILENAME ) );
    }

    @Override
    public void close( ) throws LocalDBException
    {
        final Instant startTime = Instant.now();
        if ( environment != null && environment.isOpen() )
        {
            environment.close();
        }

        try
        {
            Files.deleteIfExists( getDirtyFile().toPath() );
            LOGGER.trace( () -> "deleted openLock file" );
        }
        catch ( final IOException e )
        {
            LOGGER.error( () -> "error creating openLock file: " + e.getMessage() );
        }

        status = LocalDB.Status.CLOSED;
        LOGGER.debug( () -> "closed (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
    }

    private EnvironmentConfig makeEnvironmentConfig( final Map<String, String> initParameters )
    {
        final EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setEnvCloseForcedly( true );
        environmentConfig.setMemoryUsage( 50 * 1024 * 1024 );
        environmentConfig.setEnvGatherStatistics( true );

        for ( final Map.Entry<String, String> entry : initParameters.entrySet() )
        {
            final String key = entry.getKey();
            final String value = entry.getValue();
            final Map<String, String> singleMap = Collections.singletonMap( key, value );
            try
            {
                environmentConfig.setSettings( singleMap );
                LOGGER.trace( () -> "set env setting from appProperty: " + key + "=" + value );
            }
            catch ( final InvalidSettingException e )
            {
                LOGGER.warn( () -> "problem setting configured env settings: " + e.getMessage() );
            }
        }

        return environmentConfig;
    }

    @Override
    public long size( final LocalDB.DB db ) throws LocalDBException
    {
        checkStatus( false );
        return environment.computeInReadonlyTransaction( transaction ->
        {
            final Store store = getStore( db );
            return store.count( transaction );
        } );
    }

    @Override
    public boolean contains( final LocalDB.DB db, final String key ) throws LocalDBException
    {
        checkStatus( false );
        return get( db, key ) != null;
    }

    @Override
    public String get( final LocalDB.DB db, final String key ) throws LocalDBException
    {
        checkStatus( false );
        return environment.computeInReadonlyTransaction( transaction ->
        {
            final Store store = getStore( db );
            final ByteIterable returnValue = store.get( transaction, bindMachine.keyToEntry( key ) );
            if ( returnValue != null )
            {
                return bindMachine.entryToValue( returnValue );
            }
            return null;
        } );
    }

    @Override
    public LocalDB.LocalDBIterator<Map.Entry<String, String>> iterator( final LocalDB.DB db )  throws LocalDBException
    {
        return new InnerIterator( db );
    }

    public class InnerIterator implements LocalDB.LocalDBIterator<Map.Entry<String, String>>
    {
        private final Transaction transaction;
        private final Cursor cursor;

        private boolean closed;
        private Map.Entry<String, String> nextValue = null;

        InnerIterator( final LocalDB.DB db )
        {
            this.transaction = environment.beginReadonlyTransaction();
            this.cursor = getStore( db ).openCursor( transaction );
            doNext();
        }

        private void doNext( )
        {
            try
            {
                checkStatus( false );
            }
            catch ( final LocalDBException e )
            {
                throw new IllegalStateException( e );
            }
            try
            {
                if ( closed )
                {
                    return;
                }

                if ( !cursor.getNext() )
                {
                    close();
                    return;
                }
                final ByteIterable nextCursor = cursor.getKey();
                if ( nextCursor == null || nextCursor.getLength() == 0 )
                {
                    close();
                    return;
                }
                final String decodedKey = bindMachine.entryToKey( nextCursor );
                if ( decodedKey == null )
                {
                    close();
                    return;
                }
                final ByteIterable nextValueIterable = cursor.getValue();
                final String nextStringValue = nextValueIterable == null ? null : bindMachine.entryToValue( nextValueIterable );

                nextValue = new AbstractMap.SimpleImmutableEntry<>( decodedKey, nextStringValue );
            }
            catch ( final Exception e )
            {
                e.printStackTrace();
                throw e;
            }
        }

        @Override
        public void close( )
        {
            if ( closed )
            {
                return;
            }
            cursor.close();
            transaction.abort();
            nextValue = null;
            closed = true;
        }

        @Override
        public boolean hasNext( )
        {
            return !closed && nextValue != null;
        }

        @Override
        public Map.Entry<String, String> next( )
        {
            if ( closed )
            {
                return null;
            }
            final Map.Entry<String, String> value = nextValue;
            doNext();
            return value;
        }
        
        @Override
        public void remove( )
        {
            throw new UnsupportedOperationException( "remove not supported" );
        }
    }

    @Override
    public void putAll( final LocalDB.DB db, final Map<String, String> keyValueMap ) throws LocalDBException
    {
        checkStatus( true );
        environment.executeInTransaction( transaction ->
        {
            final Store store = getStore( db );
            for ( final Map.Entry<String, String> entry : keyValueMap.entrySet() )
            {
                final ByteIterable k = bindMachine.keyToEntry( entry.getKey() );
                final ByteIterable v = bindMachine.valueToEntry( entry.getValue() );
                store.put( transaction, k, v );
            }
        } );
        outputLogExecutor.conditionallyExecuteTask();
    }


    @Override
    public boolean put( final LocalDB.DB db, final String key, final String value ) throws LocalDBException
    {
        checkStatus( true );
        return environment.computeInTransaction( transaction ->
        {
            final ByteIterable k = bindMachine.keyToEntry( key );
            final ByteIterable v = bindMachine.valueToEntry( value );
            final Store store = getStore( db );
            return store.put( transaction, k, v );
        } );
    }

    @LocalDB.WriteOperation
    public boolean putIfAbsent( final LocalDB.DB db, final String key, final String value ) throws LocalDBException
    {
        checkStatus( true );
        return environment.computeInTransaction( transaction ->
        {
            final ByteIterable k = bindMachine.keyToEntry( key );
            final ByteIterable v = bindMachine.valueToEntry( value );
            final Store store = getStore( db );
            final ByteIterable existingValue = store.get( transaction, k );
            if ( existingValue != null )
            {
                return false;
            }
            return store.put( transaction, k, v );
        } );
    }

    @Override
    public boolean remove( final LocalDB.DB db, final String key ) throws LocalDBException
    {
        checkStatus( true );
        return environment.computeInTransaction( transaction ->
        {
            final Store store = getStore( db );
            return store.delete( transaction, bindMachine.keyToEntry( key ) );
        } );
    }

    @Override
    public void removeAll( final LocalDB.DB db, final Collection<String> keys ) throws LocalDBException
    {
        checkStatus( true );
        environment.executeInTransaction( transaction ->
        {
            final Store store = getStore( db );
            for ( final String key : keys )
            {
                store.delete( transaction, bindMachine.keyToEntry( key ) );
            }
        } );
    }

    @Override
    public void truncate( final LocalDB.DB db ) throws LocalDBException
    {
        checkStatus( true );

        {
            final long finalSize = this.size( db );
            LOGGER.trace( () -> "begin truncate of " + db.toString() + ", size=" + finalSize );
        }
        final Instant startDate = Instant.now();

        environment.executeInTransaction( transaction ->
        {
            environment.truncateStore( db.toString(), transaction );
            final Store newStoreReference = environment.openStore( db.toString(), StoreConfig.USE_EXISTING, transaction );
            cachedStoreObjects.put( db, newStoreReference );
        } );

        {
            final long finalSize = this.size( db );
            LOGGER.trace( () -> "completed truncate of " + db.toString()
                    + " (" + TimeDuration.fromCurrent( startDate ).asCompactString() + ")"
                    + ", size=" + finalSize );
        }
    }

    @Override
    public File getFileLocation( )
    {
        return fileLocation;
    }

    @Override
    public LocalDB.Status getStatus( )
    {
        return status;
    }

    private Store getStore( final LocalDB.DB db )
    {
        return cachedStoreObjects.get( db );
    }

    private Store initStore( final LocalDB.DB db, final Transaction txn )
    {
        return environment.openStore( db.toString(), StoreConfig.WITHOUT_DUPLICATES, txn );
    }


    private void checkStatus( final boolean writeOperation ) throws LocalDBException
    {
        if ( status != LocalDB.Status.OPEN )
        {
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, "cannot perform operation, localdb instance is not open" ) );
        }

        if ( writeOperation && readOnly )
        {
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, "cannot perform operation, localdb is in read-only mode" ) );
        }

        outputLogExecutor.conditionallyExecuteTask();
    }

    private void outputStats( )
    {
        LOGGER.trace( () -> "xodus environment stats: " + StringUtil.mapToString( debugInfo() ) );
    }

    @Override
    public Map<String, Serializable> debugInfo( )
    {
        final Map<String, Serializable> outputStats = new LinkedHashMap<>();
        {
            final Statistics statistics = environment.getStatistics();
            for ( final EnvironmentStatistics.Type type : EnvironmentStatistics.Type.values() )
            {
                final String name = type.name();
                final StatisticsItem item = statistics.getStatisticsItem( name );
                if ( item != null )
                {
                    outputStats.put( name, String.valueOf( item.getTotal() ) );
                }
            }
        }

        try
        {
            for ( final LocalDB.DB db : LocalDB.DB.values() )
            {
                outputStats.put( "size." + db.name(), this.size( db ) );
            }
        }
        catch ( final LocalDBException e )
        {
            LOGGER.debug( () -> "error while calculating sizes for localDB debug output: "  + e.getMessage() );
        }

        return outputStats;
    }

    private static class BindMachine
    {
        private static final byte COMPRESSED_PREFIX = 98;
        private static final byte UNCOMPRESSED_PREFIX = 99;

        private static final int DEFAULT_MIN_COMPRESSION_LENGTH = 16;
        private static final boolean DEFAULT_ENABLE_COMPRESSION = false;

        private final int minCompressionLength;
        private final boolean enableCompression;

        BindMachine( final boolean enableCompression, final int minCompressionLength )
        {
            this.enableCompression = enableCompression;
            this.minCompressionLength = minCompressionLength;
        }

        ByteIterable keyToEntry( final String key )
        {
            return StringBinding.stringToEntry( key );
        }

        String entryToKey( final ByteIterable entry )
        {
            return StringBinding.entryToString( entry );
        }

        ByteIterable valueToEntry( final String value )
        {
            if ( !enableCompression || value.length() < minCompressionLength )
            {
                final ByteIterable byteIterable = StringBinding.stringToEntry( value );
                return new ArrayByteIterable( UNCOMPRESSED_PREFIX, byteIterable );
            }

            final ByteIterable byteIterable = StringBinding.stringToEntry( value );
            final byte[] rawArray = byteIterable.getBytesUnsafe();
            final byte[] compressedArray = compressData( rawArray );

            if ( compressedArray.length < rawArray.length )
            {
                return new ArrayByteIterable( COMPRESSED_PREFIX, new ArrayByteIterable( compressedArray ) );
            }
            else
            {
                return new ArrayByteIterable( UNCOMPRESSED_PREFIX, byteIterable );
            }
        }

        String entryToValue( final ByteIterable value )
        {
            final byte[] rawValue = value.getBytesUnsafe();
            final byte[] strippedArray = new byte[ rawValue.length - 1 ];
            System.arraycopy( rawValue, 1, strippedArray, 0, rawValue.length - 1 );
            if ( rawValue[ 0 ] == UNCOMPRESSED_PREFIX )
            {
                return StringBinding.entryToString( new ArrayByteIterable( strippedArray ) );
            }
            else if ( rawValue[ 0 ] == COMPRESSED_PREFIX )
            {
                final byte[] decompressedValue = decompressData( strippedArray );
                return StringBinding.entryToString( new ArrayByteIterable( decompressedValue ) );
            }
            throw new IllegalStateException( "unknown value prefix " + Byte.toString( rawValue[ 0 ] ) );
        }

        static byte[] compressData( final byte[] data )
        {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream( byteArrayOutputStream, new Deflater() );
            try
            {
                deflaterOutputStream.write( data );
                deflaterOutputStream.close();
            }
            catch ( final IOException e )
            {
                throw new IllegalStateException( "unexpected exception compressing data stream: " + e.getMessage(), e );
            }
            return byteArrayOutputStream.toByteArray();
        }

        static byte[] decompressData( final byte[] data )
        {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final InflaterOutputStream inflaterOutputStream = new InflaterOutputStream( byteArrayOutputStream, new Inflater() );
            try
            {
                inflaterOutputStream.write( data );
                inflaterOutputStream.close();
            }
            catch ( final IOException e )
            {
                throw new IllegalStateException( "unexpected exception decompressing data stream: " + e.getMessage(), e );
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

    @Override
    public Set<Flag> flags( )
    {
        return Collections.emptySet();
    }

    private static void outputReadme( final File xodusPath )
    {
        try
        {
            final ResourceBundle resourceBundle = ResourceBundle.getBundle( XodusLocalDB.class.getName() );
            final String contents = resourceBundle.getString( "ReadmeContents" );
            final byte[] byteContents = contents.getBytes( PwmConstants.DEFAULT_CHARSET );
            Files.write( xodusPath.toPath(), byteContents, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING );
        }
        catch ( final IOException e )
        {
            LOGGER.error( () -> "error writing LocalDB readme file: " + e.getMessage() );
        }
    }

    private File getDirtyFile()
    {
        return new File( this.getFileLocation().getAbsolutePath() + File.separator + FILE_SUB_PATH + File.separator + "xodus.open" );
    }
}
