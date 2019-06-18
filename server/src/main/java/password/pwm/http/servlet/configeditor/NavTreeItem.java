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

package password.pwm.http.servlet.configeditor;

import java.io.Serializable;
import java.util.Set;

class NavTreeItem implements Serializable
{
    private String id;
    private String name;
    private String parent;
    private String category;
    private NavTreeHelper.NavItemType type;
    private String profileSetting;
    private String menuLocation;
    private Set<String> keys;
    private String profile;

    public String getId( )
    {
        return id;
    }

    public void setId( final String id )
    {
        this.id = id;
    }

    public String getName( )
    {
        return name;
    }

    public void setName( final String name )
    {
        this.name = name;
    }

    public String getParent( )
    {
        return parent;
    }

    public void setParent( final String parent )
    {
        this.parent = parent;
    }

    public String getCategory( )
    {
        return category;
    }

    public void setCategory( final String category )
    {
        this.category = category;
    }

    public NavTreeHelper.NavItemType getType( )
    {
        return type;
    }

    public void setType( final NavTreeHelper.NavItemType type )
    {
        this.type = type;
    }

    public String getProfileSetting( )
    {
        return profileSetting;
    }

    public void setProfileSetting( final String profileSetting )
    {
        this.profileSetting = profileSetting;
    }

    public String getMenuLocation( )
    {
        return menuLocation;
    }

    public void setMenuLocation( final String menuLocation )
    {
        this.menuLocation = menuLocation;
    }

    public Set<String> getKeys( )
    {
        return keys;
    }

    public void setKeys( final Set<String> keys )
    {
        this.keys = keys;
    }

    public String getProfile( )
    {
        return profile;
    }

    public void setProfile( final String profile )
    {
        this.profile = profile;
    }
}
