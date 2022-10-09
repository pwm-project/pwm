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

package password.pwm.receiver;

import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.StringBinding;
import jetbrains.exodus.env.Cursor;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.StoreConfig;
import jetbrains.exodus.env.Transaction;
import password.pwm.bean.TelemetryPublishBean;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;

public class Storage
{
    private static final Logger LOGGER = Logger.createLogger( Storage.class );
    private static final String STORE_NAME = "store1";

    private final Environment environment;
    private Store store;

    public Storage( final Settings settings ) throws IOException
    {
        final String path = settings.getSetting( Settings.Setting.storagePath );
        if ( path == null )
        {
            throw new IOException( "data path is not specified!" );
        }

        final File dataPath = new File( path );
        if ( !dataPath.exists() )
        {
            throw new IOException( "data path '" + dataPath + "' does not exist" );
        }

        final File storagePath = new File( dataPath.getAbsolutePath() + File.separator + "storage" );
        mkdirs( storagePath );

        final EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environment = Environments.newInstance( storagePath.getAbsolutePath(), environmentConfig );

        LOGGER.info( () -> "environment open" );

        environment.executeInTransaction( txn -> store
                = environment.openStore( STORE_NAME, StoreConfig.WITHOUT_DUPLICATES, txn ) );

        LOGGER.info( () -> "store open with " + count() + " records" );
    }

    public void store( final TelemetryPublishBean bean )
    {
        if ( bean == null )
        {
            return;
        }

        final String instanceHash = bean.getInstanceHash();
        if ( instanceHash != null )
        {
            final TelemetryPublishBean existingBean = get( instanceHash );
            Instant existingTimestamp = null;
            if ( existingBean != null )
            {
                existingTimestamp = existingBean.getTimestamp();
            }
            if ( existingTimestamp == null || existingTimestamp.isBefore( bean.getTimestamp() ) )
            {
                put( bean );
            }
        }
    }

    public Iterator<TelemetryPublishBean> iterator( )
    {
        return new InnerIterator();
    }

    private void put( final TelemetryPublishBean value )
    {
        environment.executeInTransaction( transaction ->
        {
            final ByteIterable k = StringBinding.stringToEntry( value.getInstanceHash() );
            final ByteIterable v = StringBinding.stringToEntry( JsonFactory.get().serialize( value ) );
            store.put( transaction, k, v );
        } );
    }

    private TelemetryPublishBean get( final String hash )
    {
        return environment.computeInTransaction( transaction ->
        {
            final ByteIterable k = StringBinding.stringToEntry( hash );
            final ByteIterable v = store.get( transaction, k );
            if ( v != null )
            {
                final String string = StringBinding.entryToString( new ArrayByteIterable( v ) );
                if ( StringUtil.notEmpty( string ) )
                {
                    return JsonFactory.get().deserialize( string, TelemetryPublishBean.class );
                }
            }
            return null;
        } );
    }

    public void close( )
    {
        store.getEnvironment().close();
    }

    public long count( )
    {
        return environment.computeInTransaction( transaction -> store.count( transaction ) );
    }

    private class InnerIterator implements AutoCloseable, Iterator<TelemetryPublishBean>
    {
        private final Transaction transaction;
        private final Cursor cursor;

        private boolean closed;
        private String nextValue = "";

        InnerIterator( )
        {
            this.transaction = environment.beginReadonlyTransaction();
            this.cursor = store.openCursor( transaction );
            doNext();
        }

        private void doNext( )
        {
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
                final ByteIterable nextKey = cursor.getKey();
                final String string = StringBinding.entryToString( new ArrayByteIterable( nextKey ) );

                if ( string == null || string.isEmpty() )
                {
                    close();
                    return;
                }
                nextValue = string;
            }
            catch ( final Exception e )
            {
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
        public TelemetryPublishBean next( )
        {
            final String value = nextValue;
            doNext();
            return get( value );
        }

        @Override
        public void remove( )
        {
            throw new UnsupportedOperationException( "remove not supported" );
        }
    }

    static void mkdirs( final File file ) throws IOException
    {
        if ( file.exists() )
        {
            if ( file.isDirectory() )
            {
                return;
            }
            throw new IOException( "path already exists as file: " + file.getAbsolutePath() );
        }

        if ( !file.mkdirs() )
        {
            throw new IOException( "unable to create path " + file.getAbsolutePath() );
        }
    }

}
