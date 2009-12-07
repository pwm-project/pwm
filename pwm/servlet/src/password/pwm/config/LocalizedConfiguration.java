/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.config;

import com.novell.ldapchai.cr.ChallengeSet;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class LocalizedConfiguration {
// ------------------------------ FIELDS ------------------------------

    ChallengeSet challengeSet = null;

    EmailInfo newUserEmail = EmailInfo.EMPTY_EMAIL_INFO;
    EmailInfo changePasswordEmail = EmailInfo.EMPTY_EMAIL_INFO;

    Map<String, ParameterConfig> newUserCreationAttributes = Collections.emptyMap();
    Map<String, ParameterConfig> activateUserAttributes = Collections.emptyMap();
    Map<String, ParameterConfig> updateAttributesAttributes = Collections.emptyMap();
    Map<String, ParameterConfig> challengeRequiredAttributes = Collections.emptyMap();

    List<ShortcutItem> shortcutItems = Collections.emptyList();

    String applicationTitle;

// --------------------------- CONSTRUCTORS ---------------------------

    public LocalizedConfiguration()
    {
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public ChallengeSet getChallengeSet()
    {
        return challengeSet;
    }

    public EmailInfo getChangePasswordEmail()
    {
        return changePasswordEmail;
    }

    public EmailInfo getNewUserEmail()
    {
        return newUserEmail;
    }

    public List<ShortcutItem> getShortcutItems() {
        return shortcutItems;
    }

// ------------------------ CANONICAL METHODS ------------------------

    public String toString()
    {
        final StringBuilder sb = new StringBuilder();

        for (final Method method : this.getClass().getMethods()) {
            if ((method.getName().startsWith("is") || method.getName().startsWith("get")) && method.getName().toLowerCase().indexOf("password") == -1) {
                sb.append(method.getName()).append(": ");
                try {
                    sb.append(method.invoke(this));
                } catch (Exception e) { /*blah*/ }
                sb.append(", ");
            }
        }
        if (sb.length() > 2) {
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.toString();
    }

// -------------------------- OTHER METHODS --------------------------

    public Map<String, ParameterConfig> getActivateUserAttributes()
    {
        return Collections.unmodifiableMap(activateUserAttributes);
    }

    public Map<String, ParameterConfig> getNewUserCreationAttributes()
    {
        return Collections.unmodifiableMap(newUserCreationAttributes);
    }

    public Map<String, ParameterConfig> getUpdateAttributesAttributes()
    {
        return Collections.unmodifiableMap(updateAttributesAttributes);
    }

    public Map<String, ParameterConfig> getChallengeRequiredAttributes()
    {
        return Collections.unmodifiableMap(challengeRequiredAttributes);
    }

    public String getApplicationTitle() {
        return applicationTitle;
    }

    public void setApplicationTitle(String applicationTitle) {
        this.applicationTitle = applicationTitle;
    }
}
