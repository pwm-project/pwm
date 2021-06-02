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

package password.pwm.util.java;

import lombok.Value;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LicenseInfoReader
{
    private LicenseInfoReader()
    {
    }

    public static List<DependencyInfo> getLicenseInfos() throws PwmUnrecoverableException
    {
        final List<String> attributionFiles = Collections.singletonList( "/attribution.xml" );
        final List<DependencyInfo> returnList = new ArrayList<>();
        final XmlFactory factory = new XmlFactory.XmlFactoryW3c();

        for ( final String attributionFile : attributionFiles )
        {
            try ( InputStream attributionInputStream = LicenseInfoReader.class.getResourceAsStream( attributionFile ) )
            {
                if ( attributionInputStream != null )
                {
                    final XmlDocument document = factory.parseXml( attributionInputStream );
                    final XmlElement rootElement = document.getRootElement();
                    final XmlElement dependenciesElement = rootElement.getChildren( "dependencies" ).iterator().next();

                    for ( final XmlElement dependency : dependenciesElement.getChildren( "dependency" ) )
                    {
                        final DependencyInfo dependencyInfo = readDependencyInfo( dependency );
                        returnList.add( dependencyInfo );
                    }
                }
            }
            catch ( final IOException e )
            {
                final String errorMsg = "unexpected error reading stream license data: " + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMsg );
                throw new PwmUnrecoverableException( errorInfo );
            }
        }

        return Collections.unmodifiableList( returnList );
    }

    private static DependencyInfo readDependencyInfo( final XmlElement dependency )
    {
        final String projectUrl = dependency.getChildText( "projectUrl" );
        final String name = dependency.getChildText( "name" );
        final String artifactId = dependency.getChildText( "artifactId" );
        final String version = dependency.getChildText( "version" );
        final String type = dependency.getChildText( "type" );

        final List<LicenseInfo> licenseInfos = dependency.getChild( "licenses" )
                .map( LicenseInfoReader::readLicenses )
                .orElse( Collections.emptyList() );

        return new DependencyInfo( projectUrl, name, artifactId, version, type, licenseInfos );
    }

    private static List<LicenseInfo> readLicenses( final XmlElement licenses )
    {
        return Collections.unmodifiableList( licenses.getChildren( "license" )
                .stream()
                .map( LicenseInfoReader::readLicenseInfo )
                .collect( Collectors.toList() ) );
    }

    private static LicenseInfo readLicenseInfo( final XmlElement license )
    {
        final String licenseUrl = license.getChildText( "url" );
        final String licenseName = license.getChildText( "name" );
        return new LicenseInfo( licenseUrl, licenseName );
    }


    @Value
    public static class DependencyInfo
    {
        private String projectUrl;
        private String name;
        private String artifactId;
        private String version;
        private String type;
        private List<LicenseInfo> licenses;
    }

    @Value
    public static class LicenseInfo
    {
        private String licenseUrl;
        private String licenseName;
    }
}
