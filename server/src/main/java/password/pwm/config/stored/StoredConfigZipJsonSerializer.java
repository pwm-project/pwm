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

package password.pwm.config.stored;

import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.StoredValue;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.data.ImmutableByteArray;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonProvider;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class StoredConfigZipJsonSerializer implements StoredConfigSerializer
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigZipJsonSerializer.class );

    private static final String SETTINGS_FILENAME = "settings.json";
    private static final String META_VALUES_FILENAME = "meta-values.json";
    private static final String META_FILENAME = "meta.json";
    private static final String XREF_SUFFIX = ".xref";

    @Override
    public StoredConfiguration readInput( final InputStream inputStream )
            throws PwmUnrecoverableException, IOException
    {
        final IntermediateRepresentation intermediateRepresentation = readIntermediateRep( inputStream );

        final Map<StoredConfigKey, StoredValue> storedValueMap = new HashMap<>();
        for ( final SerializedValue serializedValue : intermediateRepresentation.getSerializedValues() )
        {
            try
            {
                final StoredConfigKey key = serializedValue.getKey();
                System.out.println( key.toString() );
                if ( key.getRecordType() == StoredConfigKey.RecordType.SETTING )
                {
                    System.out.println( key.toPwmSetting().getSyntax() );
                    System.out.println( key.toPwmSetting().getKey() );

                }
                final PwmSettingSyntax syntax = key.getSyntax();
                final StoredValue storedValue;
                if (
                        StoredConfigKey.RecordType.SETTING.equals( key.getRecordType() )
                                && key.toPwmSetting().getSyntax() == PwmSettingSyntax.FILE
                )
                {
                    final SerializedFileValue tempValue = JsonFactory.get().deserialize( serializedValue.getValueData(), SerializedFileValue.class );
                    final Map<FileValue.FileInformation, FileValue.FileContent> unstrippedMap = new HashMap<>( tempValue.getFileInformation().size() );

                    for ( final Map.Entry<String, FileValue.FileInformation> entry : tempValue.getFileInformation().entrySet() )
                    {
                        final String hash = entry.getKey();
                        final FileValue.FileInformation fileInformation = entry.getValue();
                        final ImmutableByteArray realContent = intermediateRepresentation.getExRefs().get( hash );
                        final FileValue.FileContent realFileContent = FileValue.FileContent.fromBytes( realContent );
                        unstrippedMap.put( fileInformation, realFileContent );
                    }

                    storedValue = new FileValue( unstrippedMap );
                }
                else
                {
                    storedValue = syntax.getFactory().fromJson( serializedValue.getValueData() );
                }
                storedValueMap.put( key, storedValue );
            }
            catch ( final Exception e )
            {
                final String msg = "error reading configuration file: " + e.getMessage();
                LOGGER.error( () -> msg, e );
                throw new PwmUnrecoverableException( PwmError.CONFIG_FORMAT_ERROR, msg );
            }
        }

        final Map<StoredConfigKey, ValueMetaData> valueMetaDataMap = new HashMap<>( intermediateRepresentation.serializedMetaValues.size() );
        for ( final SerializedMetaValue serializedMetaValue : intermediateRepresentation.serializedMetaValues )
        {
            valueMetaDataMap.put( serializedMetaValue.getKey(), serializedMetaValue.getValueMetaData() );
        }

        final StoredConfigData storedConfigData = StoredConfigData.builder()
                .storedValues( storedValueMap )
                .metaDatas( valueMetaDataMap )
                .createTime( intermediateRepresentation.getMetaData().getCreateTime() )
                .modifyTime( intermediateRepresentation.getMetaData().getModifyTime() )
                .build();

        return new StoredConfigurationImpl( storedConfigData );
    }

    private IntermediateRepresentation readIntermediateRep( final InputStream inputStream ) throws IOException
    {
        final Map<String, ImmutableByteArray> exRefs = new HashMap<>();
        final List<SerializedValue> serializedValues = new ArrayList<>();
        final List<SerializedMetaValue> serializedMetaValues = new ArrayList<>();
        MetaData metaData = null;

        final ZipInputStream zipInputStream = new ZipInputStream( inputStream );
        ZipEntry zipEntry;
        while ( ( zipEntry = zipInputStream.getNextEntry() ) != null )
        {
            if ( SETTINGS_FILENAME.equals( zipEntry.getName() ) )
            {
                final String stringData = JavaHelper.copyToString( zipInputStream, PwmConstants.DEFAULT_CHARSET, Integer.MAX_VALUE )
                        .orElse( "" );
                final List<SerializedValue> readComponents = JsonFactory.get().deserializeList( stringData, SerializedValue.class );
                serializedValues.addAll( readComponents );
            }
            else if ( META_VALUES_FILENAME.equals( zipEntry.getName() ) )
            {
                final String stringData = JavaHelper.copyToString( zipInputStream, PwmConstants.DEFAULT_CHARSET, Integer.MAX_VALUE )
                        .orElse( "" );
                final List<SerializedMetaValue> readMetaValues = JsonFactory.get().deserializeList( stringData, SerializedMetaValue.class );
                serializedMetaValues.addAll( readMetaValues );
            }
            else if ( META_FILENAME.equals( zipEntry.getName() ) )
            {
                final String stringData = JavaHelper.copyToString( zipInputStream, PwmConstants.DEFAULT_CHARSET, Integer.MAX_VALUE )
                        .orElse( "" );
                metaData = JsonFactory.get().deserialize( stringData, MetaData.class );
            }
            else if ( zipEntry.getName().endsWith( XREF_SUFFIX ) )
            {
                final String hash = zipEntry.getName().substring( 0, zipEntry.getName().length() - XREF_SUFFIX.length() );
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                JavaHelper.copy( inputStream, byteArrayOutputStream );
                final byte[] contents = byteArrayOutputStream.toByteArray();
                exRefs.put( hash, ImmutableByteArray.of( contents ) );
            }
        }

        return new IntermediateRepresentation( serializedValues, serializedMetaValues, exRefs, metaData );
    }

    @Override
    public void writeOutput(
            final StoredConfiguration storedConfiguration,
            final OutputStream outputStream,
            final StoredConfigurationFactory.OutputSettings outputSettings
    )
            throws PwmUnrecoverableException, IOException
    {
        final IntermediateRepresentation intermediateRepresentation = makeIntermediateRepresentation( storedConfiguration, outputSettings );

        final ZipOutputStream zipOutputStream = new ZipOutputStream( outputStream );

        {
            zipOutputStream.putNextEntry( new ZipEntry( SETTINGS_FILENAME ) );
            JavaHelper.copy(
                    JsonFactory.get().serializeCollection(
                            intermediateRepresentation.getSerializedValues(),
                            JsonProvider.Flag.PrettyPrint ),
                    zipOutputStream,
                    PwmConstants.DEFAULT_CHARSET );
        }

        {
            zipOutputStream.putNextEntry( new ZipEntry( META_VALUES_FILENAME ) );
            JavaHelper.copy(
                    JsonFactory.get().serializeCollection(
                            intermediateRepresentation.getSerializedMetaValues(),
                            JsonProvider.Flag.PrettyPrint ),
                    zipOutputStream,
                    PwmConstants.DEFAULT_CHARSET );
        }

        {
            final MetaData metaData = new MetaData( storedConfiguration.createTime(), storedConfiguration.modifyTime(), "1" );
            zipOutputStream.putNextEntry( new ZipEntry( META_FILENAME ) );
            JavaHelper.copy(
                    JsonFactory.get().serialize(
                            metaData,
                            JsonProvider.Flag.PrettyPrint ),
                    zipOutputStream,
                    PwmConstants.DEFAULT_CHARSET );
        }

        for ( final Map.Entry<String, ImmutableByteArray> entry : intermediateRepresentation.getExRefs().entrySet() )
        {
            zipOutputStream.putNextEntry( new ZipEntry( entry.getKey() ) );
            zipOutputStream.write( entry.getValue().copyOf() );
        }

        zipOutputStream.finish();
    }

    private IntermediateRepresentation makeIntermediateRepresentation(
            final StoredConfiguration storedConfiguration,
            final StoredConfigurationFactory.OutputSettings outputSettings
    )
            throws PwmUnrecoverableException, IOException
    {
        final Map<String, ImmutableByteArray> exRefs = new LinkedHashMap<>();
        final List<SerializedValue> serializedValues = new ArrayList<>();
        final List<StoredConfigKey> keys = CollectionUtil.iteratorToStream( storedConfiguration.keys() ).collect( Collectors.toList() );
        for ( final StoredConfigKey key : keys )
        {
            final Optional<StoredValue> storedValue = storedConfiguration.readStoredValue( key );
            if ( storedValue.isPresent() )
            {
                final PwmSettingSyntax syntax;
                final StoredValue value;
                if (
                        StoredConfigKey.RecordType.SETTING.equals( key.getRecordType() )
                                && key.toPwmSetting().getSyntax() == PwmSettingSyntax.FILE
                )
                {
                    final StoredValue fileValue = storedValue.get();
                    final Map<FileValue.FileInformation, FileValue.FileContent> values = ( Map ) fileValue.toNativeObject();
                    final Map<FileValue.FileInformation, FileValue.FileContent> strippedValues = new HashMap<>( values.size() );
                    for ( final Map.Entry<FileValue.FileInformation, FileValue.FileContent> entry : values.entrySet() )
                    {
                        final FileValue.FileInformation info = entry.getKey();
                        final FileValue.FileContent content = entry.getValue();
                        final String hash = hash( content );
                        strippedValues.put( info, FileValue.FileContent.fromBytes( ImmutableByteArray.of( hash.getBytes( PwmConstants.DEFAULT_CHARSET ) ) ) );
                        exRefs.put( hash, content.getContents() );
                    }
                    value = new FileValue( strippedValues );
                    syntax = PwmSettingSyntax.FILE;
                }
                else
                {
                    value = storedValue.get();
                    syntax = key.getSyntax();
                }
                final String jsonValue = JsonFactory.get().serialize( ( Serializable ) value.toNativeObject() );
                final SerializedValue storedComponent = new SerializedValue( key, syntax, jsonValue );
                serializedValues.add( storedComponent );
            }
        }

        final List<SerializedMetaValue> metaValues = new ArrayList<>();
        for ( final StoredConfigKey key : keys )
        {
            final Optional<ValueMetaData> valueMetaData = storedConfiguration.readMetaData( key );
            valueMetaData.ifPresent( metaData -> metaValues.add( new SerializedMetaValue( key, metaData ) ) );
        }

        final MetaData metaData = new MetaData( storedConfiguration.createTime(), storedConfiguration.modifyTime(), "1" );
        return new IntermediateRepresentation(
                serializedValues,
                metaValues,
                exRefs,
                metaData );
    }

    private String hash( final FileValue.FileContent fileContent )
    {
        return SecureEngine.hash( fileContent.getContents().newByteArrayInputStream(), PwmHashAlgorithm.SHA256 );
    }


    @Value
    private static class IntermediateRepresentation
    {
        private List<SerializedValue> serializedValues;
        private List<SerializedMetaValue> serializedMetaValues;
        private Map<String, ImmutableByteArray> exRefs;
        private MetaData metaData;
    }

    @Value
    private static class SerializedValue implements Serializable
    {
        private StoredConfigKey key;
        private PwmSettingSyntax syntax;
        private String valueData;
    }

    @Value
    private static class SerializedFileValue implements Serializable
    {
        private Map<String, FileValue.FileInformation> fileInformation;
    }

    @Value
    private static class SerializedMetaValue implements Serializable
    {
        private StoredConfigKey key;
        private ValueMetaData valueMetaData;
    }

    @Value
    private static class MetaData implements Serializable
    {
        private String createTime;
        private Instant modifyTime;
        private String version;
    }
}
