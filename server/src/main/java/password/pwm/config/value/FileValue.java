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

package password.pwm.config.value;

import lombok.Value;
import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.ImmutableByteArray;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FileValue extends AbstractValue implements StoredValue
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FileValue.class );

    private Map<FileInformation, FileContent> values = new LinkedHashMap<>();

    public static class FileInformation implements Serializable
    {
        private String filename;
        private String filetype;

        public FileInformation(
                final String filename,
                final String filetype
        )
        {
            this.filename = filename;
            this.filetype = filetype;
        }

        public String getFilename( )
        {
            return filename;
        }

        public String getFiletype( )
        {
            return filetype;
        }
    }

    @Value
    public static class FileContent implements Serializable
    {
        private ImmutableByteArray contents;


        public static FileContent fromEncodedString( final String input )
                throws IOException
        {
            final byte[] convertedBytes = StringUtil.base64Decode( input );
            return new FileContent( new ImmutableByteArray( convertedBytes ) );
        }

        public String toEncodedString( )
                throws IOException
        {
            return StringUtil.base64Encode( contents.getBytes(), StringUtil.Base64Options.GZIP );
        }

        public String md5sum( )
                throws PwmUnrecoverableException
        {
            return SecureEngine.hash( new ByteArrayInputStream( contents.getBytes() ), PwmHashAlgorithm.MD5 );
        }

        public String sha1sum( )
                throws PwmUnrecoverableException
        {
            return SecureEngine.hash( new ByteArrayInputStream( contents.getBytes() ), PwmHashAlgorithm.SHA1 );
        }

        public int size( )
        {
            return contents.getBytes().length;
        }
    }

    public FileValue( final Map<FileInformation, FileContent> values )
    {
        this.values = values;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {

            public FileValue fromXmlElement( final Element settingElement, final PwmSecurityKey input )
                    throws PwmOperationalException
            {
                final List valueElements = settingElement.getChildren( "value" );
                final Map<FileInformation, FileContent> values = new LinkedHashMap<>();
                for ( final Object loopValue : valueElements )
                {
                    final Element loopValueElement = ( Element ) loopValue;

                    final Element loopFileInformation = loopValueElement.getChild( "FileInformation" );
                    if ( loopFileInformation != null )
                    {
                        final String loopFileInformationJson = loopFileInformation.getText();
                        final FileInformation fileInformation = JsonUtil.deserialize( loopFileInformationJson,
                                FileInformation.class );

                        final Element loopFileContentElement = loopValueElement.getChild( "FileContent" );
                        if ( loopFileContentElement != null )
                        {
                            final String fileContentString = loopFileContentElement.getText();
                            final FileContent fileContent;
                            try
                            {
                                fileContent = FileContent.fromEncodedString( fileContentString );
                                values.put( fileInformation, fileContent );
                            }
                            catch ( IOException e )
                            {
                                LOGGER.error( "error reading file contents item: " + e.getMessage(), e );
                            }
                        }
                    }
                }
                return new FileValue( values );
            }

            public StoredValue fromJson( final String input )
            {
                throw new IllegalStateException( "not implemented" );
            }
        };
    }

    public List<Element> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey )
    {
        final List<Element> returnList = new ArrayList<>();
        for ( final Map.Entry<FileInformation, FileContent> entry : this.values.entrySet() )
        {
            final FileValue.FileInformation fileInformation = entry.getKey();
            final FileContent fileContent = entry.getValue();
            final Element valueElement = new Element( valueElementName );

            final Element fileInformationElement = new Element( "FileInformation" );
            fileInformationElement.addContent( JsonUtil.serialize( fileInformation ) );
            valueElement.addContent( fileInformationElement );

            final Element fileContentElement = new Element( "FileContent" );
            try
            {
                fileContentElement.addContent( fileContent.toEncodedString() );
            }
            catch ( IOException e )
            {
                LOGGER.error( "unexpected error writing setting to xml, IO error during base64 encoding: " + e.getMessage() );
            }
            valueElement.addContent( fileContentElement );

            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public Object toNativeObject( )
    {
        return values;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwm )
    {
        return Collections.emptyList();
    }

    @Override
    public String toDebugString(
            final Locale locale
    )
    {
        final List<Map<String, Object>> output = asMetaData();
        return JsonUtil.serialize( ( Serializable ) output, JsonUtil.Flag.PrettyPrint );
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return ( Serializable ) asMetaData();
    }

    public List<Map<String, Object>> asMetaData( )
    {
        final List<Map<String, Object>> output = new ArrayList<>();
        for ( final Map.Entry<FileInformation, FileContent> entry : this.values.entrySet() )
        {
            final FileValue.FileInformation fileInformation = entry.getKey();
            final FileContent fileContent = entry.getValue();
            final Map<String, Object> details = new LinkedHashMap<>();
            details.put( "name", fileInformation.getFilename() );
            details.put( "type", fileInformation.getFiletype() );
            details.put( "size", fileContent.size() );
            try
            {
                details.put( "md5sum", fileContent.md5sum() );
            }
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.trace( "error generating file hash" );
            }
            output.add( details );
        }
        return output;
    }

    public List<FileInfo> toInfoMap( )
    {
        if ( values == null )
        {
            return Collections.emptyList();
        }
        final List<FileInfo> returnObj = new ArrayList<>();
        for ( final Map.Entry<FileInformation, FileContent> entry : this.values.entrySet() )
        {
            final FileValue.FileInformation fileInformation = entry.getKey();
            final FileContent fileContent = entry.getValue();
            final FileInfo loopInfo = new FileInfo();
            loopInfo.name = fileInformation.getFilename();
            loopInfo.type = fileInformation.getFiletype();
            loopInfo.size = fileContent.size();
            try
            {
                loopInfo.md5sum = fileContent.md5sum();
                loopInfo.sha1sum = fileContent.sha1sum();
            }
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.warn( "error generating hash for certificate: " + e.getMessage() );
            }
            returnObj.add( loopInfo );
        }
        return Collections.unmodifiableList( returnObj );
    }

    @Override
    public String valueHash( ) throws PwmUnrecoverableException
    {
        return SecureEngine.hash( JsonUtil.serializeCollection( toInfoMap() ), PwmConstants.SETTING_CHECKSUM_HASH_METHOD );
    }

    public static class FileInfo implements Serializable
    {
        public String name;
        public String type;
        public int size;
        public String md5sum;
        public String sha1sum;

        private FileInfo( )
        {
        }

        public String getName( )
        {
            return name;
        }

        public String getType( )
        {
            return type;
        }

        public int getSize( )
        {
            return size;
        }

        public String getMd5sum( )
        {
            return md5sum;
        }

        public String getSha1sum( )
        {
            return sha1sum;
        }
    }
}
