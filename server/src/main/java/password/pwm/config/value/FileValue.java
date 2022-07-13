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

package password.pwm.config.value;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlElement;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.data.ImmutableByteArray;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class FileValue extends AbstractValue implements StoredValue
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FileValue.class );

    private static final String XML_ELEMENT_FILE_INFORMATION = "FileInformation";
    private static final String XML_ELEMENT_FILE_CONTENT = "FileContent";

    private final Map<FileInformation, FileContent> values;

    @Value
    public static class FileInformation implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private final String filename;
        private final String filetype;
    }

    @EqualsAndHashCode
    public static class FileContent implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private final String b64EncodedContents;
        private final transient Supplier<ImmutableByteArray> byteContents;

        private FileContent( final String b64EncodedContents )
        {
            this.b64EncodedContents = b64EncodedContents;
            this.byteContents = new LazySupplier<>( () -> b64decode( b64EncodedContents ) );
        }

        public static FileContent fromEncodedString( final String input )
                throws IOException
        {
            final String whitespaceStripped = StringUtil.stripAllWhitespace( input );
            return new FileContent( whitespaceStripped );
        }

        public static FileContent fromBytes( final ImmutableByteArray contents )
                throws PwmUnrecoverableException
        {
            return new FileContent( b64encode( contents ) );
        }

        String toEncodedString( )
                throws IOException
        {
            return b64EncodedContents;
        }

        String sha512sum( )
                throws PwmUnrecoverableException
        {
            return SecureEngine.hash( byteContents.get().newByteArrayInputStream(), PwmHashAlgorithm.SHA512 );
        }

        public int size( )
        {
            return byteContents.get().size();
        }

        public ImmutableByteArray getContents()
        {
            return byteContents.get();
        }

        
    }

    public static FileValue newFileValue( final String filename, final String fileMimeType, final ImmutableByteArray contents )
            throws PwmUnrecoverableException
    {
        final FileInformation fileInformation = new FileValue.FileInformation( filename, fileMimeType );
        final FileContent fileContent = FileContent.fromBytes( contents );
        return new FileValue( Collections.singletonMap( fileInformation, fileContent ) );
    }

    public FileValue( final Map<FileInformation, FileContent> values )
    {
        this.values = values == null ? Collections.emptyMap() : Collections.unmodifiableMap( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public FileValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey input )
            {
                final List<XmlElement> valueElements = settingElement.getChildren( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                final Map<FileInformation, FileContent> values = new LinkedHashMap<>();
                for ( final XmlElement loopValueElement : valueElements )
                {
                    final Optional<XmlElement> loopFileInformation = loopValueElement.getChild( XML_ELEMENT_FILE_INFORMATION );
                    loopFileInformation.flatMap( XmlElement::getText ).ifPresent( loopFileInformationJson ->
                    {
                        final FileInformation fileInformation = JsonFactory.get().deserialize( loopFileInformationJson,
                                FileInformation.class );

                        final Optional<XmlElement> loopFileContentElement = loopValueElement.getChild( XML_ELEMENT_FILE_CONTENT );
                        loopFileContentElement.flatMap( XmlElement::getText ).ifPresent( fileContentString ->
                        {
                            final FileContent fileContent;
                            try
                            {
                                fileContent = FileContent.fromEncodedString( fileContentString );
                                values.put( fileInformation, fileContent );
                            }
                            catch ( final IOException e )
                            {
                                LOGGER.error( () -> "error reading file contents item: " + e.getMessage(), e );
                            }
                        } );
                    } );
                }
                return new FileValue( values );
            }

            @Override
            public StoredValue fromJson( final PwmSetting pwmSetting, final String input )
            {
                throw new IllegalStateException( "not implemented" );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues(
            final String valueElementName,
            final XmlOutputProcessData xmlOutputProcessData
    )
    {
        final List<XmlElement> returnList = new ArrayList<>( values.size() );
        for ( final Map.Entry<FileInformation, FileContent> entry : this.values.entrySet() )
        {
            final FileValue.FileInformation fileInformation = entry.getKey();
            final FileContent fileContent = entry.getValue();
            final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );

            final XmlElement fileInformationElement = XmlChai.getFactory().newElement( XML_ELEMENT_FILE_INFORMATION );
            fileInformationElement.setText( JsonFactory.get().serialize( fileInformation ) );
            valueElement.attachElement( fileInformationElement );

            final XmlElement fileContentElement = XmlChai.getFactory().newElement( XML_ELEMENT_FILE_CONTENT );

            try
            {
                final String encodedLineBreaks = StringUtil.insertRepeatedLineBreaks(
                        fileContent.toEncodedString(), PwmConstants.XML_OUTPUT_LINE_WRAP_LENGTH );
                fileContentElement.setText( encodedLineBreaks );
            }
            catch ( final IOException e )
            {
                LOGGER.error( () -> "unexpected error writing setting to xml, IO error during base64 encoding: " + e.getMessage() );
            }
            valueElement.attachElement( fileContentElement );

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
        return JsonFactory.get().serialize( ( Serializable ) output, JsonProvider.Flag.PrettyPrint );
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return ( Serializable ) asMetaData();
    }

    List<Map<String, Object>> asMetaData( )
    {
        final List<Map<String, Object>> output = new ArrayList<>( values.size() );
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
                details.put( "sha512sum", fileContent.sha512sum() );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.trace( () -> "error generating file hash" );
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
            try
            {
                returnObj.add( FileInfo.builder()
                        .name( fileInformation.getFilename() )
                        .type( fileInformation.getFiletype() )
                        .size( fileContent.size() )
                        .sha512sum( fileContent.sha512sum() )
                        .build() );
            }
            catch ( final PwmUnrecoverableException e )
            {
                throw new IllegalStateException( e );
            }
        }
        return Collections.unmodifiableList( returnObj );
    }

    @Value
    @Builder
    public static class FileInfo implements Serializable
    {
        private String name;
        private String type;
        private long size;
        private String sha512sum;
    }
}
