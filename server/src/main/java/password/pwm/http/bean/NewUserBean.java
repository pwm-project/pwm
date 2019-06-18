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

package password.pwm.http.bean;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.http.servlet.newuser.NewUserForm;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class NewUserBean extends PwmSessionBean
{
    @SerializedName( "p" )
    private String profileID;

    @SerializedName( "f" )
    private NewUserForm newUserForm = new NewUserForm( new HashMap<>(), null, null );

    @SerializedName( "r" )
    private Map<String, String> remoteInputData;

    @SerializedName( "ap" )
    private boolean agreementPassed;

    @SerializedName( "fp" )
    private boolean formPassed;

    @SerializedName( "t" )
    private Instant createStartTime;

    @SerializedName( "u" )
    private boolean urlSpecifiedProfile;

    @SerializedName( "ct" )
    private String currentTokenField;

    @SerializedName( "ft" )
    private Set<String> completedTokenFields = new HashSet<>();

    @SerializedName( "ts" )
    private boolean tokenSent;



    @Override
    public Type getType( )
    {
        return Type.PUBLIC;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.unmodifiableSet( new HashSet<>( Arrays.asList( SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE ) ) );
    }


}
