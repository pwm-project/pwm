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

package password.pwm.util;

import password.pwm.PwmConstants;

import javax.swing.JOptionPane;
import java.util.Map;


public class JarMain
{

    public static void main( final String[] args )
    {
        System.out.println( buildInfoString() );

        JOptionPane.showMessageDialog
                ( null,
                        buildInfoString(),
                        "About",
                        JOptionPane.INFORMATION_MESSAGE );
    }

    private static String buildInfoString( )
    {
        final StringBuilder sb = new StringBuilder();

        sb.append( PwmConstants.PWM_APP_NAME + " v" + PwmConstants.BUILD_VERSION + "\n" );
        sb.append( "\n" );
        sb.append( "Build Information: \n" );

        for ( final Map.Entry<String, String> entry : PwmConstants.BUILD_MANIFEST.entrySet() )
        {
            sb.append( entry.getKey() );
            sb.append( "=" );
            sb.append( entry.getValue() );
            sb.append( "\n" );
        }

        sb.append( "\n" );
        sb.append( "Reference URL: " + PwmConstants.PWM_URL_HOME + "\n" );
        sb.append( "\n" );
        sb.append( "source files are included inside jar archive" );

        return sb.toString();
    }
}
