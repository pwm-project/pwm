/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2013 The PWM Project
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

package password.pwm.bean;

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;

import java.io.Serializable;

public class ConfigEditorCookie implements Serializable {
    private int level = 1;
    private boolean showDesc = false;
    private EDIT_MODE editMode = EDIT_MODE.SETTINGS;
    private PwmConstants.EDITABLE_LOCALE_BUNDLES localeBundle = PwmConstants.EDITABLE_LOCALE_BUNDLES.DISPLAY;
    private PwmSetting.Category category = PwmSetting.Category.LDAP;
    private PwmSetting.Group group = null;
    private boolean notesSeen;

    public ConfigEditorCookie() {
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public EDIT_MODE getEditMode() {
        return editMode;
    }

    public boolean isShowDesc() {
        return showDesc;
    }

    public void setShowDesc(boolean showDesc) {
        this.showDesc = showDesc;
    }

    public void setEditMode(EDIT_MODE editMode) {
        this.editMode = editMode;
    }

    public PwmSetting.Category getCategory() {
        return category;
    }

    public void setCategory(PwmSetting.Category category) {
        this.category = category;
    }



    public PwmConstants.EDITABLE_LOCALE_BUNDLES getLocaleBundle() {
        return localeBundle;
    }

    public void setLocaleBundle(final PwmConstants.EDITABLE_LOCALE_BUNDLES localeBundle) {
        this.localeBundle = localeBundle;
    }

    public boolean isNotesSeen() {
        return notesSeen;
    }

    public void setNotesSeen(boolean notesSeen) {
        this.notesSeen = notesSeen;
    }

    public static enum EDIT_MODE {
        SETTINGS,
        LOCALEBUNDLE,
    }
}
