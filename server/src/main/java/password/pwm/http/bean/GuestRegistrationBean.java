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

package password.pwm.http.bean;

import password.pwm.bean.UserIdentity;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.http.servlet.GuestRegistrationServlet;
import password.pwm.util.FormMap;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Jason D. Rivard, Menno Pieters
 */
public class GuestRegistrationBean extends PwmSessionBean
{

    private UserIdentity updateUserIdentity;
    private Instant updateUserExpirationDate;
    private FormMap formValues = new FormMap();
    private GuestRegistrationServlet.Page currentPage = GuestRegistrationServlet.Page.create;

    public UserIdentity getUpdateUserIdentity( )
    {
        return updateUserIdentity;
    }

    public void setUpdateUserIdentity( final UserIdentity updateUserIdentity )
    {
        this.updateUserIdentity = updateUserIdentity;
    }

    public Instant getUpdateUserExpirationDate( )
    {
        return updateUserExpirationDate;
    }

    public void setUpdateUserExpirationDate( final Instant updateUserExpirationDate )
    {
        this.updateUserExpirationDate = updateUserExpirationDate;
    }

    public GuestRegistrationServlet.Page getCurrentPage( )
    {
        return currentPage;
    }

    public void setCurrentPage( final GuestRegistrationServlet.Page currentPage )
    {
        this.currentPage = currentPage;
    }

    public FormMap getFormValues( )
    {
        return formValues;
    }

    public void setFormValues( final FormMap formValues )
    {
        this.formValues = formValues;
    }

    public Type getType( )
    {
        return Type.AUTHENTICATED;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.unmodifiableSet( new HashSet<>( Arrays.asList( SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE ) ) );
    }

}

