/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import password.pwm.health.HealthRecord;
import password.pwm.i18n.Display;
import password.pwm.util.PwmLogEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class AlertHandler {
    public static void alertStartup(final PwmApplication pwmApplication) {
        if (!checkIfEnabled(pwmApplication, PwmSetting.EVENTS_ALERT_STARTUP)) {
            return;
        }

        for (final String toAddress : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
            final String fromAddress = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
            final String subject = PwmConstants.PWM_APP_NAME + " Alert - Startup";
            final StringBuilder body = new StringBuilder();
            body.append("event: Startup\n");
            body.append("instanceID: ").append(pwmApplication.getInstanceID()).append("\n");
            body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");

            final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
            pwmApplication.sendEmailUsingQueue(emailItem,null,null);
        }
    }

    public static void alertShutdown(final PwmApplication pwmApplication) {
        if (!checkIfEnabled(pwmApplication, PwmSetting.EVENTS_ALERT_SHUTDOWN)) {
            return;
        }

        for (final String toAddress : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
            final String fromAddress = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
            final String subject = PwmConstants.PWM_APP_NAME + " Alert - Shutdown";
            final StringBuilder body = new StringBuilder();
            body.append("event: Shutdown\n");
            body.append("instanceID: ").append(pwmApplication.getInstanceID()).append("\n");
            body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");

            final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
            pwmApplication.sendEmailUsingQueue(emailItem,null,null);
        }
    }

    public static void alertIntruder(final PwmApplication pwmApplication, final Map<String, String> valueMap) {
        if (!checkIfEnabled(pwmApplication, PwmSetting.EVENTS_ALERT_INTRUDER_LOCKOUT)) {
            return;
        }

        for (final String toAddress : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
            final String fromAddress = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
            final String subject = PwmConstants.PWM_APP_NAME + " Admin Alert - Intruder Detection";
            final StringBuilder body = new StringBuilder();
            body.append("event: Intruder Detection\n");
            body.append("instanceID: ").append(pwmApplication.getInstanceID()).append("\n");
            body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");

            for (final String key : valueMap.keySet()) {
                body.append(key);
                body.append(": ");
                body.append(valueMap.get(key));
                body.append("\n");
            }

            final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
            pwmApplication.sendEmailUsingQueue(emailItem,null,null);
        }
    }

    public static void alertFatalEvent(final PwmApplication pwmApplication, final PwmLogEvent pwmLogEvent) {
        if (!checkIfEnabled(pwmApplication, PwmSetting.EVENTS_ALERT_FATAL_EVENT)) {
            return;
        }

        for (final String toAddress : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
            final String fromAddress = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
            final String subject = PwmConstants.PWM_APP_NAME + " Alert - Fatal Event";
            final StringBuilder body = new StringBuilder();
            body.append("event: Fatal Event\n");
            body.append("instanceID: ").append(pwmApplication.getInstanceID()).append("\n");
            body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");
            body.append("level: ").append(pwmLogEvent.getLevel()).append("\n");
            body.append("actor: ").append(pwmLogEvent.getActor()).append("\n");
            body.append("date: ").append(pwmLogEvent.getDate()).append("\n");
            body.append("source: ").append(pwmLogEvent.getSource()).append("\n");
            body.append("topic: ").append(pwmLogEvent.getTopic()).append("\n");
            body.append("message: ").append(pwmLogEvent.getMessage()).append("\n");

            final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
            pwmApplication.sendEmailUsingQueue(emailItem,null,null);
        }
    }

    public static void alertConfigModify(final PwmApplication pwmApplication, final Configuration config) {
        if (!checkIfEnabled(pwmApplication, PwmSetting.EVENTS_ALERT_CONFIG_MODIFY)) {
            return;
        }

        for (final String toAddress : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
            final String fromAddress = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
            final String subject = PwmConstants.PWM_APP_NAME + " Alert - Configuration Modification";
            final StringBuilder body = new StringBuilder();
            body.append("event: Configuration Modification\n");
            body.append("instanceID: ").append(pwmApplication.getInstanceID()).append("\n");
            body.append("timestamp: ").append(new java.util.Date().toString()).append("\n");
            body.append("configuration: \n\n");
            body.append(config.toDebugString());

            final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, body.toString(), null);
            pwmApplication.sendEmailUsingQueue(emailItem,null,null);
        }
    }

    public static void alertDailyStats(final PwmApplication pwmApplication, final Map<String, String> valueMap) {
        if (!checkIfEnabled(pwmApplication, PwmSetting.EVENTS_ALERT_DAILY_SUMMARY)) {
            return;
        }

        for (final String toAddress : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EMAIL_ADMIN_ALERT_TO)) {
            final String fromAddress = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);
            final String subject = Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,"Title_Application",pwmApplication.getConfig()) + " - Daily Summary";
            final StringBuilder textBody = new StringBuilder();
            final StringBuilder htmlBody = new StringBuilder();

            htmlBody.append("<html><head>");
            htmlBody.append("<style type='text/css'");
            htmlBody.append(
                    "\n" +
                            "html, body { font-family:Arial, Helvetica, sans-serif; color:#333333; font-size:12px; height:100%; margin:0 }\n" +
                            "\n" +
                            "a { color:#2D2D2D; text-decoration:underline; font-weight:bold }\n" +
                            "p { max-width: 600px; color:#2D2D2D; position:relative; margin-left: auto; margin-right: auto}\n" +
                            "hr { float: none; width:100px; position:relative; margin-left:5px; margin-top: 30px; margin-bottom: 30px; }\n" +
                            "\n" +
                            "h1 { font-size:16px; }\n" +
                            "h2 { font-size:14px; }\n" +
                            "h3 { font-size:12px; }\n" +
                            "\n" +
                            "select { font-family:Trebuchet MS, sans-serif; width: 500px }\n" +
                            "\n" +
                            "table { border-collapse:collapse;  border: 2px solid #D4D4D4; width:100%; margin-left: auto; margin-right: auto }\n" +
                            "table td { border: 1px solid #D4D4D4; padding-left: 5px;}\n" +
                            "table td.title { text-align:center; font-weight: bold; font-size: 150%; padding-right: 10px; background-color:#DDDDDD }\n" +
                            "table td.key { text-align:right; font-weight:bold; padding-right: 10px; width: 200px;}\n" +
                            "\n" +
                            ".inputfield { width:400px; margin: 5px; height:18px }\n" +
                            "\n" +
                            "/* main body wrapper, all elements (except footer) should be within wrapper */\n" +
                            "#wrapper { width:100%; min-height: 100%; height: auto !important; height: 100%; margin: 0; }\n" +
                            "\n" +
                            "\n" +
                            "/* main content section, all content should be inside a centerbody div */\n" +
                            "#centerbody { width:600px; min-width:600px; padding:0; position:relative; ; margin-left:auto; margin-right:auto; margin-top: 10px; clear:both; padding-bottom:40px;}\n" +
                            "\n" +
                            "/* all forms use a buttonbar div containing the action buttons */\n" +
                            "#buttonbar { margin-top: 30px; width:600px; margin-bottom: 15px; text-align: center}\n" +
                            "#buttonbar .btn { font-family:Trebuchet MS, sans-serif; margin-left: 5px; margin-right: 5px; padding: 0 .25em; width: auto; overflow: visible}\n" +
                            "\n" +
                            "/* used for password complexity meter */\n" +
                            "div.progress-container { border: 1px solid #ccc; width: 90px; margin: 2px 5px 2px 0; padding: 1px; float: left; background: white; }\n" +
                            "div.progress-container > div { background-color: #ffffff; height: 10px; }\n" +
                            "\n" +
                            "/* header stuff */\n" +
                            "#header         { width:100%; height: 70px; margin: 0; background-image:url('header-gradient.gif') }\n" +
                            "#header-page    { width:600px; padding-top:9px; margin-left: auto; margin-right: auto; font-family:Trebuchet MS, sans-serif; font-size:22px; color:#FFFFFF; }\n" +
                            "#header-title   { width:600px; margin: auto; font-family:Trebuchet MS, sans-serif; font-size:14px; color:#FFFFFF; }\n" +
                            "#header-warning { width:100%; background-color:#FFDC8B; text-align:center; padding-top:4px; padding-bottom:4px }\n" +
                            "\n" +
                            ".clear { clear:both; }\n" +
                            "\n" +
                            ".msg-info    { display:block; padding:6px; background-color:#DDDDDD; width: 560px; border-radius:3px; -moz-border-radius:3px}\n" +
                            ".msg-error   { display:block; padding:6px; background-color:#FFCD59; width: 560px; border-radius:3px; -moz-border-radius:3px}\n" +
                            ".msg-success { display:block; padding:6px; background-color:#EFEFEF; width: 560px; border-radius:3px; -moz-border-radius:3px}\n" +
                            "\n" +
                            "#footer { position:relative; ;text-align: center; bottom:0; width:100%; color: #BBBBBB; font-size: 11px; height: 30px; margin: 0; margin-top: -30px}\n" +
                            "#footer .idle_status { color: #333333; }\n" +
                            "\n" +
                            "#capslockwarning { font-family: Trebuchet MS, sans-serif; color: #ffffff; font-weight:bold; font-variant:small-caps; margin-bottom: 5px; background-color:#d20734; border-radius:3px}\n" +
                            "");
            htmlBody.append("</style></head><body>");

            htmlBody.append("<h2>Daily Statistics</h2>");
            htmlBody.append("<p>InstanceID: ").append(pwmApplication.getInstanceID()).append("</p>");
            htmlBody.append("<br/>");

            textBody.append("--Daily Statistics--\n");
            textBody.append("instanceID: ").append(pwmApplication.getInstanceID()).append("\n");
            textBody.append("\n");

            {
                final Collection<HealthRecord> healthRecords = pwmApplication.getHealthMonitor().getHealthRecords();
                final java.util.Date lastHeathCheckDate = pwmApplication.getHealthMonitor().getLastHealthCheckDate();

                textBody.append("-- Health Check Results --\n");
                htmlBody.append("<h2>Health Check Results</h2>");
                textBody.append("healthCheckTimestamp: ").append(lastHeathCheckDate != null ? lastHeathCheckDate.toString() : "never").append("\n");
                htmlBody.append("HealthCheck Timestamp: ").append(lastHeathCheckDate != null ? lastHeathCheckDate.toString() : "never").append("<br/>");

                htmlBody.append("<table border='1'>");
                for (final HealthRecord record : healthRecords) {
                    textBody.append("topic='").append(record.getTopic(PwmConstants.DEFAULT_LOCALE,pwmApplication.getConfig())).append("'");
                    htmlBody.append("<tr><td class='key'>").append(record.getTopic(PwmConstants.DEFAULT_LOCALE,pwmApplication.getConfig())).append("</td>");

                    textBody.append(", status=").append(record.getStatus());
                    {
                        final String color;
                        switch (record.getStatus()) {
                            case GOOD:
                                color = "#8ced3f";
                                break;
                            case CAUTION:
                                color = "#FFCD59";
                                break;
                            case WARN:
                                color = "#d20734";
                                break;
                            default:
                                color = "white";
                        }
                        htmlBody.append("<td bgcolor='").append(color).append("'>").append(record.getStatus()).append("</td>");
                    }

                    textBody.append(", detail='").append(record.getDetail(PwmConstants.DEFAULT_LOCALE,pwmApplication.getConfig())).append("'").append("\n");
                    htmlBody.append("<td>").append(record.getDetail(PwmConstants.DEFAULT_LOCALE,pwmApplication.getConfig())).append("</td></tr>");
                }
                htmlBody.append("</table>");
            }

            textBody.append("\n\n\n");
            htmlBody.append("<br/><br/>");

            { // statistics
                final Map<String, String> sortedStats = new TreeMap<String, String>();
                sortedStats.putAll(valueMap);

                htmlBody.append("<table border='1'>");
                for (final String key : sortedStats.keySet()) {
                    final String value = valueMap.get(key);
                    textBody.append(key).append(": ").append(value).append("\n");
                    htmlBody.append("<tr><td class='key'>").append(key).append("</td><td>").append(value).append("</td></tr>");
                }
                htmlBody.append("</table>");
            }

            htmlBody.append("</body></html>");

            final EmailItemBean emailItem = new EmailItemBean(toAddress, fromAddress, subject, textBody.toString(), htmlBody.toString());
            pwmApplication.sendEmailUsingQueue(emailItem,null,null);
        }
    }

    private static boolean checkIfEnabled(final PwmApplication pwmApplication, final PwmSetting pwmSetting) {
        if (pwmApplication == null) {
            return false;
        }

        if (pwmApplication.getConfig() == null) {
            return false;
        }

        final List<String> toAddress = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EMAIL_ADMIN_ALERT_TO);
        final String fromAddress = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_ADMIN_ALERT_FROM);

        if (toAddress == null || toAddress.isEmpty() || toAddress.get(0) == null || toAddress.get(0).length() < 1) {
            return false;
        }

        if (fromAddress == null || fromAddress.length() < 1) {
            return false;
        }

        if (pwmSetting != null) {
            return pwmApplication.getConfig().readSettingAsBoolean(pwmSetting);
        }

        return true;
    }
}
