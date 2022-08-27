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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class FunctionalReentrantLock
{
    private final Lock readLock = new ReentrantLock();

    public FunctionalReentrantLock()
    {
    }

    public <T> T exec( final Supplier<T> block )
    {
        readLock.lock();
        try
        {
            return block.get();
        }
        finally
        {
            readLock.unlock();
        }
    }

    public void exec( final Runnable block )
    {
        readLock.lock();
        try
        {
            block.run();
        }
        finally
        {
            readLock.unlock();
        }
    }
}
