/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

package password.pwm;

import password.pwm.bean.EmailItemBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.PwmLogEvent;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class AlertHandler {
    public static void alertStartup(final ContextManager contextManager) {
        if (!checkIfEnabled(contextManager, PwmSetting.EVENTS_ALERT_STARTUP)) {
            return;
        }

        for (final String toAddress : contextManager.getConfig().readStringArraySetting(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
            final String fromAddress = contextManager.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
            final String subject = "PWM Alert - Startup";
            final StringBuilder body = new StringBuilder();
            body.append("event: Startup\n");
            body.append("instanceID: ").append(contextManager.getInstanceID()).append("\n");
            body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");

            final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
            contextManager.sendEmailUsingQueue(emailItem);
        }
    }

    public static void alertShutdown(final ContextManager contextManager) {
        if (!checkIfEnabled(contextManager, PwmSetting.EVENTS_ALERT_SHUTDOWN)) {
            return;
        }

        for (final String toAddress : contextManager.getConfig().readStringArraySetting(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
            final String fromAddress = contextManager.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
            final String subject = "PWM Alert - Shutdown";
            final StringBuilder body = new StringBuilder();
            body.append("event: Shutdown\n");
            body.append("instanceID: ").append(contextManager.getInstanceID()).append("\n");
            body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");

            final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
            contextManager.sendEmailUsingQueue(emailItem);
        }
    }

    public static void alertIntruder(final ContextManager contextManager, final Map<String,String> valueMap) {
        if (!checkIfEnabled(contextManager, PwmSetting.EVENTS_ALERT_INTRUDER_LOCKOUT)) {
            return;
        }

        for (final String toAddress : contextManager.getConfig().readStringArraySetting(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
        final String fromAddress = contextManager.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
        final String subject = "PWM Admin Alert - Intruder Detection";
        final StringBuilder body = new StringBuilder();
        body.append("event: Intruder Detection\n");
        body.append("instanceID: ").append(contextManager.getInstanceID()).append("\n");
        body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");

        for (final String key : valueMap.keySet()) {
            body.append(key);
            body.append(": ");
            body.append(valueMap.get(key));
            body.append("\n");
        }

        final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
        contextManager.sendEmailUsingQueue(emailItem);
        }
    }

    public static void alertFatalEvent(final ContextManager contextManager, final PwmLogEvent pwmLogEvent) {
        if (!checkIfEnabled(contextManager, PwmSetting.EVENTS_ALERT_FATAL_EVENT)) {
            return;
        }

        for (final String toAddress : contextManager.getConfig().readStringArraySetting(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
        final String fromAddress = contextManager.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
        final String subject = "PWM Alert - Fatal Event";
        final StringBuilder body = new StringBuilder();
        body.append("event: Fatal Event\n");
        body.append("instanceID: ").append(contextManager.getInstanceID()).append("\n");
        body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");
        body.append("level: ").append(pwmLogEvent.getLevel()).append("\n");
        body.append("actor: ").append(pwmLogEvent.getActor()).append("\n");
        body.append("date: ").append(pwmLogEvent.getDate()).append("\n");
        body.append("source: ").append(pwmLogEvent.getSource()).append("\n");
        body.append("topic: ").append(pwmLogEvent.getTopic()).append("\n");
        body.append("message: ").append(pwmLogEvent.getMessage()).append("\n");

        final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
        contextManager.sendEmailUsingQueue(emailItem);
        }
    }

    public static void alertConfigModify(final ContextManager contextManager, final Configuration config) {
        if (!checkIfEnabled(contextManager, PwmSetting.EVENTS_ALERT_CONFIG_MODIFY)) {
            return;
        }

        for (final String toAddress : contextManager.getConfig().readStringArraySetting(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
        final String fromAddress = contextManager.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
        final String subject = "PWM Alert - Configuration Modification";
        final StringBuilder body = new StringBuilder();
        body.append("event: Configuration Modification\n");
        body.append("instanceID: ").append(contextManager.getInstanceID()).append("\n");
        body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");
        body.append("configuration: \n\n");
        body.append(config.toDebugString());

        final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
        contextManager.sendEmailUsingQueue(emailItem);
        }
    }

    public static void alertDailyStats(final ContextManager contextManager, final Map<String,String> valueMap) {
        if (!checkIfEnabled(contextManager, PwmSetting.EVENTS_ALERT_DAILY_STATS)) {
            return;
        }

        for (final String toAddress : contextManager.getConfig().readStringArraySetting(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
        final String fromAddress = contextManager.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
        final String subject = "PWM Alert - Daily Statistics";
        final StringBuilder body = new StringBuilder();
        body.append("event: Daily Statistics\n");
        body.append("instanceID: ").append(contextManager.getInstanceID()).append("\n");
        body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");
        body.append("\n");

        final Map<String,String> sortedStats = new TreeMap<String,String>();
        sortedStats.putAll(valueMap);

        for (final String key : sortedStats.keySet()) {
            body.append(key);
            body.append(": ");
            body.append(valueMap.get(key));
            body.append("\n");
        }

        final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
        contextManager.sendEmailUsingQueue(emailItem);
        }
    }

    private static boolean checkIfEnabled(final ContextManager contextManager, final PwmSetting pwmSetting)  {
        if (contextManager == null) {
            return false;
        }

        if (contextManager.getConfig() == null) {
            return false;
        }

        final List<String> toAddress = contextManager.getConfig().readStringArraySetting(PwmSetting.EMAIL_ADMIN_ALERT_TO);
        final String fromAddress = contextManager.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);

        if (toAddress == null || toAddress.isEmpty() || toAddress.get(0) == null || toAddress.get(0).length() < 1) {
            return false;
        }

        if (fromAddress == null || fromAddress.length() < 1) {
            return false;
        }

        if (pwmSetting != null) {
            return contextManager.getConfig().readSettingAsBoolean(pwmSetting);
        }

        return true;
    }
}
