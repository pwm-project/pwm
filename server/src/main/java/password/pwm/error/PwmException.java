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

package password.pwm.error;

public abstract class PwmException extends Exception
{
    protected final ErrorInformation errorInformation;

    public PwmException( final ErrorInformation error )
    {
        this.errorInformation = error == null ? new ErrorInformation( PwmError.ERROR_INTERNAL ) : error;
    }

    public PwmException( final ErrorInformation error, final Throwable initialCause )
    {
        this.errorInformation = error == null ? new ErrorInformation( PwmError.ERROR_INTERNAL ) : error;
        this.initCause( initialCause );
    }

    public PwmException( final PwmError error )
    {
        this.errorInformation = new ErrorInformation( error );
    }

    public PwmException( final PwmError error, final String detailedErrorMsg )
    {
        this.errorInformation = new ErrorInformation( error, detailedErrorMsg );
    }

    public ErrorInformation getErrorInformation( )
    {
        return errorInformation;
    }

    public PwmError getError( )
    {
        return errorInformation.getError();
    }

    @Override
    public String getMessage( )
    {
        return errorInformation.toDebugStr();
    }
}
