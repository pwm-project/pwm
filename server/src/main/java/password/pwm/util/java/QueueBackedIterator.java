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

package password.pwm.util.java;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

/**
 * A simple iterator that takes a collection as input, and uses an internal queue to iterate, thus allowing
 * the collection contents to be garbage collected as the iterator is consumed.  This is only effective if the
 * collector used in the constructor is itself garbage collectable after the {@link QueueBackedIterator}
 * is created.
 */
public class QueueBackedIterator<T> implements Iterator<T>
{
    final Queue<T> memQueue = new ArrayDeque<>();

    public QueueBackedIterator( final Collection<T> input )
    {
        memQueue.addAll( input );
    }

    @Override
    public boolean hasNext()
    {
        return memQueue.peek() != null;
    }

    @Override
    public T next()
    {
        return memQueue.poll();
    }
}
