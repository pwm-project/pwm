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

import lombok.Getter;
import lombok.Setter;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.error.ErrorInformation;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public abstract class PwmSessionBean implements Serializable
{
    public enum Type
    {
        PUBLIC,
        AUTHENTICATED,
    }

    private static List<Class<? extends PwmSessionBean>> publicBeans;

    static
    {
        final List<Class<? extends PwmSessionBean>> list = new ArrayList<>(  );
        list.add( ActivateUserBean.class );
        list.add( ForgottenPasswordBean.class );
        list.add( NewUserBean.class );
        publicBeans = Collections.unmodifiableList( list );
    }

    private String guid;
    private Instant timestamp;
    private ErrorInformation lastError;

    public abstract Type getType( );

    public abstract Set<SessionBeanMode> supportedModes( );

    public static List<Class<? extends PwmSessionBean>> getPublicBeans()
    {
        return publicBeans;
    }
}
