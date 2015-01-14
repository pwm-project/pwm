/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.util.localdb;

import password.pwm.PwmApplication;
import password.pwm.util.logging.PwmLogger;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A LIFO {@link Queue} implementation backed by a localDB instance.  {@code this} instances are internally
 * synchronized.
 */
public class
        LocalDBStoredQueue implements Queue<String>, Deque<String>
{
// ------------------------------ FIELDS ------------------------------

    private final static PwmLogger LOGGER = PwmLogger.forClass(LocalDBStoredQueue.class, true);
    private final static int MAX_SIZE = Integer.MAX_VALUE - 3;

    private final static String KEY_HEAD_POSITION = "_HEAD_POSITION";
    private final static String KEY_TAIL_POSITION = "_TAIL_POSITION";
    private final static String KEY_VERSION = "_KEY_VERSION";
    private final static String VALUE_VERSION = "7a";

    private final InternalQueue internalQueue;

// --------------------------- CONSTRUCTORS ---------------------------

    private LocalDBStoredQueue(
            final LocalDB localDB,
            final LocalDB.DB DB,
            final boolean developerDebug
    )
            throws LocalDBException
    {
        this.internalQueue = new InternalQueue(localDB, DB, developerDebug);
    }

    public static synchronized LocalDBStoredQueue createLocalDBStoredQueue(
            final PwmApplication pwmApplication,
            final LocalDB pwmDB,
            final LocalDB.DB DB
    )
            throws LocalDBException
    {

        boolean developerDebug = false;
        try {
            developerDebug = pwmApplication.getConfig().isDevDebugMode();
        } catch (Exception e) {
            LOGGER.debug("can't read app property for developerDebug mode: " + e.getMessage());
        }

        return new LocalDBStoredQueue(pwmDB, DB, developerDebug);
    }

    public static synchronized LocalDBStoredQueue createLocalDBStoredQueue(
            final LocalDB pwmDB,
            final LocalDB.DB DB,
            final boolean debugEnabled
    )
            throws LocalDBException
    {

        return new LocalDBStoredQueue(pwmDB, DB, debugEnabled);
    }

    public void removeLast(final int removalCount) {
        try {
            internalQueue.removeLast(removalCount);
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected localDB error while modifying queue: " + e.getMessage(), e);
        }
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Collection ---------------------


    public boolean isEmpty() {
        try {
            return internalQueue.size() == 0;
        } catch (LocalDBException e) {
            throw new IllegalStateException(e);
        }
    }

    public Object[] toArray() {
        final List<Object> returnList = new ArrayList<>();
        for (final Iterator<String> innerIter = this.iterator(); innerIter.hasNext();) {
            returnList.add(innerIter.next());
        }
        return returnList.toArray();
    }

    public <T> T[] toArray(final T[] a) {
        int i = 0;
        for (final Iterator<String> innerIter = this.iterator(); innerIter.hasNext();) {
            a[i] = (T) innerIter.next();
            i++;
        }
        return a;
    }

    public boolean containsAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(final Collection<? extends String> c) {
        try {
            final Collection<String> stringCollection = new ArrayList<>();
            for (final Object loopObj : c) {
                if (loopObj != null) {
                    stringCollection.add(loopObj.toString());
                }
            }
            internalQueue.addFirst(stringCollection);
            return true;
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected LocalDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean add(final String s) {
        try {
            internalQueue.addFirst(Collections.singletonList(s));
            return true;
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected LocalDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        try {
            internalQueue.clear();
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected LocalDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean contains(final Object o) {
        throw new UnsupportedOperationException();
    }

    public int size() {
        try {
            return internalQueue.size();
        } catch (LocalDBException e) {
            throw new IllegalStateException(e);
        }
    }

// --------------------- Interface Deque ---------------------


    public void addFirst(final String s) {
        try {
            internalQueue.addFirst(Collections.singletonList(s));
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected LocalDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public void addLast(final String s) {
        try {
            internalQueue.addLast(Collections.singletonList(s));
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected LocalDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public boolean offerFirst(final String s) {
        try {
            internalQueue.addFirst(Collections.singletonList(s));
            return true;
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected localDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public boolean offerLast(final String s) {
        try {
            internalQueue.addLast(Collections.singletonList(s));
            return true;
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected localDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public String removeFirst() {
        final String value = pollFirst();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public String removeLast() {
        final String value = pollLast();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public String pollFirst() {
        try {
            final List<String> values = internalQueue.removeFirst(1);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.get(0);
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected localDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public String pollLast() {
        try {
            final List<String> values = internalQueue.removeLast(1);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.get(0);
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected localDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public String getFirst() {
        final String value = peekFirst();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public String getLast() {
        final String value = peekLast();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public String peekFirst() {
        try {
            final List<String> values = internalQueue.getFirst(1);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.get(0);
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected localDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public String peekLast() {
        try {
            final List<String> values = internalQueue.getLast(1);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.get(0);
        } catch (LocalDBException e) {
            throw new IllegalStateException("unexpected localDB error while modifying queue: " + e.getMessage(), e);
        }
    }

    public boolean removeFirstOccurrence(final Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean removeLastOccurrence(final Object o) {
        throw new UnsupportedOperationException();
    }

    public void push(final String s) {
        this.addFirst(s);
    }

    public String pop() {
        final String value = this.removeFirst();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public Iterator<String> descendingIterator() {
        try {
            return new InnerIterator<String>(internalQueue, false);
        } catch (LocalDBException e) {
            throw new IllegalStateException(e);
        }
    }

// --------------------- Interface Iterable ---------------------

    public Iterator<String> iterator() {
        try {
            return new InnerIterator<String>(internalQueue, true);
        } catch (LocalDBException e) {
            throw new IllegalStateException(e);
        }
    }

// --------------------- Interface Queue ---------------------


    public boolean offer(final String s) {
        this.add(s);
        return true;
    }

    public String remove() {
        return this.removeFirst();
    }

    public String poll() {
        try {
            return this.removeFirst();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public String element() {
        return this.getFirst();
    }

    public String peek() {
        return this.peekFirst();
    }

    public LocalDB getPwmDB() {
        return internalQueue.localDB;
    }

// -------------------------- INNER CLASSES --------------------------

    private class InnerIterator<K> implements Iterator {
        private Position position;
        private final InternalQueue internalQueue;
        private final boolean first;
        private int queueSizeAtCreate;
        private int steps;


        private InnerIterator(final InternalQueue internalQueue, final boolean first)
                throws LocalDBException
        {
            this.internalQueue = internalQueue;
            this.first = first;
            position = internalQueue.size() == 0 ? null : first ? internalQueue.headPosition : internalQueue.tailPosition;
            queueSizeAtCreate = internalQueue.size();
        }

        public boolean hasNext() {
            return position != null;
        }

        public String next() {
            if (position == null) {
                throw new NoSuchElementException();
            }
            steps++;
            try {
                final String nextValue = internalQueue.localDB.get(internalQueue.DB, position.toString());
                if (first) {
                    position = position.equals(internalQueue.tailPosition) ? null : position.previous();
                } else {
                    position = position.equals(internalQueue.headPosition) ? null : position.next();
                }
                if (steps > queueSizeAtCreate) {
                    position = null;
                }
                return nextValue;
            } catch (LocalDBException e) {
                throw new IllegalStateException("unexpected localDB error while iterating queue: " + e.getMessage(), e);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class Position {
        private final static int RADIX = 36;
        private final static BigInteger MAXIMUM_POSITION = new BigInteger("zzzzzz", RADIX);
        private final static BigInteger MINIMUM_POSITION = BigInteger.ZERO;

        private final BigInteger bigInt;

        private Position(final BigInteger bigInt) {
            this.bigInt = bigInt;
        }

        public Position(final String bigInt) {
            this.bigInt = new BigInteger(bigInt, RADIX);
        }

        public Position next() {
            BigInteger next = bigInt.add(BigInteger.ONE);
            if (next.compareTo(MAXIMUM_POSITION) > 0) {
                next = MINIMUM_POSITION;
            }
            return new Position(next);
        }

        public Position previous() {
            BigInteger previous = bigInt.subtract(BigInteger.ONE);
            if (previous.compareTo(MINIMUM_POSITION) < 0) {
                previous = MAXIMUM_POSITION;
            }
            return new Position(previous);
        }

        public BigInteger distanceToHead(final Position head) {
            final int compareToValue = head.bigInt.compareTo(this.bigInt);
            if (compareToValue == 0) {
                return BigInteger.ZERO;
            } else if (compareToValue == 1) {
                return head.bigInt.subtract(this.bigInt);
            }

            final BigInteger tailToMax = MAXIMUM_POSITION.subtract(this.bigInt);
            final BigInteger minToHead = head.bigInt.subtract(MINIMUM_POSITION);
            return minToHead.add(tailToMax).add(BigInteger.ONE);
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(bigInt.toString(RADIX).toUpperCase());
            while (sb.length() < 6) {
                sb.insert(0, "0");
            }
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Position position = (Position) o;

            return bigInt.equals(position.bigInt);
        }

        @Override
        public int hashCode() {
            return bigInt.hashCode();
        }
    }

    private static class InternalQueue {
        private final LocalDB localDB;
        private final LocalDB.DB DB;
        private volatile Position headPosition;
        private volatile Position tailPosition;
        private boolean developerDebug = false;
        private static final int DEBUG_MAX_ROWS = 50;
        private static final int DEBUG_MAX_WIDTH = 120;
        private static final Set<LocalDB.DB> DEBUG_IGNORED_DBs = Collections.unmodifiableSet(new HashSet(Arrays.asList(
                new LocalDB.DB[] {
                        LocalDB.DB.EVENTLOG_EVENTS
                }
        )));

        private final ReadWriteLock LOCK = new ReentrantReadWriteLock();

        private InternalQueue(final LocalDB localDB, final LocalDB.DB DB, final boolean developerDebug)
                throws LocalDBException {
            try {
                LOCK.writeLock().lock();
                if (localDB == null) {
                    throw new NullPointerException("LocalDB cannot be null");
                }

                if (localDB.status() != LocalDB.Status.OPEN) {
                    throw new IllegalStateException("LocalDB must hae a status of " + LocalDB.Status.OPEN);
                }

                if (DB == null) {
                    throw new NullPointerException("DB cannot be null");
                }

                this.developerDebug = developerDebug;
                this.localDB = localDB;
                this.DB = DB;
                init();
            } finally {
                LOCK.writeLock().unlock();
            }
        }

        private void init()
                throws LocalDBException {
            if (!checkVersion()) {
                clear();
            }

            final String headPositionStr = localDB.get(DB, KEY_HEAD_POSITION);
            final String tailPositionStr = localDB.get(DB, KEY_TAIL_POSITION);

            headPosition = headPositionStr != null && headPositionStr.length() > 0 ? new Position(headPositionStr) : new Position("0");
            tailPosition = tailPositionStr != null && tailPositionStr.length() > 0 ? new Position(tailPositionStr) : new Position("0");

            LOGGER.trace("loaded for db " + DB + "; headPosition=" + headPosition + ", tailPosition=" + tailPosition + ", size=" + this.size());

            repair();

            debugOutput("post init()");
        }

        private boolean checkVersion() throws LocalDBException {
            final String storedVersion = localDB.get(DB, KEY_VERSION);
            if (storedVersion == null || !VALUE_VERSION.equals(storedVersion)) {
                LOGGER.warn("values in db " + DB + " use an outdated format, the stored events will be purged!");
                return false;
            }
            return true;
        }

        public void clear()
                throws LocalDBException {
            try {
                LOCK.writeLock().lock();
                localDB.truncate(DB);

                headPosition = new Position("0");
                tailPosition = new Position("0");
                localDB.put(DB, KEY_HEAD_POSITION, headPosition.toString());
                localDB.put(DB, KEY_TAIL_POSITION, tailPosition.toString());

                localDB.put(DB, KEY_VERSION, VALUE_VERSION);

                debugOutput("post clear()");
            } finally {
                LOCK.writeLock().unlock();
            }
        }

        public int size()
                throws LocalDBException
        {
            try {
                LOCK.readLock().lock();
                return internalSize();
            } finally {
                LOCK.readLock().unlock();
            }
        }

        private int internalSize()
                throws LocalDBException
        {
            if (headPosition.equals(tailPosition) && localDB.get(DB, headPosition.toString()) == null) {
                return 0;
            }
            return tailPosition.distanceToHead(headPosition).intValue() + 1;
        }

        public List<String> removeFirst(final int removalCount) throws LocalDBException {
            try {
                LOCK.writeLock().lock();

                debugOutput("pre removeFirst()");

                if (removalCount < 1) {
                    Collections.emptyList();
                }

                final List<String> removalKeys = new ArrayList<>();
                final List<String> removedValues = new ArrayList<>();
                Position previousHead = headPosition;
                int removedPositions = 0;
                while (removedPositions < removalCount) {
                    removalKeys.add(previousHead.toString());
                    final String loopValue = localDB.get(DB, previousHead.toString());
                    if (loopValue != null) {
                        removedValues.add(loopValue);
                    }
                    previousHead = previousHead.equals(tailPosition) ? previousHead : previousHead.previous();
                    removedPositions++;
                }
                localDB.removeAll(DB, removalKeys);
                localDB.put(DB, KEY_HEAD_POSITION, previousHead.toString());
                headPosition = previousHead;

                debugOutput("post removeFirst()");
                return Collections.unmodifiableList(removedValues);
            } finally {
                LOCK.writeLock().unlock();
            }
        }

        public List<String> removeLast(final int removalCount) throws LocalDBException {
            try {
                LOCK.writeLock().lock();

                debugOutput("pre removeLast()");

                if (removalCount < 1) {
                    Collections.emptyList();
                }

                final List<String> removalKeys = new ArrayList<>();
                final List<String> removedValues = new ArrayList<>();
                Position nextTail = tailPosition;
                int removedPositions = 0;
                while (removedPositions < removalCount) {
                    removalKeys.add(nextTail.toString());
                    final String loopValue = localDB.get(DB, nextTail.toString());
                    if (loopValue != null) {
                        removedValues.add(loopValue);
                    }
                    nextTail = nextTail.equals(headPosition) ? nextTail : nextTail.next();
                    removedPositions++;
                }
                localDB.removeAll(DB, removalKeys);
                localDB.put(DB, KEY_TAIL_POSITION, nextTail.toString());
                tailPosition = nextTail;

                debugOutput("post removeLast()");
                return Collections.unmodifiableList(removedValues);
            } finally {
                LOCK.writeLock().unlock();
            }
        }

        public void addFirst(final Collection<String> values)
                throws LocalDBException
        {
            try {
                LOCK.writeLock().lock();
                debugOutput("pre addFirst()");

                if (values == null || values.isEmpty()) {
                    return;
                }

                if (internalSize() + values.size() > MAX_SIZE) {
                    throw new IllegalStateException("queue overflow");
                }

                final Iterator<String> valueIterator = values.iterator();

                final Map<String, String> keyValueMap = new HashMap<>();
                Position nextHead = headPosition;

                if (internalSize() == 0) {
                    keyValueMap.put(nextHead.toString(), valueIterator.next());
                }

                while (valueIterator.hasNext()) {
                    nextHead = nextHead.next();
                    keyValueMap.put(nextHead.toString(), valueIterator.next());
                }

                localDB.putAll(DB, keyValueMap);
                localDB.put(DB, KEY_HEAD_POSITION, String.valueOf(nextHead));
                headPosition = nextHead;

                debugOutput("post addFirst()");
            } finally {
                LOCK.writeLock().unlock();
            }
        }

        public void addLast(final Collection<String> values) throws LocalDBException {
            try {
                LOCK.writeLock().lock();
                debugOutput("pre addLast()");
                if (values == null || values.isEmpty()) {
                    return;
                }

                if (internalSize() + values.size() > MAX_SIZE) {
                    throw new IllegalStateException("queue overflow");
                }

                final Iterator<String> valueIterator = values.iterator();

                final Map<String, String> keyValueMap = new HashMap<>();
                Position nextTail = tailPosition;

                if (internalSize() == 0) {
                    keyValueMap.put(nextTail.toString(), valueIterator.next());
                }

                while (valueIterator.hasNext()) {
                    nextTail = nextTail.previous();
                    keyValueMap.put(nextTail.toString(), valueIterator.next());
                }

                localDB.putAll(DB, keyValueMap);
                localDB.put(DB, KEY_TAIL_POSITION, String.valueOf(nextTail));
                tailPosition = nextTail;

                debugOutput("post addLast()");
            } finally {
                LOCK.writeLock().unlock();
            }
        }

        public List<String> getFirst(int getCount)
                throws LocalDBException {
            try {
                LOCK.readLock().lock();
                debugOutput("pre getFirst()");

                if (getCount < 1) {
                    return Collections.emptyList();
                }

                if (getCount > internalSize()) {
                    getCount = internalSize();
                }

                final List<String> returnList = new ArrayList<>();

                Position nextHead = headPosition;
                while (returnList.size() < getCount) {
                    returnList.add(localDB.get(DB, nextHead.toString()));
                    nextHead = nextHead.previous();
                }

                debugOutput("post getFirst()");

                return returnList;
            } finally {
                LOCK.readLock().unlock();
            }
        }

        public List<String> getLast(int getCount)
                throws LocalDBException {
            try {
                LOCK.readLock().lock();

                debugOutput("pre getLast()");

                if (getCount < 1) {
                    return Collections.emptyList();
                }

                if (getCount > internalSize()) {
                    getCount = internalSize();
                }

                final List<String> returnList = new ArrayList<>();

                Position nextTail = tailPosition;
                while (returnList.size() < getCount) {
                    returnList.add(localDB.get(DB, nextTail.toString()));
                    nextTail = nextTail.next();
                }

                debugOutput("post getLast()");

                return returnList;
            } finally {
                LOCK.readLock().unlock();
            }
        }

        public void debugOutput(final String input) {
            if (!developerDebug || DEBUG_IGNORED_DBs.contains(DB)) {
                return;
            }

            final StringBuilder sb = new StringBuilder();
            try {
                sb.append(input);
                sb.append("  tailPosition=").append(tailPosition).append(", headPosition=").append(headPosition).append(", db=").append(DB);
                sb.append(", size=").append(internalSize()).append("\n");

                LocalDB.LocalDBIterator<String> keyIter = null;
                try {
                    keyIter = localDB.iterator(DB);
                    int rowCount = 0;
                    while (keyIter.hasNext() && rowCount < DEBUG_MAX_ROWS) {
                        final String key = keyIter.next();
                        String value = localDB.get(DB, key);
                        value = value == null ? "" : value;
                        value = value.length() < DEBUG_MAX_WIDTH ? value : value.substring(0, DEBUG_MAX_WIDTH) + "...";
                        String row = key + " " + value;
                        sb.append(row).append("\n");
                        rowCount++;
                    }
                } finally {
                    if (keyIter != null) {
                        keyIter.close();
                    }
                }


            } catch (LocalDBException e) {
                e.printStackTrace();
            }

            LOGGER.trace(sb.toString());
        }

        private void repair() throws LocalDBException {
            int headTrim = 0, tailTrim = 0;

            debugOutput("pre repair()");

            // trim the top.
            while (!headPosition.equals(tailPosition) && localDB.get(DB,headPosition.toString()) == null) {
                headPosition = headPosition.previous();
                localDB.put(DB, KEY_HEAD_POSITION, headPosition.toString());
                headTrim++;
            }

            // trim the bottom.
            while (!headPosition.equals(tailPosition) && localDB.get(DB,tailPosition.toString()) == null) {
                tailPosition = tailPosition.next();
                localDB.put(DB, KEY_TAIL_POSITION, tailPosition.toString());
                tailTrim++;
            }

            if (tailTrim == 0 && headTrim == 0) {
                LOGGER.trace("repair unnecessary for " + DB);
            } else {
                if (headTrim > 0) {
                    LOGGER.warn("trimmed " + headTrim + " from head position against database " + DB);
                }

                if (tailTrim > 0) {
                    LOGGER.warn("trimmed " + tailTrim + " from tail position against database " + DB);
                }
            }

        }
    }
}
