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

import password.pwm.config.Configuration;
import password.pwm.util.i18n.LocaleHelper;

import java.util.Locale;

public enum Config implements PwmDisplayBundle
{

    Button_Next,
    Button_Previous,
    Button_CheckSettings,
    Button_ShowAdvanced,
    Button_HideAdvanced,
    Confirm_ConfigPasswordStored,
    Confirm_LockConfig,
    Confirm_SkipGuide,
    Confirm_UploadConfig,
    Confirm_UploadLocalDB,
    Confirm_SSLDisable,
    Display_AboutTemplates,
    Display_ConfigEditorLocales,
    Display_ConfigGuideNotSecureLDAP,
    Display_ConfigGuideSelectCrStorage,
    Display_ConfigGuideLdapSchema,
    Display_ConfigGuideLdapSchema2,
    Display_ConfigManagerConfiguration,
    Display_ConfigManagerNew,
    Display_ConfigManagerRunning,
    Display_ConfigManagerRunningEditor,
    Display_EditorLDAPSizeExceeded,
    Display_SettingFilter_Level_0,
    Display_SettingFilter_Level_1,
    Display_SettingFilter_Level_2,
    Display_SettingNavigationSeparator,
    Display_SettingNavigationNullProfile,
    Field_VerificationMethod,
    Label_ProfileListEditMenuItem,
    Label_UserPasswordAttribute,
    MenuDisplay_AlternateNewConfig,
    MenuDisplay_AlternateUnlockConfig,
    MenuDisplay_AlternateUpload,
    MenuDisplay_CancelEdits,
    MenuDisplay_ConfigEditor,
    MenuDisplay_DownloadConfig,
    MenuDisplay_DownloadConfigRunning,
    MenuDisplay_DownloadBundle,
    MenuDisplay_LockConfig,
    MenuDisplay_UnlockConfig,
    MenuDisplay_ExportLocalDB,
    MenuDisplay_MainMenu,
    MenuDisplay_ManualConfig,
    MenuDisplay_ReturnToEditor,
    MenuDisplay_SaveConfig,
    MenuDisplay_CancelConfig,
    MenuDisplay_StartConfigGuide,
    MenuDisplay_UploadConfig,
    MenuDisplay_ViewLog,
    MenuItem_AlternateNewConfig,
    MenuItem_AlternateUnlockConfig,
    MenuItem_CancelEdits,
    MenuItem_DownloadConfig,
    MenuItem_DownloadBundle,
    MenuItem_LockConfig,
    MenuItem_ExportLocalDB,
    MenuItem_MainMenu,
    MenuItem_ManualConfig,
    MenuItem_ReturnToEditor,
    MenuItem_StartConfigGuide,
    MenuItem_UploadConfig,
    MenuItem_UnlockConfig,
    MenuItem_ViewLog,
    Setting_Permission_Profile,
    Setting_Permission_Filter,
    Setting_Permission_Base,
    Setting_Permission_Base_Group,
    Title_ConfigGuide,
    Title_ConfigGuide_app,
    Title_ConfigGuide_ldap,
    Title_ConfigGuide_ldapcert,
    Title_ConfigGuide_ldap_schema,
    Title_ConfigGuide_start,
    Title_ConfigGuide_template,
    Title_ConfigGuide_crStorage,
    Title_ConfigManager,
    Warning_ChangeTemplate,
    Warning_ResetSetting,
    Warning_ShowAdvanced,
    Warning_ShowDescription,
    Warning_ShowNotes,
    Warning_HeaderVisibility,
    Warning_ConfigMustBeClosed,
    Warning_MakeSupportZipNoTrace,
    Warning_DownloadSupportZip,
    Warning_DownloadConfiguration,
    Warning_DownloadLocal,
    Warning_InvalidFormat,
    Warning_UploadIE9,
    Tooltip_ResetButton,
    Tooltip_HelpButton,
    Tooltip_Setting_Permission_Profile,
    Tooltip_Setting_Permission_Filter,
    Tooltip_Setting_Permission_Base,;

    public static String getLocalizedMessage( final Locale locale, final Config key, final Configuration config )
    {
        return LocaleHelper.getLocalizedMessage( locale, key.toString(), config, Config.class );
    }

    @Override
    public String getKey( )
    {
        return this.toString();

    }
}
