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

package password.pwm.config.stored;

import password.pwm.PwmConstants;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.ImmutableByteArray;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class StoredConfigZipXmlSerializer implements StoredConfigSerializer
{
    private static final String SETTINGS_FILENAME = "settings.xml";
    private static final String XREF_SUFFIX = ".xref";

    @Override
    public StoredConfiguration readInput( final InputStream inputStream )
            throws PwmUnrecoverableException, IOException
    {
        final ZipInputStream zipInputStream = new ZipInputStream( inputStream );

        final Map<String, ImmutableByteArray> exrefMap = new HashMap<>();

        StoredConfiguration storedConfiguration = null;

        ZipEntry zipEntry;
        while ( ( zipEntry = zipInputStream.getNextEntry() ) != null )
        {
            if ( SETTINGS_FILENAME.equals( zipEntry.getName() ) )
            {
                final ImmutableByteArray byteArray = JavaHelper.copyToBytes( zipInputStream );
                storedConfiguration = new StoredConfigXmlSerializer().readInput( byteArray.newByteArrayInputStream() );
            }
            else if ( zipEntry.getName().endsWith( XREF_SUFFIX ) )
            {
                final String hash = zipEntry.getName().substring( 0, zipEntry.getName().length() - XREF_SUFFIX.length() );
                final ImmutableByteArray contents = JavaHelper.copyToBytes( zipInputStream );
                exrefMap.put( hash, contents );
            }
            zipInputStream.closeEntry();

        }

        return injectXrefs( exrefMap, StoredConfigurationModifier.newModifier( storedConfiguration ) );
    }

    @Override
    public void writeOutput(
            final StoredConfiguration storedConfiguration,
            final OutputStream outputStream,
            final StoredConfigurationFactory.OutputSettings outputSettings
    )
            throws PwmUnrecoverableException, IOException
    {
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
        final Map<String, ImmutableByteArray> exrefMap = extractExRefs( modifier );
        final StoredConfiguration outputConfig = modifier.newStoredConfiguration();

        final ZipOutputStream zipOutputStream = new ZipOutputStream( outputStream );

        for ( final Map.Entry<String, ImmutableByteArray> entry : exrefMap.entrySet() )
        {
            zipOutputStream.putNextEntry( new ZipEntry( entry.getKey() + XREF_SUFFIX ) );
            zipOutputStream.write( entry.getValue().copyOf() );
            zipOutputStream.closeEntry();
        }

        {
            zipOutputStream.putNextEntry( new ZipEntry( SETTINGS_FILENAME ) );
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            new StoredConfigXmlSerializer().writeOutput( outputConfig, byteArrayOutputStream, outputSettings );
            zipOutputStream.write( byteArrayOutputStream.toByteArray() );
            zipOutputStream.closeEntry();
        }


        zipOutputStream.finish();
    }

    private StoredConfiguration injectXrefs(
            final Map<String, ImmutableByteArray> exRefMap,
            final StoredConfigurationModifier modifier
    )
            throws PwmUnrecoverableException
    {
        final StoredConfiguration inputConfig = modifier.newStoredConfiguration();
        for ( final StoredConfigItemKey key : inputConfig.modifiedItems() )
        {
            if (
                    StoredConfigItemKey.RecordType.SETTING.equals( key.getRecordType() )
                            && key.toPwmSetting().getSyntax().equals( PwmSettingSyntax.FILE )
            )
            {
                final Optional<StoredValue> optionalStoredValue = inputConfig.readStoredValue( key );
                if ( optionalStoredValue.isPresent() )
                {
                    final FileValue fileValue = ( FileValue ) optionalStoredValue.get();
                    final Map<FileValue.FileInformation, FileValue.FileContent> values = ( Map ) fileValue.toNativeObject();
                    final Map<FileValue.FileInformation, FileValue.FileContent> stripedValues = new HashMap<>();

                    for ( final Map.Entry<FileValue.FileInformation, FileValue.FileContent> entry : values.entrySet() )
                    {
                        final FileValue.FileInformation info = entry.getKey();
                        final FileValue.FileContent content = entry.getValue();
                        final String hash = new String( content.getContents().copyOf(), PwmConstants.DEFAULT_CHARSET );

                        final ImmutableByteArray realContents = exRefMap.get( hash );
                        if ( realContents != null )
                        {
                            final FileValue.FileContent realFileContent = new FileValue.FileContent( realContents );
                            stripedValues.put( info, realFileContent );
                        }
                    }

                    if ( !stripedValues.isEmpty() )
                    {
                        final FileValue strippedFileValue = new FileValue( stripedValues );
                        modifier.writeSetting( key.toPwmSetting(), key.getProfileID(),  strippedFileValue, null );
                    }
                }
            }
        }
        return modifier.newStoredConfiguration();
    }

    private Map<String, ImmutableByteArray> extractExRefs( final StoredConfigurationModifier modifier ) throws PwmUnrecoverableException
    {
        final Map<String, ImmutableByteArray> returnObj = new HashMap<>();
        final StoredConfiguration inputConfig = modifier.newStoredConfiguration();
        for ( final StoredConfigItemKey key : inputConfig.modifiedItems() )
        {
            if (
                    StoredConfigItemKey.RecordType.SETTING.equals( key.getRecordType() )
                            && key.toPwmSetting().getSyntax().equals( PwmSettingSyntax.FILE )
            )
            {
                final Optional<StoredValue> optionalStoredValue = inputConfig.readStoredValue( key );
                if ( optionalStoredValue.isPresent() )
                {
                    final FileValue fileValue = ( FileValue ) optionalStoredValue.get();
                    final Map<FileValue.FileInformation, FileValue.FileContent> values = ( Map ) fileValue.toNativeObject();
                    final Map<FileValue.FileInformation, FileValue.FileContent> stripedValues = new HashMap<>();

                    for ( final Map.Entry<FileValue.FileInformation, FileValue.FileContent> entry : values.entrySet() )
                    {
                        final FileValue.FileInformation info = entry.getKey();
                        final FileValue.FileContent content = entry.getValue();
                        final String hash = hash( content );
                        final FileValue.FileContent fileContentWithHash = new FileValue.FileContent( ImmutableByteArray.of( hash.getBytes( PwmConstants.DEFAULT_CHARSET ) ) );
                        returnObj.put( hash, content.getContents() );
                        stripedValues.put( info, fileContentWithHash );
                    }
                    final FileValue strippedFileValue = new FileValue( stripedValues );
                    modifier.writeSetting( key.toPwmSetting(), key.getProfileID(),  strippedFileValue, null );
                }
            }
        }
        return returnObj;
    }

    private static String hash( final FileValue.FileContent fileContent )
            throws PwmUnrecoverableException
    {
        return SecureEngine.hash( fileContent.getContents().copyOf(), PwmHashAlgorithm.SHA256 );
    }
}
