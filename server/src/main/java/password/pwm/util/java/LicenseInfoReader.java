/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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
import password.pwm.error.PwmUnrecoverableException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LicenseInfoReader
{
    private LicenseInfoReader()
    {
    }

    public static List<DependencyInfo> getLicenseInfos() throws PwmUnrecoverableException
    {
        final List<String> attributionFiles = Arrays.asList( "/attribution.xml" );
        final List<DependencyInfo> returnList = new ArrayList<>();
        final XmlFactory factory = new XmlFactory.XmlFactoryW3c();

        for ( final String attributionFile : attributionFiles )
        {
            final InputStream attributionInputStream = XmlFactory.XmlFactoryJDOM.class.getResourceAsStream( attributionFile );

            if ( attributionInputStream != null )
            {
                final XmlDocument document = factory.parseXml( attributionInputStream );
                final XmlElement rootElement = document.getRootElement();
                final XmlElement dependenciesElement = rootElement.getChildren( "dependencies" ).iterator().next();

                for ( final XmlElement dependency : dependenciesElement.getChildren( "dependency" ) )
                {
                    final String projectUrl = dependency.getChildText( "projectUrl" );
                    final String name = dependency.getChildText( "name" );
                    final String artifactId = dependency.getChildText( "artifactId" );
                    final String version = dependency.getChildText( "version" );
                    final String type = dependency.getChildText( "type" );

                    final List<LicenseInfo> licenseInfos = new ArrayList<>();
                    {
                        final Optional<XmlElement> licenses = dependency.getChild( "licenses" );
                        if ( licenses.isPresent() )
                        {
                            final List<XmlElement> licenseList = licenses.get().getChildren( "license" );
                            for ( final XmlElement license : licenseList )
                            {
                                final String licenseUrl = license.getChildText( "url" );
                                final String licenseName = license.getChildText( "name" );
                                final LicenseInfo licenseInfo = new LicenseInfo( licenseUrl, licenseName );
                                licenseInfos.add( licenseInfo );
                            }
                        }
                    }

                    final DependencyInfo dependencyInfo = new DependencyInfo( projectUrl, name, artifactId, version, type,
                            Collections.unmodifiableList( licenseInfos ) );

                    returnList.add( dependencyInfo );
                }
            }
        }
        return Collections.unmodifiableList( returnList );
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
