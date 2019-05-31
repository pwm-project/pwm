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
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;

public class Storage
{
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

        environment.executeInTransaction( txn -> store
                = environment.openStore( "store1", StoreConfig.WITHOUT_DUPLICATES, txn ) );
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

    private boolean put( final TelemetryPublishBean value )
    {
        return environment.computeInTransaction( transaction ->
        {
            final ByteIterable k = StringBinding.stringToEntry( value.getInstanceHash() );
            final ByteIterable v = StringBinding.stringToEntry( JsonUtil.serialize( value ) );
            return store.put( transaction, k, v );
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
                if ( !StringUtil.isEmpty( string ) )
                {
                    return JsonUtil.deserialize( string, TelemetryPublishBean.class );
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

    private class InnerIterator implements AutoCloseable, Iterator
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
            catch ( Exception e )
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
        if ( !file.mkdirs() )
        {
            throw new IOException( "unable to create path " + file.getAbsolutePath() );
        }
    }

}
