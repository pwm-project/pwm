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

package password.pwm.util.secure;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import password.pwm.config.AppConfig;

public class BCrypt
{
    private final AppConfig appConfig;

    private BCrypt( final AppConfig appConfig )
    {
        this.appConfig = appConfig;
    }

    public static BCrypt createBCrypt( final AppConfig appConfig )
    {
        return new BCrypt( appConfig );
    }

    public String hashPassword( final String password )
    {
        final int bcryptRounds = 10;
        final byte[] salt = PwmRandom.getInstance().newBytes( 16 );
        return OpenBSDBCrypt.generate( password.toLowerCase().toCharArray(), salt, bcryptRounds );
    }

    public boolean testAnswer( final String password, final String hashedPassword )
    {
        final char[] pwCharArray = password.toLowerCase().toCharArray();
        return OpenBSDBCrypt.checkPassword( hashedPassword, pwCharArray );
    }
}
