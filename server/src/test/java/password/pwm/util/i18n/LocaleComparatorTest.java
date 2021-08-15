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

package password.pwm.util.i18n;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LocaleComparatorTest
{
    @Test
    public void stringLocaleComparator()
    {
        final List<String> list = new ArrayList<>();
        list.add( "ja" );
        list.add( "en" );
        list.add( "br" );
        list.add( "fr" );

        list.sort( LocaleComparators.stringLocaleComparator() );

        Assert.assertEquals( "br", list.get( 0 ) );
        Assert.assertEquals( "en", list.get( 1 ) );
        Assert.assertEquals( "fr", list.get( 2 ) );
        Assert.assertEquals( "ja", list.get( 3 ) );

        list.sort( LocaleComparators.stringLocaleComparator( LocaleComparators.Flag.DefaultFirst ) );

        // default (english) first
        Assert.assertEquals( "en", list.get( 0 ) );
        Assert.assertEquals( "br", list.get( 1 ) );
        Assert.assertEquals( "fr", list.get( 2 ) );
        Assert.assertEquals( "ja", list.get( 3 ) );
    }
}
