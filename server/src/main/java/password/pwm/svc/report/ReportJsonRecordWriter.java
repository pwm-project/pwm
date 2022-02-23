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

package password.pwm.svc.report;

import password.pwm.PwmConstants;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Optional;

class ReportJsonRecordWriter implements ReportRecordWriter
{
    private final Writer writer;
    private final JsonProvider jsonFactory = JsonFactory.get();

    ReportJsonRecordWriter( final OutputStream outputStream )
            throws IOException
    {
        this.writer = new OutputStreamWriter( outputStream, PwmConstants.DEFAULT_CHARSET );
    }

    @Override
    public String getZipName()
    {
        return "report.json";
    }

    @Override
    public void outputHeader() throws IOException
    {
        writer.write( "[\n" );
    }

    @Override
    public void outputRecord( final UserReportRecord userReportRecord, final boolean lastRecord ) throws IOException
    {
        writer.write( ' ' );

        final String jsonString = jsonFactory.serialize( userReportRecord, UserReportRecord.class, JsonProvider.Flag.PrettyPrint );
        final String indentedJson = " " + StringUtil.replaceAllChars( jsonString, character ->
        {
            if ( character.equals( '\n' ) )
            {
                return Optional.of( "\n  " );
            }
            return Optional.empty();
        } );

        writer.write( indentedJson );

        if ( !lastRecord )
        {
            writer.write( ',' );
        }

        writer.write( '\n' );
    }

    @Override
    public void outputFooter() throws IOException
    {
        writer.write( ']' );
    }

    @Override
    public void close() throws IOException
    {
        writer.flush();
    }
}
