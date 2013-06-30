<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
  ~
  ~ This program is free software; you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation; either version 2 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
  --%>

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(session); %>
<% final password.pwm.config.Configuration pwmConfig = ContextManager.getPwmApplication(session).getConfig(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="PWM Configuration Settings"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <p>
            This screen shows the current running configuration. The configuration was loaded at
            <%=ContextManager.getContextManager(session).getConfigReader().getConfigurationReadTime()%>. You
            can use the <a href="<%=request.getContextPath()%><pwm:url url="/private/admin/ConfigManager"/>">ConfigManager</a>
            to modify the configuration.  Values in <span style="color:blue;">blue</span> are modified from the default values.
        </p>
        <% if (pwmConfig.getNotes() != null && (pwmConfig.getNotes().length() > 0)) { %>
        <div data-dojo-type="dijit/TitlePane" data-dojo-props="title: 'Configuration Notes', open:false" style="width: 100%">
            <textarea readonly="readonly" style="max-height: 200px;border:0" rows="1" data-dojo-type="dijit.form.Textarea"><%=StringEscapeUtils.escapeHtml(pwmConfig.getNotes())%></textarea>
        </div>
        <br/>
        <% } %>
        <div data-dojo-type="dijit/layout/TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false">
            <%
                final Map<PwmSetting.Category,List<PwmSetting>> categorySettingMap = PwmSetting.valuesByFilter(pwmConfig.getTemplate(),null,2);
                for (final PwmSetting.Category loopCategory : categorySettingMap.keySet()) {
                    final List<PwmSetting> loopSettings = categorySettingMap.get(loopCategory);
            %>
            <div data-dojo-type="dijit/layout/ContentPane" title="<%= loopCategory.getLabel(pwmSession.getSessionStateBean().getLocale())%>"
                 style="max-height: 500px; overflow: auto">
                <table>
                    <% for (final PwmSetting loopSetting : loopSettings) { %>
                    <% final boolean defaultValue = pwmConfig.isDefaultValue(loopSetting); %>
                    <tr>
                        <td class="key" style="width:100px; text-align:center; <%=defaultValue?"":"color:blue;"%>" id="<%=loopSetting.getKey()%>">
                            <%= loopSetting.getLabel(pwmSession.getSessionStateBean().getLocale()) %>
                        </td>
                        <td <%= !defaultValue ? "style=\"color:blue;\"" : ""%>>
                            <%
                                if (loopSetting.isConfidential()) {
                                    out.write("<span style=\"color:gray;\">" + PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT + "</span>");
                                } else {
                                    switch (loopSetting.getSyntax()) {
                                        case STRING: {
                                            final String value = pwmConfig.readSettingAsString(loopSetting);
                                            out.write(StringEscapeUtils.escapeHtml(value) + "<br/>");
                                        }
                                        break;

                                        case STRING_ARRAY: {
                                            final List<String> values = pwmConfig.readSettingAsStringArray(loopSetting);
                                            for (final String value : values) {
                                                out.write(StringEscapeUtils.escapeHtml(value) + "<br/>");
                                            }
                                        }
                                        break;

                                        case LOCALIZED_STRING:
                                        case LOCALIZED_TEXT_AREA: {
                                            for (final Locale locale : pwmConfig.localesForSetting(loopSetting)) {
                                                final String value = StringEscapeUtils.escapeHtml(pwmConfig.readSettingAsLocalizedString(loopSetting, locale));
                                                out.write("<b>" + locale + "</b>" + value + "<br/>");
                                            }

                                        }
                                        break;

                                        case LOCALIZED_STRING_ARRAY: {
                                            for (final Locale locale : pwmConfig.localesForSetting(loopSetting)) {
                                                out.write("<table><tr><td>");
                                                out.write((locale == null || locale.toString().length() < 1) ? "Default" : locale.toString());
                                                out.write("</td><td>");
                                                for (String value : pwmConfig.readSettingAsLocalizedStringArray(loopSetting, locale)) {
                                                    value = StringEscapeUtils.escapeHtml(value);
                                                    out.write(value + "<br/>");
                                                }
                                                out.write("</td></tr></table>");
                                            }
                                        }
                                        break;

                                        default:
                                            out.write(StringEscapeUtils.escapeHtml(pwmConfig.toString(loopSetting)));
                                    }
                                }
                            %>
                        </td>
                    </tr>
                    <script type="text/javascript">
                        require(["dojo/domReady!"],function(){setTimeout(function(){require(["dijit/Tooltip"],function(){
                            var strengthTooltip = new dijit.Tooltip({
                                connectId: ["<%=loopSetting.getKey()%>"],
                                label: '<div style="max-width: 350px">' + '<%=StringEscapeUtils.escapeJavaScript(loopSetting.getDescription(pwmSession.getSessionStateBean().getLocale()))%>' + '</div>',
                                position: ['before']
                            });
                        });},1000)});
                    </script>
                    <% } %>
                </table>
            </div>
            <% } %>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/form/Textarea","dijit/Tooltip","dijit/TitlePane","dojo/domReady!"],function(dojoParser){
        dojoParser.parse();
    });
    });
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
