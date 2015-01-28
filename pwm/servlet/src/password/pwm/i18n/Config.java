/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.i18n;

import password.pwm.config.Configuration;

import java.util.Locale;

public enum Config implements PwmDisplayBundle {
    Button_Next,
    Button_Previous,
    Button_CheckSettings,
    Button_ShowAdvanced,
    Button_HideAdvanced,
    Confirm_LockConfig,
    Confirm_SkipGuide,
    Confirm_UploadConfig,
    Confirm_UploadLocalDB,
    Confirm_SSLDisable,
    Display_AboutTemplates,
    Display_ConfigEditorLocales,
    Display_ConfigGuideNotSecureLDAP,
    Display_ConfigGuideSelectTemplate,
    Display_ConfigGuideSelectCrStorage,
    Display_ConfigGuideLdapSchema,
    Display_ConfigGuideLdapSchema2,
    Display_ConfigManagerConfiguration,
    Display_ConfigManagerNew,
    Display_ConfigManagerRunning,
    Display_ConfigManagerRunningEditor,
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
    MenuItem_Home,
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
    Tooltip_Setting_Permission_Base,
    
    ;

    public static String getLocalizedMessage(final Locale locale, final Config key, final Configuration config) {
        return LocaleHelper.getLocalizedMessage(locale, key.toString(), config, Config.class);
    }
    
    @Override
    public String getKey() {
        return this.toString();
        
    }
}