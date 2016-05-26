<%@ page import="password.pwm.PwmEnvironment" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.util.LocaleHelper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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

<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<% final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configmanagerStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%>"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></div>
        <%@ include file="fragment/configmanager-nav.jsp" %>
        <table style="width:550px">
            <tr>
                <td colspan="2" class="title">Configuration Status</td>
            </tr>
            <tr>
                <td>
                    Application Mode
                </td>
                <td>
                    <pwm:if test="<%=PwmIfTest.configurationOpen%>">Configuration (LDAP directory authentication not required)</pwm:if>
                    <pwm:if test="<%=PwmIfTest.configurationOpen%>" negate="true">Restricted</pwm:if>
                </td>
            </tr>
            <tr>
                <td>
                    Last Modified
                </td>
                <td>
                    <% String lastModified = (String)JspUtility.getAttribute(pageContext, PwmRequest.Attribute.ConfigLastModified); %>
                    <% if (lastModified == null) { %>
                    <pwm:display key="Value_NotApplicable"/>
                    <% } else { %>
                    <span class="timestamp"><%=lastModified%></span>
                    <% } %>
                </td>
            </tr>
            <tr>
                <td>
                    Password Protected
                </td>
                <td>
                    <%=JspUtility.getAttribute(pageContext, PwmRequest.Attribute.ConfigHasPassword)%>
                </td>
            </tr>
            <pwm:if test="<%=PwmIfTest.appliance%>" negate="true">
                <tr>
                    <td>
                        Application Data Path
                    </td>
                    <td>
                        <div style="max-width:398px; overflow-x: auto; white-space: nowrap">
                            <%=StringUtil.escapeHtml((String) JspUtility.getAttribute(pageContext, PwmRequest.Attribute.ApplicationPath))%>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        Configuration File
                    </td>
                    <td>
                        <div style="max-width:398px; overflow-x: auto; white-space: nowrap">
                            <%=StringUtil.escapeHtml((String) JspUtility.getAttribute(pageContext, PwmRequest.Attribute.ConfigFilename))%>
                        </div>
                    </td>
                </tr>
            </pwm:if>
        </table>
        <br/>
        <table style="width:550px">
            <tr>
                <td class="title">Health</td>
            </tr>
            <tr><td>
                <div id="healthBody" class="health-body" style="border: 0; padding: 0">
                    <div class="WaitDialogBlank"></div>
                </div>
            </td></tr>
        </table>

        <br/>

        <table style="width: 550px">
            <tr><td colspan="2" class="title">Configuration Activities</td></tr>
            <pwm:if test="<%=PwmIfTest.configurationOpen%>">
            <tr class="buttonrow">
                <td class="buttoncell" colspan="2">
                    <% String configFileName = (String)JspUtility.getAttribute(pageContext, PwmRequest.Attribute.ConfigFilename); %>
                    <pwm:if test="<%=PwmIfTest.trialMode%>">
                        <div  style="text-align: center" class="center">
                        <span><pwm:display key="Notice_TrialRestrictConfig" bundle="Admin"/></span>
                        </div>
                    </pwm:if>
                    <pwm:if test="<%=PwmIfTest.trialMode%>" negate="true">
                        <a class="menubutton important" id="MenuItem_LockConfig" title="<pwm:display key="MenuDisplay_LockConfig" value1="<%=configFileName%>" bundle="Config"/>">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-lock"></span></pwm:if>
                            <pwm:display key="MenuItem_LockConfig" bundle="Config"/>
                        </a>
                        <pwm:script>
                            <script type="application/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    PWM_MAIN.addEventHandler('MenuItem_LockConfig','click',function(){
                                        PWM_CONFIG.lockConfiguration();
                                    });
                                });
                            </script>
                        </pwm:script>
                    </pwm:if>
                    </pwm:if>
                </td>
            </tr>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_UploadConfig"  title="<pwm:display key="MenuDisplay_UploadConfig" bundle="Config"/>">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-upload"></span></pwm:if>
                        <pwm:display key="MenuItem_UploadConfig" bundle="Config"/>
                    </a>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_MAIN.addEventHandler('MenuItem_UploadConfig',"click",function(){
                                    <pwm:if test="<%=PwmIfTest.configurationOpen%>">
                                    PWM_MAIN.showConfirmDialog({text:PWM_CONFIG.showString('MenuDisplay_UploadConfig'),okAction:function(){PWM_CONFIG.uploadConfigDialog()}})
                                    </pwm:if>
                                    <pwm:if test="<%=PwmIfTest.configurationOpen%>" negate="true">
                                    PWM_CONFIG.configClosedWarning();
                                    </pwm:if>
                                });
                            });
                        </script>
                    </pwm:script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_DownloadConfig" title="<pwm:display key="MenuDisplay_DownloadConfig" bundle="Config"/>">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download"></span></pwm:if>
                        <pwm:display key="MenuItem_DownloadConfig" bundle="Config"/>
                    </a>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_MAIN.addEventHandler('MenuItem_DownloadConfig','click',function(){PWM_CONFIG.downloadConfig()});
                            });
                        </script>
                    </pwm:script>
                </td>
            </tr>
        </table>
        <br/>
        <table style="width: 550px">
            <tr><td colspan="2" class="title">Reports</td></tr>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_ConfigurationSummary">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-files-o"></span></pwm:if>
                        Configuration Summary
                    </a>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_MAIN.addEventHandler('MenuItem_ConfigurationSummary','click',function(){
                                    window.open('ConfigManager?processAction=summary','_blank', 'width=650,toolbar=0,location=0,menubar=0,scrollbars=1');
                                });
                            });
                        </script>
                    </pwm:script>

                </td>
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_DownloadBundle" title="<pwm:display key="MenuDisplay_DownloadBundle" bundle="Config"/>">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-suitcase"></span></pwm:if>
                        <pwm:display key="MenuItem_DownloadBundle" bundle="Config"/>
                    </a>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_MAIN.addEventHandler('MenuItem_DownloadBundle','click',function(){PWM_CONFIG.downloadSupportBundle()});
                            });
                        </script>
                    </pwm:script>
                </td>
            </tr>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_LdapPermissions">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-key"></span></pwm:if>
                        LDAP Permissions
                    </a>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_MAIN.addEventHandler('MenuItem_LdapPermissions','click',function(){
                                    window.open('ConfigManager?processAction=permissions','_blank', 'width=650,toolbar=0,location=0,menubar=0,scrollbars=1');
                                });
                            });
                        </script>
                    </pwm:script>

                </td>
            </tr>
        </table>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dijit/TitlePane","dojo/domReady!","dojox/form/Uploader"],function(dojoParser){
                dojoParser.parse();
            });
            PWM_VAR['config_localDBLogLevel'] = '<%=pwmRequest.getConfig().getEventLogLocalDBLevel()%>'

            require(["dojo/domReady!"],function(){
                PWM_ADMIN.showAppHealth('healthBody', {showRefresh: true, showTimestamp: true});
            });
        });

    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
