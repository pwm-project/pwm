/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import password.pwm.PwmApplication;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * A LIFO {@link Queue} implementation backed by a localDB instance.  {@code this} instances are internally
 * synchronized.
 */
public class LocalDBStoredQueue implements Queue<String>, Deque<String>
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDBStoredQueue.class, true );
    private static final int MAX_SIZE = Integer.MAX_VALUE - 3;

    private static final String KEY_HEAD_POSITION = "_HEAD_POSITION";
    private static final String KEY_TAIL_POSITION = "_TAIL_POSITION";
    private static final String KEY_VERSION = "_KEY_VERSION";
    private static final String VALUE_VERSION = "7a";

    private final InternalQueue internalQueue;

    private LocalDBStoredQueue(
            final LocalDB localDB,
            final LocalDB.DB db,
            final boolean developerDebug
    )
            throws LocalDBException
    {
        this.internalQueue = new InternalQueue( localDB, db, developerDebug );
    }

    public static LocalDBStoredQueue createLocalDBStoredQueue(
            final PwmApplication pwmApplication,
            final LocalDB pwmDB,
            final LocalDB.DB db
    )
            throws LocalDBException
    {

        boolean developerDebug = false;
        try
        {
            developerDebug = pwmApplication.getConfig().isDevDebugMode();
        }
        catch ( final Exception e )
        {
            LOGGER.debug( () -> "can't read app property for developerDebug mode: " + e.getMessage() );
        }

        return new LocalDBStoredQueue( pwmDB, db, developerDebug );
    }

    public static LocalDBStoredQueue createLocalDBStoredQueue(
            final LocalDB pwmDB,
            final LocalDB.DB db,
            final boolean debugEnabled
    )
            throws LocalDBException
    {

        return new LocalDBStoredQueue( pwmDB, db, debugEnabled );
    }

    public void removeLast( final int removalCount )
    {
        try
        {
            internalQueue.removeLast( removalCount, false );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected localDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    public void removeFirst( final int removalCount )
    {
        try
        {
            internalQueue.removeFirst( removalCount, false );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected localDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public boolean isEmpty( )
    {
        try
        {
            return internalQueue.size() == 0;
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( e );
        }
    }

    @Override
    public Object[] toArray( )
    {
        final List<Object> returnList = new ArrayList<>( this );
        return returnList.toArray();
    }

    @Override
    public <T> T[] toArray( final T[] a )
    {
        int index = 0;
        for ( final String s : this )
        {
            a[index] = ( T ) s;
            index++;
        }
        return a;
    }

    @Override
    public boolean containsAll( final Collection<?> c )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll( final Collection<? extends String> c )
    {
        try
        {
            final Collection<String> stringCollection = new ArrayList<>();
            for ( final Object loopObj : c )
            {
                if ( loopObj != null )
                {
                    stringCollection.add( loopObj.toString() );
                }
            }
            internalQueue.addFirst( stringCollection );
            return true;
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected LocalDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public boolean removeAll( final Collection<?> c )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add( final String s )
    {
        try
        {
            internalQueue.addFirst( Collections.singletonList( s ) );
            return true;
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected LocalDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public boolean retainAll( final Collection<?> c )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear( )
    {
        try
        {
            internalQueue.clear();
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected LocalDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public boolean remove( final Object o )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains( final Object o )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size( )
    {
        try
        {
            final long realSize = internalQueue.size();
            return realSize >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) realSize;
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( e );
        }
    }

    @Override
    public void addFirst( final String s )
    {
        try
        {
            internalQueue.addFirst( Collections.singletonList( s ) );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected LocalDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public void addLast( final String s )
    {
        try
        {
            internalQueue.addLast( Collections.singletonList( s ) );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected LocalDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public boolean offerFirst( final String s )
    {
        try
        {
            internalQueue.addFirst( Collections.singletonList( s ) );
            return true;
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected localDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public boolean offerLast( final String s )
    {
        try
        {
            internalQueue.addLast( Collections.singletonList( s ) );
            return true;
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected localDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public String removeFirst( )
    {
        final String value = pollFirst();
        if ( value == null )
        {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public String removeLast( )
    {
        final String value = pollLast();
        if ( value == null )
        {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public String pollFirst( )
    {
        try
        {
            final List<String> values = internalQueue.removeFirst( 1, true );
            if ( values == null || values.isEmpty() )
            {
                return null;
            }
            return values.get( 0 );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected localDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public String pollLast( )
    {
        try
        {
            final List<String> values = internalQueue.removeLast( 1, true );
            if ( values == null || values.isEmpty() )
            {
                return null;
            }
            return values.get( 0 );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected localDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public String getFirst( )
    {
        final String value = peekFirst();
        if ( value == null )
        {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public String getLast( )
    {
        final String value = peekLast();
        if ( value == null )
        {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public String peekFirst( )
    {
        try
        {
            final List<String> values = internalQueue.getFirst( 1 );
            if ( JavaHelper.isEmpty( values ) )
            {
                return null;
            }
            return values.get( 0 );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected localDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public String peekLast( )
    {
        try
        {
            final List<String> values = internalQueue.getLast( 1 );
            if ( values == null || values.isEmpty() )
            {
                return null;
            }
            return values.get( 0 );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( "unexpected localDB error while modifying queue: " + e.getMessage(), e );
        }
    }

    @Override
    public boolean removeFirstOccurrence( final Object o )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeLastOccurrence( final Object o )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void push( final String s )
    {
        this.addFirst( s );
    }

    @Override
    public String pop( )
    {
        final String value = this.removeFirst();
        if ( value == null )
        {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public Iterator<String> descendingIterator( )
    {
        try
        {
            return new InnerIterator( internalQueue, false );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( e );
        }
    }

    @Override
    public Iterator<String> iterator( )
    {
        try
        {
            return new InnerIterator( internalQueue, true );
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( e );
        }
    }

    @Override
    public boolean offer( final String s )
    {
        this.add( s );
        return true;
    }

    @Override
    public String remove( )
    {
        return this.removeFirst();
    }

    @Override
    public String poll( )
    {
        try
        {
            return this.removeFirst();
        }
        catch ( final NoSuchElementException e )
        {
            return null;
        }
    }

    @Override
    public String element( )
    {
        return this.getFirst();
    }

    @Override
    public String peek( )
    {
        return this.peekFirst();
    }

    public LocalDB getLocalDB( )
    {
        return internalQueue.localDB;
    }

    private static class InnerIterator implements Iterator<String>
    {
        private Position position;
        private final InternalQueue internalQueue;
        private final boolean first;
        private final long queueSizeAtCreate;
        private int steps;


        private InnerIterator( final InternalQueue internalQueue, final boolean first )
                throws LocalDBException
        {
            this.internalQueue = internalQueue;
            this.first = first;
            position = internalQueue.size() == 0 ? null : first ? internalQueue.headPosition : internalQueue.tailPosition;
            queueSizeAtCreate = internalQueue.size();
        }

        @Override
        public boolean hasNext( )
        {
            return position != null;
        }

        @Override
        public String next( )
        {
            if ( position == null )
            {
                throw new NoSuchElementException();
            }
            steps++;
            try
            {
                final String nextValue = internalQueue.localDB.get( internalQueue.db, position.key() );
                if ( first )
                {
                    position = position.equals( internalQueue.tailPosition ) ? null : position.previous();
                }
                else
                {
                    position = position.equals( internalQueue.headPosition ) ? null : position.next();
                }
                if ( steps > queueSizeAtCreate )
                {
                    position = null;
                }
                return nextValue;
            }
            catch ( final LocalDBException e )
            {
                throw new IllegalStateException( "unexpected localDB error while iterating queue: " + e.getMessage(), e );
            }
        }

        @Override
        public void remove( )
        {
            throw new UnsupportedOperationException();
        }
    }

    static class Position
    {
        private static final int RADIX = 36;
        private static final long MAXIMUM_POSITION = Long.parseLong( "zzzzzz", RADIX );
        private static final long MINIMUM_POSITION = 0;

        private final long bigInt;

        private Position( final long bigInt )
        {
            this.bigInt = bigInt;
        }

        Position( final String bigInt )
        {
            this.bigInt = Long.parseLong( bigInt, RADIX );
        }

        public Position next( )
        {
            long next = bigInt + 1;
            if ( next > MAXIMUM_POSITION )
            {
                next = MINIMUM_POSITION;
            }
            return new Position( next );
        }

        public Position previous( )
        {
            long previous = bigInt - 1;
            if ( previous < MINIMUM_POSITION )
            {
                previous = MAXIMUM_POSITION;
            }
            return new Position( previous );
        }

        public long distanceToHead( final Position head )
        {
            final int compareToValue = Long.compare( head.bigInt, this.bigInt );
            if ( compareToValue == 0 )
            {
                return 0;
            }
            else if ( compareToValue == 1 )
            {
                return head.bigInt - this.bigInt;
            }

            final long tailToMax = MAXIMUM_POSITION -  this.bigInt;
            final long minToHead = head.bigInt - MINIMUM_POSITION;
            return minToHead + tailToMax + 1;
        }

        public String toString( )
        {
            return key();
        }

        public String key()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append( Long.toString( bigInt, RADIX ).toUpperCase() );
            while ( sb.length() < 6 )
            {
                sb.insert( 0, '0' );
            }
            return sb.toString();
        }

        @Override
        public boolean equals( final Object o )
        {
            if ( this == o )
            {
                return true;
            }
            if ( o == null || getClass() != o.getClass() )
            {
                return false;
            }
            final Position position = ( Position ) o;
            return bigInt == position.bigInt;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( bigInt );
        }
    }

    private static class InternalQueue
    {
        private final LocalDB localDB;
        private final LocalDB.DB db;
        private volatile Position headPosition;
        private volatile Position tailPosition;
        private boolean developerDebug = false;
        private static final int DEBUG_MAX_ROWS = 50;
        private static final int DEBUG_MAX_WIDTH = 120;
        private static final Set<LocalDB.DB> DEBUG_IGNORED_DB = Collections.unmodifiableSet(
                Collections.unmodifiableSet( Collections.singleton( LocalDB.DB.EVENTLOG_EVENTS ) )
        );

        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        private InternalQueue( final LocalDB localDB, final LocalDB.DB db, final boolean developerDebug )
                throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                if ( localDB == null )
                {
                    throw new NullPointerException( "LocalDB cannot be null" );
                }

                if ( localDB.status() != LocalDB.Status.OPEN )
                {
                    throw new IllegalStateException( "LocalDB must hae a status of " + LocalDB.Status.OPEN );
                }

                if ( db == null )
                {
                    throw new NullPointerException( "DB cannot be null" );
                }

                this.developerDebug = developerDebug;
                this.localDB = localDB;
                this.db = db;
                init();
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        private void init( )
                throws LocalDBException
        {
            if ( !checkVersion() )
            {
                clear();
            }

            final String headPositionStr = localDB.get( db, KEY_HEAD_POSITION );
            final String tailPositionStr = localDB.get( db, KEY_TAIL_POSITION );

            headPosition = headPositionStr != null && headPositionStr.length() > 0 ? new Position( headPositionStr ) : new Position( "0" );
            tailPosition = tailPositionStr != null && tailPositionStr.length() > 0 ? new Position( tailPositionStr ) : new Position( "0" );

            {
                final long finalSize = this.size();
                LOGGER.trace( () -> "loaded for db " + db + "; headPosition=" + headPosition + ", tailPosition=" + tailPosition + ", size=" + finalSize );
            }

            repair();

            debugOutput( "post init()" );
        }

        private boolean checkVersion( ) throws LocalDBException
        {
            final String storedVersion = localDB.get( db, KEY_VERSION );
            if ( !Objects.equals( storedVersion, VALUE_VERSION ) )
            {
                LOGGER.warn( () -> "values in db " + db + " use an outdated format, the stored events will be purged!" );
                return false;
            }
            return true;
        }

        public void clear( )
                throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                localDB.truncate( db );

                headPosition = new Position( "0" );
                tailPosition = new Position( "0" );
                final Map<String, String> keyValueMap = new HashMap<>();
                keyValueMap.put( KEY_HEAD_POSITION, headPosition.toString() );
                keyValueMap.put( KEY_TAIL_POSITION, tailPosition.toString() );
                keyValueMap.put( KEY_VERSION, VALUE_VERSION );

                localDB.putAll( db, keyValueMap );
                debugOutput( "post clear()" );
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        public long size( )
                throws LocalDBException
        {
            lock.readLock().lock();
            try
            {
                return internalSize();
            }
            finally
            {
                lock.readLock().unlock();
            }
        }

        private long internalSize( )
                throws LocalDBException
        {
            if ( headPosition.equals( tailPosition ) && localDB.get( db, headPosition.toString() ) == null )
            {
                return 0;
            }
            return tailPosition.distanceToHead( headPosition ) + 1;
        }

        List<String> removeFirst( final int removalCount, final boolean returnValues ) throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                debugOutput( "pre removeFirst()" );
                final List<String> removedValues = removeImpl( removalCount, returnValues, true );
                debugOutput( "post removeFirst()" );
                return removedValues;
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        List<String> removeLast( final int removalCount, final boolean returnValues ) throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                debugOutput( "pre removeLast()" );
                final List<String> removedValues = removeImpl( removalCount, returnValues, false );
                debugOutput( "post removeLast()" );
                return removedValues;
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        private List<String> removeImpl( final int removalCount, final boolean returnValues, final boolean forward )
                throws LocalDBException
        {
            if ( removalCount < 1 )
            {
                return Collections.emptyList();
            }

            final List<String> removalKeys = new ArrayList<>();
            final List<String> removedValues = new ArrayList<>();
            Position loopPosition = forward ? headPosition : tailPosition;
            int removedPositions = 0;
            while ( removedPositions < removalCount )
            {
                removalKeys.add( loopPosition.key() );
                if ( returnValues )
                {
                    final String loopValue = localDB.get( db, loopPosition.key() );
                    if ( loopValue != null )
                    {
                        removedValues.add( loopValue );
                    }
                }

                if ( forward )
                {
                    loopPosition = loopPosition.equals( tailPosition ) ? loopPosition : loopPosition.previous();
                }
                else
                {
                    loopPosition = loopPosition.equals( headPosition ) ? loopPosition : loopPosition.next();
                }

                removedPositions++;
            }
            localDB.removeAll( db, removalKeys );
            localDB.put( db, forward ? KEY_HEAD_POSITION : KEY_TAIL_POSITION, loopPosition.key() );

            if ( forward )
            {
                headPosition = loopPosition;
            }
            else
            {
                tailPosition = loopPosition;
            }

            return Collections.unmodifiableList( removedValues );
        }

        void addFirst( final Collection<String> values )
                throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                debugOutput( "pre addFirst()" );
                addImpl( values, true );
                debugOutput( "post addFirst()" );
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        void addLast( final Collection<String> values ) throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                debugOutput( "pre addLast()" );
                addImpl( values, false );
                debugOutput( "post addLast()" );
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        private void addImpl( final Collection<String> values, final boolean forward )
                throws LocalDBException
        {
            if ( JavaHelper.isEmpty( values ) )
            {
                return;
            }

            if ( internalSize() + values.size() > MAX_SIZE )
            {
                throw new IllegalStateException( "queue overflow" );
            }

            final Iterator<String> valueIterator = values.iterator();

            final Map<String, String> keyValueMap = new HashMap<>();
            Position loopPosition = forward ? headPosition : tailPosition;

            if ( internalSize() == 0 )
            {
                keyValueMap.put( loopPosition.toString(), valueIterator.next() );
            }

            while ( valueIterator.hasNext() )
            {
                loopPosition = forward ? loopPosition.next() : loopPosition.previous();
                keyValueMap.put( loopPosition.key(), valueIterator.next() );
            }

            keyValueMap.put( forward ? KEY_HEAD_POSITION : KEY_TAIL_POSITION, loopPosition.key() );
            localDB.putAll( db, keyValueMap );

            if ( forward )
            {
                headPosition = loopPosition;
            }
            else
            {
                tailPosition = loopPosition;
            }
        }



        List<String> getFirst( final int count )
                throws LocalDBException
        {
            lock.readLock().lock();
            try
            {
                debugOutput( "pre getFirst()" );
                final List<String> returnList = getImpl( count, true );
                debugOutput( "post getFirst()" );
                return returnList;
            }
            finally
            {
                lock.readLock().unlock();
            }
        }

        List<String> getLast( final int count )
                throws LocalDBException
        {
            lock.readLock().lock();
            try
            {
                debugOutput( "pre getLast()" );
                final List<String> returnList = getImpl( count, false );
                debugOutput( "post getLast()" );
                return returnList;
            }
            finally
            {
                lock.readLock().unlock();
            }
        }

        private List<String> getImpl( final long count, final boolean forward )
                throws LocalDBException
        {
            long getCount = count;
            if ( getCount < 1 )
            {
                return Collections.emptyList();
            }

            if ( getCount > internalSize() )
            {
                getCount = internalSize();
            }

            final List<String> returnList = new ArrayList<>();

            Position nextPosition = forward ? headPosition : tailPosition;
            while ( returnList.size() < getCount )
            {
                returnList.add( localDB.get( db, nextPosition.key() ) );
                nextPosition = forward ? nextPosition.previous() : nextPosition.next();
            }

            return Collections.unmodifiableList( returnList );
        }

        void debugOutput( final String input )
        {
            if ( !developerDebug || DEBUG_IGNORED_DB.contains( db ) )
            {
                return;
            }

            final Supplier<CharSequence> debugOutput = () ->
            {
                final StringBuilder sb = new StringBuilder();
                try
                {
                    sb.append( input );
                    sb.append( "  tailPosition=" ).append( tailPosition ).append( ", headPosition=" ).append( headPosition ).append( ", db=" ).append( db );
                    sb.append( ", size=" ).append( internalSize() ).append( "\n" );

                    try ( LocalDB.LocalDBIterator<Map.Entry<String, String>> localDBIterator = localDB.iterator( db ) )
                    {
                        int rowCount = 0;
                        while ( localDBIterator.hasNext() && rowCount < DEBUG_MAX_ROWS )
                        {
                            final Map.Entry<String, String> entry = localDBIterator.next();
                            final String key = entry.getKey();
                            String value = entry.getValue();
                            value = value == null ? "" : value;
                            value = value.length() < DEBUG_MAX_WIDTH ? value : value.substring( 0, DEBUG_MAX_WIDTH ) + "...";
                            final String row = key + " " + value;
                            sb.append( row ).append( "\n" );
                            rowCount++;
                        }
                    }
                }
                catch ( final LocalDBException e )
                {
                    LOGGER.error( () -> "error generating logMsg: " + e.getMessage() );
                }

                return sb.toString();
            };

            LOGGER.trace( debugOutput );
        }

        private void repair( ) throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                int headTrim = 0;
                int tailTrim = 0;

                debugOutput( "pre repair()" );

                final AtomicInteger examinedRecords = new AtomicInteger( 0 );

                final ConditionalTaskExecutor conditionalTaskExecutor = new ConditionalTaskExecutor( () ->
                {
                    try
                    {
                        localDB.put( db, KEY_HEAD_POSITION, headPosition.key() );
                        localDB.put( db, KEY_TAIL_POSITION, tailPosition.key() );
                        final long dbSize = size();
                        LOGGER.debug( () -> "repairing db " + db + ", " + examinedRecords.get() + " records examined"
                                + ", size=" + dbSize
                                + ", head=" + headPosition.key() + ", tail=" + tailPosition.key() );
                    }
                    catch ( final Exception e )
                    {
                        LOGGER.error( () -> "unexpected error during output of debug message during stored queue repair operation: " + e.getMessage(), e );
                    }
                },
                        new ConditionalTaskExecutor.TimeDurationPredicate( TimeDuration.SECONDS_10 )
                );

                // trim the top.
                while ( !headPosition.equals( tailPosition ) && localDB.get( db, headPosition.key() ) == null )
                {
                    examinedRecords.incrementAndGet();
                    conditionalTaskExecutor.conditionallyExecuteTask();
                    headPosition = headPosition.previous();
                    headTrim++;
                }
                localDB.put( db, KEY_HEAD_POSITION, headPosition.key() );

                // trim the bottom.
                while ( !headPosition.equals( tailPosition ) && localDB.get( db, tailPosition.toString() ) == null )
                {
                    examinedRecords.incrementAndGet();
                    conditionalTaskExecutor.conditionallyExecuteTask();
                    tailPosition = tailPosition.next();
                    tailTrim++;
                }
                localDB.put( db, KEY_TAIL_POSITION, tailPosition.key() );

                if ( tailTrim == 0 && headTrim == 0 )
                {
                    LOGGER.trace( () -> "repair unnecessary for " + db );
                }
                else
                {
                    if ( headTrim > 0 )
                    {
                        final int headTrimFinal = headTrim;
                        LOGGER.warn( () -> "trimmed " + headTrimFinal + " from head position against database " + db );
                    }

                    if ( tailTrim > 0 )
                    {
                        final int tailTrimFinal = tailTrim;
                        LOGGER.warn( () -> "trimmed " + tailTrimFinal + " from tail position against database " + db );
                    }
                }
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }
    }
}
