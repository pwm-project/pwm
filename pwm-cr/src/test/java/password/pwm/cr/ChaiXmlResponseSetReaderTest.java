/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.cr;

import org.junit.Test;
import password.pwm.cr.api.ChallengeItemPolicy;
import password.pwm.cr.api.QuestionSource;
import password.pwm.cr.api.ResponseLevel;


public class ChaiXmlResponseSetReaderTest
{

    @Test( expected = IllegalArgumentException.class )
    public void testBogusMaxLength() throws Exception
    {

        ChallengeItemPolicy.builder()
                .questionText( "question 1!" )
                .maxLength( -3 )
                .build().validate();
    }

    @Test
    public void testValidChallengeItemCreations()
    {
        ChallengeItemPolicy.builder()
                .questionText( "question 1!" )
                .minLength( 1 )
                .maxLength( 10 )
                .questionSource( QuestionSource.ADMIN_DEFINED )
                .responseLevel( ResponseLevel.REQUIRED )
                .build();

    }
}
