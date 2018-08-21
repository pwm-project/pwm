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
