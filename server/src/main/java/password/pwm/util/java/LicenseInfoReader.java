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

package password.pwm.util.java;

import lombok.Value;
import password.pwm.error.PwmUnrecoverableException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LicenseInfoReader
{
    private LicenseInfoReader()
    {
    }

    public static List<DependencyInfo> getLicenseInfos() throws PwmUnrecoverableException
    {
        final List<String> attributionFiles = Arrays.asList( "/server-attribution.xml", "/webapp-attribution.xml" );
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
                        final XmlElement licenses = dependency.getChild( "licenses" );
                        final List<XmlElement> licenseList = licenses.getChildren( "license" );
                        for ( final XmlElement license : licenseList )
                        {
                            final String licenseUrl = license.getChildText( "url" );
                            final String licenseName = license.getChildText( "name" );
                            final LicenseInfo licenseInfo = new LicenseInfo( licenseUrl, licenseName );
                            licenseInfos.add( licenseInfo );
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
