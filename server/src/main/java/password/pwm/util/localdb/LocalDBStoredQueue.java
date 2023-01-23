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

package password.pwm.util.localdb;

import password.pwm.PwmApplication;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
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
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
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

    private enum Direction
    {
        FORWARD,
        REVERSE,
    }

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
            internalQueue.remove( removalCount, false, Direction.REVERSE );
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
            internalQueue.remove( removalCount, false, Direction.FORWARD );
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
        try
        {
            return internalQueue.toArray();
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( e );
        }
    }

    @Override
    public <T> T[] toArray( final T[] a )
    {
        try
        {
            final String[] strArray = internalQueue.toArray();
            if ( a == null || a.length < strArray.length )
            {
                return ( T[] ) strArray;
            }
            System.arraycopy( strArray, 0, a, 0, strArray.length );
            return a;
        }
        catch ( final LocalDBException e )
        {
            throw new IllegalStateException( e );
        }
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
            final List<String> list = ( c == null ? List.of() : c ).stream()
                    .filter( Objects::nonNull )
                    .map( String::valueOf )
                    .toList();

            internalQueue.add( list, Direction.FORWARD );
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
            internalQueue.add( Collections.singletonList( s ), Direction.FORWARD );
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
            internalQueue.add( Collections.singletonList( s ), Direction.FORWARD );
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
            internalQueue.add( Collections.singletonList( s ), Direction.REVERSE );
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
            internalQueue.add( Collections.singletonList( s ), Direction.FORWARD );
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
            internalQueue.add( Collections.singletonList( s ), Direction.REVERSE );
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
            final List<String> values = internalQueue.remove( 1, true, Direction.FORWARD );
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
            final List<String> values = internalQueue.remove( 1, true, Direction.REVERSE );
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
            return internalQueue.peek( Direction.FORWARD ).orElse( null );
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
            return internalQueue.peek( Direction.REVERSE ).orElse( null );
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
            return new InnerIterator( internalQueue, Direction.REVERSE );
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
            return new InnerIterator( internalQueue, Direction.FORWARD );
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
        private final AtomicReference<Position> iteratorPosition = new AtomicReference<>();
        private final InternalQueue internalQueue;
        private final Direction direction;

        //private final Position initialPosition;

        /**
         * Safety counter to make sure this iterator does not seek infinitely.  It's possible concurrent modifications
         * to the internal queue might cause the end position equality checks to miss.
         */
        private final AtomicLong itemsRemaining = new AtomicLong();
        private final Lock lock;

        private InnerIterator( final InternalQueue internalQueue, final Direction direction )
                throws LocalDBException
        {
            this.lock = internalQueue.lock.readLock();
            this.internalQueue = internalQueue;
            this.direction = direction;

            lock.lock();
            try
            {
                final long currentSize = internalQueue.internalSize();
                this.itemsRemaining.set( currentSize );
                iteratorPosition.set( currentSize == 0
                        ? null
                        : internalQueue.currentPositionForDirection( direction ) );
            }
            finally
            {
                lock.unlock();
            }
        }

        @Override
        public boolean hasNext( )
        {
            lock.lock();
            try
            {
                return iteratorPosition.get() != null;
            }
            finally
            {
                lock.unlock();
            }
        }

        @Override
        public String next( )
        {
            lock.lock();
            try
            {
                return nextImpl();
            }
            finally
            {
                lock.unlock();
            }
        }

        private String nextImpl()
        {
            if ( iteratorPosition.get() == null )
            {
                throw new NoSuchElementException();
            }

            try
            {
                final String nextValue = internalQueue.localDB.get( internalQueue.db, iteratorPosition.get().key() ).orElseThrow();

                iteratorPosition.updateAndGet( position -> switch ( direction )
                        {
                            case FORWARD -> Objects.equals( position, internalQueue.tailPosition.get() ) ? null : position.previous();
                            case REVERSE -> Objects.equals( position, internalQueue.headPosition.get() ) ? null : position.next();
                        } );

                itemsRemaining.decrementAndGet();

                if ( itemsRemaining.get() < 1 )
                {
                    iteratorPosition.set( null );
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

    record Position(
            long position
    )
    {
        private static final int RADIX = 36;
        private static final long MAXIMUM_POSITION = Long.parseLong( "zzzzzz", RADIX );
        private static final long MINIMUM_POSITION = 0;
        private static final Position ZERO = new Position( 0 );

        Position
        {
            if ( position > MAXIMUM_POSITION )
            {
                throw new IllegalStateException();
            }
            if ( position < MINIMUM_POSITION )
            {
                throw new IllegalStateException();
            }
        }

        static Position fromKey( final String position )
        {
            return new Position( Long.parseLong( position, RADIX ) );
        }

        public static Position zero()
        {
            return ZERO;
        }

        public Position next( )
        {
            long next = position + 1;
            if ( next > MAXIMUM_POSITION )
            {
                next = MINIMUM_POSITION;
            }
            return new Position( next );
        }

        public Position previous( )
        {
            long previous = position - 1;
            if ( previous < MINIMUM_POSITION )
            {
                previous = MAXIMUM_POSITION;
            }
            return new Position( previous );
        }

        public long distanceToHead( final Position head )
        {
            final int compareToValue = Long.compare( head.position, this.position );
            if ( compareToValue == 0 )
            {
                return 0;
            }
            else if ( compareToValue == 1 )
            {
                return head.position - this.position;
            }

            final long tailToMax = MAXIMUM_POSITION -  this.position;
            final long minToHead = head.position - MINIMUM_POSITION;
            return minToHead + tailToMax + 1;
        }

        public String toString( )
        {
            return key();
        }

        public String key()
        {
            return StringUtil.padLeft( Long.toString( position, RADIX ).toUpperCase(), 6, '0' );
        }
    }

    private static class InternalQueue
    {
        private final LocalDB localDB;
        private final LocalDB.DB db;
        private final AtomicReference<Position> headPosition = new AtomicReference<>();
        private final AtomicReference<Position> tailPosition = new AtomicReference<>();
        private boolean developerDebug = false;
        private static final int DEBUG_MAX_ROWS = 50;
        private static final int DEBUG_MAX_WIDTH = 120;
        private static final Set<LocalDB.DB> DEBUG_IGNORED_DB = Set.of( LocalDB.DB.EVENTLOG_EVENTS );

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

            headPosition.set( initPosition( KEY_HEAD_POSITION ) );
            tailPosition.set( initPosition( KEY_TAIL_POSITION ) );

            {
                final long finalSize = this.size();
                LOGGER.trace( () -> "loaded for db " + db + "; headPosition=" + headPosition.get()
                        + ", tailPosition=" + tailPosition.get() + ", size=" + finalSize );
            }

            repair();

            debugOutput( "post init()" );
        }

        private Position initPosition( final String dbKey )
                throws LocalDBException
        {
            final Optional<String> positionStr = localDB.get( db, dbKey  );
            return positionStr.map( Position::fromKey ).orElse( Position.zero() );
        }

        private boolean checkVersion( ) throws LocalDBException
        {
            final Optional<String> storedVersion = localDB.get( db, KEY_VERSION );
            if ( storedVersion.isEmpty() || !Objects.equals( storedVersion.get(), VALUE_VERSION ) )
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

                headPosition.set( Position.zero() );
                tailPosition.set( Position.zero() );

                localDB.putAll( db, Map.of(
                        KEY_HEAD_POSITION, headPosition.get().toString(),
                        KEY_TAIL_POSITION, tailPosition.get().toString(),
                        KEY_VERSION, VALUE_VERSION ) );

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
            if ( Objects.equals( headPosition.get(), tailPosition.get() ) && localDB.get( db, headPosition.get().toString() ).isEmpty() )
            {
                return 0;
            }
            return tailPosition.get().distanceToHead( headPosition.get() ) + 1;
        }

        private List<String> remove( final int removalCount, final boolean returnValues, final Direction direction )
                throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                debugOutput( "pre remove() " + direction );

                if ( removalCount < 1 )
                {
                    return Collections.emptyList();
                }

                final List<String> removalKeys = new ArrayList<>( removalCount );
                final List<String> removedValues = new ArrayList<>( removalCount );

                Position loopPosition = currentPositionForDirection( direction );

                int removedPositions = 0;
                while ( removedPositions < removalCount )
                {
                    removalKeys.add( loopPosition.key() );
                    if ( returnValues )
                    {
                        final Optional<String> loopValue = localDB.get( db, loopPosition.key() );
                        loopValue.ifPresent( removedValues::add );
                    }

                    loopPosition = switch ( direction )
                            {
                                case FORWARD -> loopPosition.equals( tailPosition.get() ) ? loopPosition : loopPosition.previous();
                                case REVERSE -> loopPosition.equals( headPosition.get() ) ? loopPosition : loopPosition.next();
                            };

                    removedPositions++;
                }

                localDB.removeAll( db, removalKeys );
                localDB.put( db, direction == Direction.FORWARD ? KEY_HEAD_POSITION : KEY_TAIL_POSITION, loopPosition.key() );

                switch ( direction )
                {
                    case FORWARD -> headPosition.set( loopPosition );
                    case REVERSE -> tailPosition.set( loopPosition );
                    default -> throw new IllegalStateException();
                }

                debugOutput( "post remove() " + direction );
                return List.copyOf( removedValues );
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        String[] toArray()
                throws LocalDBException
        {
            lock.readLock().lock();
            try
            {
                debugOutput( "pre toArray()" );
                final String[] stringArray = CollectionUtil.iteratorToStream( new InnerIterator( this, Direction.FORWARD ) )
                        .toArray( String[]::new );
                debugOutput( "post toArray()" );
                return stringArray;
            }
            finally
            {
                lock.readLock().unlock();
            }
        }

        private Position currentPositionForDirection( final Direction direction )
        {
            return direction == Direction.FORWARD ? headPosition.get() : tailPosition.get();
        }

        private void add( final Collection<String> values, final Direction direction )
                throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                debugOutput( "pre add() " + direction );
                if ( CollectionUtil.isEmpty( values ) )
                {
                    return;
                }

                if ( internalSize() + values.size() > MAX_SIZE )
                {
                    throw new IllegalStateException( "queue overflow" );
                }

                final Iterator<String> valueIterator = values.iterator();

                final Map<String, String> keyValueMap = new HashMap<>( values.size() );
                Position loopPosition = currentPositionForDirection( direction );

                if ( internalSize() == 0 )
                {
                    keyValueMap.put( loopPosition.toString(), valueIterator.next() );
                }

                while ( valueIterator.hasNext() )
                {
                    loopPosition = direction == Direction.FORWARD ? loopPosition.next() : loopPosition.previous();
                    keyValueMap.put( loopPosition.key(), valueIterator.next() );
                }

                keyValueMap.put( direction == Direction.FORWARD ? KEY_HEAD_POSITION : KEY_TAIL_POSITION, loopPosition.key() );
                localDB.putAll( db, keyValueMap );

                if ( direction == Direction.FORWARD )
                {
                    headPosition.set( loopPosition );
                }
                else
                {
                    tailPosition.set( loopPosition );
                }
                debugOutput( "post add() " + direction );
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        private Optional<String> peek( final Direction direction )
                throws LocalDBException
        {
            lock.readLock().lock();
            try
            {
                debugOutput( "pre get() " + direction );

                if ( internalSize() <= 0 )
                {
                    return Optional.empty();
                }

                final Position nextPosition = currentPositionForDirection( direction );
                debugOutput( "post get() " + direction );

                return localDB.get( db, nextPosition.key() );

            }
            finally
            {
                lock.readLock().unlock();
            }

        }

        void debugOutput( final String input )
        {
            if ( !developerDebug || DEBUG_IGNORED_DB.contains( db ) )
            {
                return;
            }

            final Supplier<String> debugOutput = () ->
            {
                final StringBuilder sb = new StringBuilder();
                try
                {
                    sb.append( input );
                    sb.append( "  tailPosition=" ).append( tailPosition.get() ).append( ", headPosition=" )
                            .append( headPosition.get() ).append( ", db=" ).append( db );
                    sb.append( ", size=" ).append( internalSize() ).append( '\n' );

                    try ( LocalDB.LocalDBIterator localDBIterator = localDB.iterator( db ) )
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
                            sb.append( row ).append( '\n' );
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

        private void repair( )
                throws LocalDBException
        {
            lock.writeLock().lock();
            try
            {
                debugOutput( "pre repair()" );

                final int headTrimCounter = trimQueueEnd( headPosition, Position::previous );
                final int tailTrimCounter = trimQueueEnd( tailPosition, Position::next );

                outputRepairConclusion( tailTrimCounter, headTrimCounter );
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        private int trimQueueEnd( final AtomicReference<Position> examinePosition, final Function<Position, Position> calcNextPosition )
                throws LocalDBException
        {
            long safetyCounter = internalSize();
            Position loopPosition = examinePosition.get();
            int counter = 0;
            while ( safetyCounter >= 0 && !headPosition.get().equals( tailPosition.get() )
                    && localDB.get( db, loopPosition.key() ).isEmpty() )
            {
                counter++;
                loopPosition = calcNextPosition.apply( loopPosition );
                writeRepairState( counter );
                safetyCounter--;
                examinePosition.set( loopPosition );
            }
            return counter;
        }

        private void outputRepairConclusion( final int tailTrimCounter, final int headTrimCounter )
        {
            if ( tailTrimCounter == 0 && headTrimCounter == 0 )
            {
                LOGGER.trace( () -> "repair unnecessary for " + db );
            }
            else
            {
                if ( headTrimCounter > 0 )
                {
                    LOGGER.warn( () -> "trimmed " + headTrimCounter + " from head position against database " + db );
                }

                if ( tailTrimCounter > 0 )
                {
                    LOGGER.warn( () -> "trimmed " + tailTrimCounter + " from tail position against database " + db );
                }
            }
        }

        private void writeRepairState( final int examinedRecords )
        {
            try
            {
                localDB.putAll( db, Map.ofEntries(
                        Map.entry( KEY_HEAD_POSITION, headPosition.get().key() ),
                        Map.entry( KEY_TAIL_POSITION, tailPosition.get().key() ) ) );
                final long dbSize = size();
                LOGGER.debug( () -> "repairing db " + db + ", " + examinedRecords + " records examined"
                        + ", size=" + dbSize
                        + ", head=" + headPosition.get().key() + ", tail=" + tailPosition.get().key() );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unexpected error during output of debug message during stored queue repair operation: "
                        + e.getMessage(), e );
            }
        }
    }
}
