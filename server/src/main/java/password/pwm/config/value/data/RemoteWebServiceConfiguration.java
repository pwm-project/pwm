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

package password.pwm.config.value.data;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

@Value
@Builder( toBuilder = true )
public class RemoteWebServiceConfiguration implements Serializable
{

    public enum WebMethod
    {
        delete,
        get,
        post,
        put,
        patch
    }

    private String name;
    private WebMethod method = WebMethod.get;
    private Map<String, String> headers;
    private String url;
    private String body;
    private String username;
    private String password;
    private List<X509Certificate> certificates;
}
