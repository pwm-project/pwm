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

package password.pwm.http.bean;

import password.pwm.bean.UserIdentity;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.http.servlet.GuestRegistrationServlet;
import password.pwm.util.FormMap;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
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

    @Override
    public Type getType( )
    {
        return Type.AUTHENTICATED;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.unmodifiableSet( EnumSet.of( SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE ) );
    }

}

