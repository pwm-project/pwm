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

package password.pwm.i18n;

public enum PwmSetting implements PwmDisplayBundle
{
    value,;

    public static final String SETTING_LABEL_PREFIX = "Setting_Label_";
    public static final String SETTING_DESCRIPTION_PREFIX = "Setting_Description_";

    public static final String CATEGORY_LABEL_PREFIX = "Category_Label_";
    public static final String CATEGORY_DESCRIPTION_PREFIX = "Category_Description_";

    @Override
    public String getKey( )
    {
        return this.toString();
    }
}
