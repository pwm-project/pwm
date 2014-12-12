<%@ page import="password.pwm.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="java.io.File" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmRequest pwmRequest = PwmRequest.forRequest(request,response);
    String configFileName = PwmConstants.DEFAULT_CONFIG_FILE_FILENAME;
    String configFilePath = "<application path>/WEB-INF";
    String pageTitle = LocaleHelper.getLocalizedMessage("Title_ConfigManager",pwmRequest.getConfig(),password.pwm.i18n.Config.class);
    try {
        final File file = pwmRequest.getContextManager().getConfigReader().getConfigFile();
        configFileName = file.getAbsolutePath();
        configFilePath = file.getParentFile().getAbsolutePath();
    } catch (Exception e) { /* */ }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
<jsp:include page="fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="<%=pageTitle%>"/>
</jsp:include>
<div id="centerbody">
<%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
<div id="healthBody" style="margin-top:5px; margin-left: 20px; margin-right: 20px; padding:0; max-height: 300px; overflow-y: auto">
    <div class="WaitDialogBlank"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/domReady!"],function(){
                PWM_ADMIN.showAppHealth('healthBody', {showRefresh: true, showTimestamp: true});
            });
        });
    </script>
</pwm:script>
<style>
    .buttoncell {
        border: 0;
        width:50%;
    }
    .buttonrow {
        height: 45px;
        margin-top: 20px;
        margin-bottom: 20px;
    }
    .menubutton {
        cursor: pointer;
        display: block;
        margin-left: auto;
        margin-right: auto;

    }

</style>
<br/>
<br/>
<table class="noborder">
    <tr class="noborder">
        <td colspan="2" style="text-align: center">
            <%=PwmConstants.SERVLET_VERSION%>
        </td>
    </tr>
    <tr class="noborder">
        <td colspan="2" style="text-align: center">
            Configuration Status:
            <pwm:if test="configurationOpen">Open</pwm:if>
            <pwm:if test="configurationOpen" negate="true">Closed</pwm:if>
        </td>
    </tr>
    <tr class="noborder">
        <td colspan="2" style="text-align: center">
            Configuration File: <%=configFileName%>
        </td>
    </tr>
</table>
<br/>
<table class="noborder">
<tr class="buttonrow">
    <td class="buttoncell">
        <a class="menubutton" id="MenuItem_ConfigEditor">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-edit"></span></pwm:if>
            <pwm:display key="MenuItem_ConfigEditor" bundle="Admin"/>
        </a>
        <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_MAIN.addEventHandler('MenuItem_ConfigEditor','click',function(){PWM_CONFIG.startConfigurationEditor()});
                    makeTooltip('MenuItem_ConfigEditor',PWM_CONFIG.showString('MenuDisplay_ConfigEditor'));
                });
            </script>
        </pwm:script>
    </td>
    <td class="buttoncell">
        <a class="menubutton" id="MenuItem_ViewLog">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-list-alt"></span></pwm:if>
            <pwm:display key="MenuItem_ViewLog" bundle="Config"/>
        </a>
        <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_MAIN.addEventHandler('MenuItem_ViewLog','click',function(){PWM_CONFIG.openLogViewer(null)});
                    makeTooltip('MenuItem_ViewLog',PWM_CONFIG.showString('MenuDisplay_ViewLog'));
                });
            </script>
        </pwm:script>
    </td>
</tr>
<tr class="buttonrow">
    <td class="buttoncell">
        <a class="menubutton" id="MenuItem_DownloadBundle">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-suitcase"></span></pwm:if>
            <pwm:display key="MenuItem_DownloadBundle" bundle="Config"/>
        </a>
        <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_MAIN.addEventHandler('MenuItem_DownloadBundle','click',function(){PWM_CONFIG.downloadSupportBundle()});
                    makeTooltip('MenuItem_DownloadBundle',PWM_CONFIG.showString('MenuDisplay_DownloadBundle'));
                });
            </script>
        </pwm:script>
    </td>
    <td class="buttoncell">
        <a class="menubutton" id="MenuItem_DownloadConfig">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-download"></span></pwm:if>
            <pwm:display key="MenuItem_DownloadConfig" bundle="Config"/>
        </a>
        <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_MAIN.addEventHandler('MenuItem_DownloadConfig','click',function(){PWM_CONFIG.downloadConfig()});
                    makeTooltip('MenuItem_DownloadConfig',PWM_CONFIG.showString('MenuDisplay_DownloadConfig'));
                });
            </script>
        </pwm:script>
    </td>
</tr>
<tr class="buttonrow">
    <td class="buttoncell">
        <pwm:if test="configurationOpen">
            <a class="menubutton" id="MenuItem_LockConfig">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-lock"></span></pwm:if>
                <pwm:display key="MenuItem_LockConfig" bundle="Config"/>
            </a>
            <pwm:script>
                <script type="application/javascript">
                    PWM_GLOBAL['startupFunctions'].push(function(){
                        makeTooltip('MenuItem_LockConfig',PWM_CONFIG.showString('MenuDisplay_LockConfig',{value1:'<%=configFileName%>'}));
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
                                    value1:'<%=StringUtil.escapeJS(configFileName)%>',
                                    value2:'<%=StringUtil.escapeJS(configFilePath)%>'
                                })
                            });
                        });
                    });
                </script>
            </pwm:script>
        </pwm:if>
    </td>
    <td class="buttoncell">
        <a class="menubutton" id="MenuItem_UploadConfig">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-upload"></span></pwm:if>
            <pwm:display key="MenuItem_UploadConfig" bundle="Config"/>
        </a>
        <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    makeTooltip('MenuItem_UploadConfig',PWM_CONFIG.showString('MenuDisplay_UploadConfig'));
                    PWM_MAIN.addEventHandler('MenuItem_UploadConfig',"click",function(){
                        <pwm:if test="configurationOpen">
                        PWM_MAIN.showConfirmDialog({text:PWM_CONFIG.showString('MenuDisplay_UploadConfig'),okAction:function(){PWM_CONFIG.uploadConfigDialog()}})
                        </pwm:if>
                        <pwm:if test="configurationOpen" negate="true">
                        configClosedWarning();
                        </pwm:if>
                    });
                });
            </script>
        </pwm:script>
    </td>
</tr>
<tr class="buttonrow">
    <td class="buttoncell">
        <a class="menubutton"id="MenuItem_MainMenu">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-arrow-circle-left"></span></pwm:if>
            <pwm:display key="MenuItem_MainMenu" bundle="Config"/>
        </a>
        <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_MAIN.addEventHandler('MenuItem_MainMenu','click',function(){PWM_MAIN.goto('/')});
                    makeTooltip('MenuItem_MainMenu',PWM_CONFIG.showString('MenuDisplay_MainMenu'));
                });
            </script>
        </pwm:script>
    </td>
    <td class="buttoncell">
        <a class="menubutton" id="MenuItem_ExportLocalDB">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-download"></span></pwm:if>
            <pwm:display key="MenuItem_ExportLocalDB" bundle="Config"/>
        </a>
        <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_MAIN.addEventHandler('MenuItem_ExportLocalDB','click',function(){PWM_CONFIG.downloadLocalDB()});
                    makeTooltip('MenuItem_ExportLocalDB',PWM_CONFIG.showString('MenuDisplay_ExportLocalDB'));
                });
            </script>
        </pwm:script>
    </td>
</tr>
<tr class="buttonrow">
    <td class="buttoncell">
        <a class="menubutton" id="MenuItem_Administration">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-dashboard"></span></pwm:if>
            <pwm:display key="Title_Admin"/>
        </a>
        <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_MAIN.addEventHandler('MenuItem_Administration','click',function(){PWM_MAIN.goto('/private/admin/dashboard.jsp')});
                    makeTooltip('MenuItem_Administration',PWM_MAIN.showString('Long_Title_Admin'));
                });
            </script>
        </pwm:script>
    </td>
    <td class="buttoncell">
        <a class="menubutton" id="MenuItem_UploadLocalDB">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-upload"></span></pwm:if>
            Import (Upload) LocalDB Archive File
        </a>
        <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    makeTooltip('MenuItem_UploadConfig',PWM_CONFIG.showString('MenuDisplay_UploadConfig'));
                    PWM_MAIN.addEventHandler('MenuItem_UploadLocalDB',"click",function(){
                        <pwm:if test="configurationOpen">
                        PWM_CONFIG.uploadLocalDB();
                        </pwm:if>
                        <pwm:if test="configurationOpen" negate="true">
                        configClosedWarning();
                        </pwm:if>
                    });
                });
            </script>
        </pwm:script>
    </td>
</tr>
<pwm:if test="authenticated">
    <tr class="buttonrow">
        <td class="buttoncell" colspan="2">
            <a class="menubutton" id="MenuItem_Logout">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-sign-out"></span></pwm:if>
                <pwm:display key="Title_Logout"/>
            </a>
            <pwm:script>
                <script type="application/javascript">
                    PWM_GLOBAL['startupFunctions'].push(function(){
                        PWM_MAIN.addEventHandler('MenuItem_Logout','click',function(){PWM_MAIN.goto('/public/Logout')});
                        makeTooltip('MenuItem_Logout',PWM_MAIN.showString('Long_Title_Logout'));
                    });
                </script>
            </pwm:script>
        </td>
    </tr>
</pwm:if>
</table>
</div>
</div>
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
        });

        function makeTooltip(id,text) {
            PWM_MAIN.showTooltip({
                id: id,
                showDelay: 800,
                position: ['below','above'],
                text: text,
                width: 300
            });
        }

        function configClosedWarning() {
            PWM_MAIN.showDialog({
                title:PWM_MAIN.showString('Title_Error'),
                text:"This operation is not available when the configuration is closed."
            });
        }

    </script>
</pwm:script>
<script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/admin.js"/>"></script>
<% password.pwm.http.JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
