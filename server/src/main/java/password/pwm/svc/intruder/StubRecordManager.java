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

package password.pwm.svc.intruder;

import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.ClosableIterator;

import java.util.NoSuchElementException;

class StubRecordManager implements RecordManager
{
    @Override
    public boolean checkSubject( final String subject )
    {
        return false;
    }

    @Override
    public void markSubject( final String subject )
    {
    }

    @Override
    public void clearSubject( final String subject )
    {
    }

    @Override
    public boolean isAlerted( final String subject )
    {
        return false;
    }

    @Override
    public void markAlerted( final String subject )
    {
    }

    @Override
    public IntruderRecord readIntruderRecord( final String subject )
    {
        return null;
    }

    @Override
    public ClosableIterator<IntruderRecord> iterator( ) throws PwmOperationalException
    {
        return new ClosableIterator<IntruderRecord>()
        {
            @Override
            public boolean hasNext( )
            {
                return false;
            }

            @Override
            public IntruderRecord next( )
            {
                throw new NoSuchElementException();
            }

            @Override
            public void remove( )
            {
            }

            @Override
            public void close( )
            {
            }
        };
    }
}
