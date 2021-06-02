<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.PwmEnvironment" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<% final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<link href="<pwm:context/><pwm:url url='/public/resources/configmanagerStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%>"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></h1>
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
                    <% final String lastModified = (String)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ConfigLastModified); %>
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
                    <%=JspUtility.getAttribute(pageContext, PwmRequestAttribute.ConfigHasPassword)%>
                </td>
            </tr>
            <pwm:if test="<%=PwmIfTest.appliance%>" negate="true">
                <tr>
                    <td>
                        Application Data Path
                    </td>
                    <td>
                        <div style="max-width:398px; overflow-x: auto; white-space: nowrap">
                            <%=StringUtil.escapeHtml((String) JspUtility.getAttribute(pageContext, PwmRequestAttribute.ApplicationPath))%>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        Configuration File
                    </td>
                    <td>
                        <div style="max-width:398px; overflow-x: auto; white-space: nowrap">
                            <%=StringUtil.escapeHtml((String) JspUtility.getAttribute(pageContext, PwmRequestAttribute.ConfigFilename))%>
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
                <div id="healthBody" class="health-body noborder nopadding">
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
                    <% final String configFileName = (String)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ConfigFilename); %>
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
