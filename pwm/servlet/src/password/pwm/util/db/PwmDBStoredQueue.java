/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

package password.pwm.util.db;

import password.pwm.util.PwmLogger;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PwmDBStoredQueue {
    private final static PwmLogger LOGGER = PwmLogger.getLogger(PwmDBStoredQueue.class);
    private final static int MAX_SIZE = Integer.MAX_VALUE - 3;


    private final static String KEY_HEAD_POSITION = "_HEAD_POSITION";
    private final static String KEY_TAIL_POSITION = "_TAIL_POSITION";
    private final static String KEY_VERSION = "_KEY_VERSION";
    private final static String VALUE_VERSION = "6";


    private final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    private final PwmDB pwmDB;
    private final PwmDB.DB DB;
    private volatile Position headPosition;
    private volatile Position tailPosition;

    public PwmDBStoredQueue(final PwmDB pwmDB, final PwmDB.DB DB)
            throws PwmDBException {
        this.pwmDB = pwmDB;
        this.DB = DB;
        init();
    }

    private void init()
            throws PwmDBException {
        if (!checkVersion()) {
            initializeNewSystem();
        }

        final String headPositionStr = pwmDB.get(DB, KEY_HEAD_POSITION);
        final String tailPositionStr = pwmDB.get(DB, KEY_TAIL_POSITION);

        headPosition = headPositionStr != null && headPositionStr.length() > 0 ? new Position(headPositionStr) : new Position("0");
        tailPosition = tailPositionStr != null && tailPositionStr.length() > 0 ? new Position(tailPositionStr) : new Position("0");

        /* {
            final int realSize = pwmDB.size(DB);
            final int computedSize = this.size();
            System.out.println("computedSize = " + computedSize);
            System.out.println("realSize = " + realSize);
        } */

        LOGGER.debug("loaded for db " + DB + "; headPosition=" + headPosition + ", tailPosition=" + tailPosition);
    }

    private boolean checkVersion() throws PwmDBException {
        final String storedVersion = pwmDB.get(DB, KEY_VERSION);
        if (storedVersion == null || !VALUE_VERSION.equals(storedVersion)) {
            LOGGER.warn("values in db " + DB + " use an outdated format, the stored events will be purged!");
            return false;
        }
        return true;
    }

    public void clear() throws PwmDBException {
        initializeNewSystem();
    }


    private void initializeNewSystem()
            throws PwmDBException {
        pwmDB.truncate(DB);

        headPosition = new Position("0");
        tailPosition = new Position("0");
        pwmDB.put(DB, KEY_HEAD_POSITION, headPosition.toString());
        pwmDB.put(DB, KEY_TAIL_POSITION, tailPosition.toString());

        pwmDB.put(DB, KEY_VERSION, VALUE_VERSION);
    }

    public void add(final List<String> values)
            throws PwmDBException {
        if (values == null) {
            return;
        }

        if (values.size() > MAX_SIZE) {
            throw new IllegalArgumentException("cannot add values size larger than MAX_SIZE");
        }

        if (size() + values.size() > MAX_SIZE) {
            removeTail(size() + values.size() - MAX_SIZE);
        }

        final Lock lock = LOCK.writeLock();
        lock.lock();
        try {
            final Map<String, String> keyValueMap = new HashMap<String, String>();
            Position nextHead = headPosition;
            for (final String loopValue : values) {
                keyValueMap.put(nextHead.toString(), loopValue);
                nextHead = nextHead.next();
            }

            pwmDB.putAll(DB, keyValueMap);
            pwmDB.put(DB, KEY_HEAD_POSITION, String.valueOf(nextHead));
            headPosition = nextHead;
        } finally {
            lock.unlock();
        }
    }

    public void removeTail(final int removalCount) throws PwmDBException {
        if (headPosition == tailPosition) {
            return;
        }

        final Lock lock = LOCK.writeLock();
        lock.lock();
        try {
            final List<String> removalKeys = new ArrayList<String>();
            Position nextTail = tailPosition;
            while (removalKeys.size() < removalCount && headPosition != tailPosition) {
                removalKeys.add(nextTail.toString());
                nextTail = nextTail.next();
            }
            pwmDB.removeAll(DB, removalKeys);
            pwmDB.put(DB, KEY_TAIL_POSITION, String.valueOf(nextTail));
            tailPosition = nextTail;
        } finally {
            lock.unlock();
        }
    }

    public String head() throws PwmDBException {
        return readKey(headPosition);
    }

    public String tail() throws PwmDBException {
        return readKey(tailPosition);
    }

    /**
     * Determines the item count based on difference between the tail position and head position
     *
     * @return calculated item count;
     */
    public int size() {
        final Lock lock = LOCK.readLock();
        lock.lock();
        try {
            return tailPosition.distanceToHead(headPosition).intValue();
        } finally {
            lock.unlock();
        }
    }

    private String readKey(final Position position)
            throws PwmDBException {
        final Lock lock = LOCK.readLock();
        lock.lock();
        try {
            return pwmDB.get(DB, position.toString());
        } finally {
            lock.unlock();
        }
    }

    public Iterator<String> iterator() {
        return new InnerIterator(headPosition);
    }

    private class InnerIterator implements Iterator {
        private Position position;
        private boolean nullFound;

        private InnerIterator(final Position position) {
            this.position = position;
        }

        public boolean hasNext() {
            return !nullFound;
        }

        public Object next() {
            final Lock lock = LOCK.readLock();
            lock.lock();
            try {
                final String value = readKey(position);
                position = position.previous();
                if (value == null) {
                    nullFound = true;
                }
                return value;
            } catch (PwmDBException e) {
                return null;
            } finally {
                lock.unlock();
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
            return minToHead.add(tailToMax);
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
}
