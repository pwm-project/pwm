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

package password.pwm.svc.wordlist;

import password.pwm.util.java.StringUtil;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

class WordlistUtil
{
    static Set<String> chunkWord( final String input, final int chunkSize )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return Collections.emptySet();
        }

        if ( chunkSize == 0 || chunkSize >= input.length() )
        {
            return Collections.singleton( input );
        }

        final TreeSet<String> testWords = new TreeSet<>();
        final int maxIndex = input.length() - chunkSize;
        for ( int i = 0; i <= maxIndex; i++ )
        {
            final String loopWord = input.substring( i, i + chunkSize );
            testWords.add( loopWord );
        }

        return testWords;
    }

    static Optional<String> normalizeWordLength( final String input, final WordlistConfiguration wordlistConfiguration )
    {
        if ( input == null )
        {
            return Optional.empty();
        }

        String word = input.trim();

        if ( word.length() < wordlistConfiguration.getMinWordSize() )
        {
            return Optional.empty();
        }

        if ( word.length() > wordlistConfiguration.getMaxWordSize() )
        {
            word = word.substring( 0, wordlistConfiguration.getMaxWordSize() );
        }

        return word.length() > 0 ? Optional.of( word ) : Optional.empty();
    }


}
