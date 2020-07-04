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

package password.pwm.config.option;

public enum TLSVersion
{
    SSL_2_0( "SSLv2" ),
    SSL_3_0( "SSLv3" ),
    TLS_1_0( "TLSv1" ),
    TLS_1_1( "TLSv1.1" ),
    TLS_1_2( "TLSv1.2" ),
    TLS_1_3( "TLSv1.3" ),;

    private final String tomcatValueName;

    TLSVersion( final String tomcatValueName )
    {
        this.tomcatValueName = tomcatValueName;
    }

    public String getTomcatValueName( )
    {
        return tomcatValueName;
    }

}
