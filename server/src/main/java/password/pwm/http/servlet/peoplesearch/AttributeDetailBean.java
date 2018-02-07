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

package password.pwm.http.servlet.peoplesearch;

import password.pwm.config.value.data.FormConfiguration;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

class AttributeDetailBean implements Serializable
{
    private String name;
    private String label;
    private FormConfiguration.Type type;
    private List<String> values;
    private Collection<UserReferenceBean> userReferences;
    private boolean searchable;

    public String getName( )
    {
        return name;
    }

    public void setName( final String name )
    {
        this.name = name;
    }

    public String getLabel( )
    {
        return label;
    }

    public void setLabel( final String label )
    {
        this.label = label;
    }

    public FormConfiguration.Type getType( )
    {
        return type;
    }

    public void setType( final FormConfiguration.Type type )
    {
        this.type = type;
    }

    public List<String> getValues( )
    {
        return values;
    }

    public void setValues( final List<String> values )
    {
        this.values = values;
    }

    public Collection<UserReferenceBean> getUserReferences( )
    {
        return userReferences;
    }

    public void setUserReferences( final Collection<UserReferenceBean> userReferences )
    {
        this.userReferences = userReferences;
    }

    public boolean isSearchable( )
    {
        return searchable;
    }

    public void setSearchable( final boolean searchable )
    {
        this.searchable = searchable;
    }


}
