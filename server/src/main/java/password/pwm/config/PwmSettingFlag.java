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

package password.pwm.config;

/**
 * Flags defined for {@link PwmSetting} values.  Flags typically correspond to one or more {@link PwmSettingSyntax} types.
 */
public enum PwmSettingFlag
{
    /* Marker to indicate in setting UI and generated docs that setting supports macros */
    MacroSupport,

    /* Setting uses LDAP DN syntax */
    ldapDNsyntax,

    /* Setting must be a valid email address format */
    emailSyntax,

    /* No Default - Makes the setting UI act as if there is not a default to reset to */
    NoDefault,

    Select_AllowUserInput,

    Permission_HideGroups,
    Permission_HideMatch,

    Form_HideOptions,
    Form_ShowUniqueOption,
    Form_ShowReadOnlyOption,
    Form_ShowRequiredOption,
    Form_ShowMultiValueOption,
    Form_HideStandardOptions,
    Form_ShowSource,

    Verification_HideMinimumOptional,

    WebService_NoBody,

    Deprecated,
}
