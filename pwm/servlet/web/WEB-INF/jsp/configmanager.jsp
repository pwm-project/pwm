<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.util.LocaleHelper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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
<%
    final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext);
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configmanagerStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%>"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="fragment/configmanager-nav.jsp" %>
        <style nonce="<pwm:value name="cspNonce"/>">
        </style>
        <table style="width:550px">
            <col class="key" style="width:150px">
            <col class="key" style="width:400px">
            <tr>
                <td>
                    Configuration Status
                </td>
                <td>
                    <pwm:if test="configurationOpen">Open</pwm:if>
                    <pwm:if test="configurationOpen" negate="true">Restricted</pwm:if>
                </td>
            </tr>
            <tr>
                <td>
                    Last Modified
                </td>
                <td>
                    <% String lastModified = (String)JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.ConfigLastModified); %>
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
                    <%=JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.ConfigHasPassword)%>
                </td>
            </tr>
            <tr>
                <td>
                    Application Data Path
                </td>
                <td>
                    <div style="max-width:398px; overflow-x: auto; white-space: nowrap">
                        <%=StringUtil.escapeHtml((String) JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.ApplicationPath))%>
                    </div>
                </td>
            </tr>
            <tr>
                <td>
                    Configuration File
                </td>
                <td>
                    <div style="max-width:398px; overflow-x: auto; white-space: nowrap">
                        <%=StringUtil.escapeHtml((String) JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.ConfigFilename))%>
                    </div>
                </td>
            </tr>
        </table>
        <br/>
        <div id="healthBody" style="margin-top:5px; margin-left: 20px; margin-right: 20px; padding:0; max-height: 300px; overflow-y: auto">
            <div class="WaitDialogBlank"></div>
        </div>
        <br/>
        <table class="noborder">
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_DownloadBundle" title="<pwm:display key="MenuDisplay_DownloadBundle" bundle="Config"/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-suitcase"></span></pwm:if>
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
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_DownloadConfig" title="<pwm:display key="MenuDisplay_DownloadConfig" bundle="Config"/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-download"></span></pwm:if>
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
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_ConfigurationSummary" href="#">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-files-o"></span></pwm:if>
                        Configuration Summary
                    </a>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_MAIN.addEventHandler('MenuItem_ConfigurationSummary','click',function(){
                                    window.open('ConfigManager?processAction=summary','_blank', 'width=650,toolbar=0,location=0,menubar=0');
                                });
                            });
                        </script>
                    </pwm:script>

                </td>
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_UploadConfig"  title="<pwm:display key="MenuDisplay_UploadConfig" bundle="Config"/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-upload"></span></pwm:if>
                        <pwm:display key="MenuItem_UploadConfig" bundle="Config"/>
                    </a>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_MAIN.addEventHandler('MenuItem_UploadConfig',"click",function(){
                                    <pwm:if test="configurationOpen">
                                    PWM_MAIN.showConfirmDialog({text:PWM_CONFIG.showString('MenuDisplay_UploadConfig'),okAction:function(){PWM_CONFIG.uploadConfigDialog()}})
                                    </pwm:if>
                                    <pwm:if test="configurationOpen" negate="true">
                                    PWM_CONFIG.configClosedWarning();
                                    </pwm:if>
                                });
                            });
                        </script>
                    </pwm:script>
                </td>
            </tr>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <pwm:if test="configurationOpen">
                        <a class="menubutton" id="MenuItem_LockConfig" title="<pwm:display key="MenuDisplay_LockConfig" bundle="Config"/>">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-lock"></span></pwm:if>
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
                    <pwm:if test="configurationOpen" negate="true">
                        <a class="menubutton" id="MenuItem_UnlockConfig">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-unlock"></span></pwm:if>
                            <pwm:display key="MenuItem_UnlockConfig" bundle="Config"/>
                        </a>
                        <pwm:script>
                            <script type="application/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    PWM_MAIN.addEventHandler('MenuItem_UnlockConfig','click',function(){
                                        PWM_MAIN.showDialog({
                                            title:'Alert',
                                            width:500,
                                            text:PWM_CONFIG.showString('MenuDisplay_UnlockConfig',{
                                                value1:'<%=StringUtil.escapeJS((String)JspUtility.getAttribute(pageContext,PwmConstants.REQUEST_ATTR.ConfigFilename))%>'
                                            })
                                        });
                                    });
                                });
                            </script>
                        </pwm:script>
                    </pwm:if>
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
<pwm:script-ref url="/public/resources/js/admin.js"/>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
