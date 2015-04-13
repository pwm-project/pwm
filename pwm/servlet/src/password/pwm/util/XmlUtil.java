/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util;

import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import java.io.*;
import java.nio.charset.Charset;

public class XmlUtil {

    public static final Charset STORAGE_CHARSET = Charset.forName("UTF8");

    public static Document parseXml(final InputStream inputStream)
            throws PwmUnrecoverableException
    {
        return parseXml(new InputStreamReader(inputStream, STORAGE_CHARSET));
    }

    public static Document parseXml(final Reader inputStream)
            throws PwmUnrecoverableException
    {
        final SAXBuilder builder = getBuilder();
        final Document inputDocument;
        try {
            inputDocument = builder.build(inputStream);
        } catch (Exception e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{"error parsing xml data: " + e.getMessage()}));
        }
        return inputDocument;
    }

    public static void outputDocument(final Document document, final OutputStream outputStream)
            throws IOException
    {
        final Format format = Format.getPrettyFormat();
        format.setEncoding(STORAGE_CHARSET.toString());
        final XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(format);
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(outputStream, STORAGE_CHARSET);
            outputter.output(document, writer);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static SAXBuilder getBuilder() {
        final SAXBuilder builder = new SAXBuilder();
        builder.setExpandEntities(false);
        builder.setValidation(false);
        builder.setFeature("http://xml.org/sax/features/resolve-dtd-uris", false);
        return builder;
    }


}
