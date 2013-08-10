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
<%@ page import="password.pwm.bean.servlet.ConfigManagerBean" %>
<%@ page import="password.pwm.config.StoredConfiguration" %>
<%@ page import="password.pwm.config.value.X509CertificateValue" %>
<%@ page import="password.pwm.servlet.ConfigManagerServlet" %>
<%@ page import="password.pwm.util.ServletHelper" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<% request.setAttribute("noTheme","true"); %>
<%@ include file="fragment/header.jsp" %>
<% final Collection<Locale> localeList = new ArrayList<Locale>(ContextManager.getPwmApplication(session).getConfig().getKnownLocales()); %>
<% localeList.remove(Helper.localeResolver(PwmConstants.DEFAULT_LOCALE, localeList)); %>
<% final Locale locale = password.pwm.PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final int level = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().getLevel(); %>
<% final boolean showDesc = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().isShowDescr(); %>
<% final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean(); %>
<% final password.pwm.config.PwmSetting.Category category = configManagerBean.getCategory(); %>
<%
    String configFilePath = PwmConstants.CONFIG_FILE_FILENAME;
    try { configFilePath = ContextManager.getContextManager(session).getConfigReader().getConfigFile().toString(); } catch (Exception e) { /* */ }
%>

<body class="nihilo" onload="initConfigPage();pwmPageLoadHandler()">
<script type="text/javascript" defer="defer" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script type="text/javascript" defer="defer" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<script type="text/javascript">
    PWM_GLOBAL['configurationNotes'] = '<%=StringEscapeUtils.escapeJavaScript(configManagerBean.getConfiguration().readProperty(StoredConfiguration.PROPERTY_KEY_NOTES))%>';
</script>
<style type="text/css">
    #centerbody-config {
        width: 600px;
        min-width: 600px;
        padding: 10px;
        position: relative;
        margin-left: auto;
        margin-right: auto;
        margin-top: 0;
        clear: both;
        border-radius: 0 0 5px 5px;
        box-shadow: 0;
        background-color: white;
    }
</style>
<div id="wrapper" style="border:1px; background-color: black">
<div id="header">
    <div id="header-company-logo"></div>
    <div id="header-page">
        <% if (configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.SETTINGS) { %>
        <%=category.getType() == PwmSetting.Category.Type.SETTING ? "Settings - " : "Modules - "%>
        <%=category.getLabel(locale)%>
        <% } else if (configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.LOCALEBUNDLE) { %>
        <% final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().getLocaleBundle(); %>
        Custom Text - <%=bundleName.getTheClass().getSimpleName()%>
        <% } %>
    </div>
    <div id="header-title">
        Configuration Editor
    </div>
</div>
<div id="TopMenu">
</div>
<div id="centerbody-config">
<%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
<script type="text/javascript">
function buildMenuBar() {
    clearDijitWidget('topMenuBar');
    require(["dojo","dijit","dijit/Menu","dijit/Dialog","dijit/MenuBar","dijit/MenuItem","dijit/MenuBarItem","dijit/PopupMenuBarItem","dijit/CheckedMenuItem","dijit/MenuSeparator"],
            function(dojo,dijit,Menu,Dialog,MenuBar,MenuItem,MenuBarItem,PopupMenuBarItem,CheckedMenuItem,MenuSeparator){
                var topMenuBar = new MenuBar({id:"topMenuBar"});
                { // Settings Menu
                    var settingsMenu = new Menu({});
                    <% final Map<PwmSetting.Category,List<PwmSetting>> settingMap = PwmSetting.valuesByFilter(configManagerBean.getConfiguration().getTemplate(),PwmSetting.Category.Type.SETTING,level); %>
                    <% for (final PwmSetting.Category loopCategory : settingMap.keySet()) { %>
                    <% if (loopCategory == category && configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.SETTINGS) { %>
                    settingsMenu.addChild(new MenuItem({
                        label: '<%=loopCategory.getLabel(locale)%>',
                        disabled: true
                    }));
                    <% } else { %>
                    settingsMenu.addChild(new MenuItem({
                        label: '<%=loopCategory.getLabel(locale)%>',
                        onClick: function() {
                            showWaitDialog(null,null,function(){
                                dojo.xhrGet({
                                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&category=<%=loopCategory.toString()%>",
                                    preventCache: true,
                                    error: function(errorObj) {
                                        showError("error reason: " + errorObj)
                                    },
                                    load: function(data) {
                                        loadMainPageBody();
                                    }
                                });
                            });
                        }
                    }));
                    <% } %>
                    <% } %>
                    topMenuBar.addChild(new PopupMenuBarItem({
                        label: "Settings",
                        popup: settingsMenu
                    }));
                }
                { // Modules Menu
                    var modulesMenu = new Menu({});
                    <% final Map<PwmSetting.Category,List<PwmSetting>> moduleMap = PwmSetting.valuesByFilter(configManagerBean.getConfiguration().getTemplate(),PwmSetting.Category.Type.MODULE,level); %>
                    <% for (final PwmSetting.Category loopCategory : moduleMap.keySet()) { %>
                    <% if (loopCategory == category && configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.SETTINGS) { %>
                    modulesMenu.addChild(new MenuItem({
                        label: '<%=loopCategory.getLabel(locale)%>',
                        disabled: true
                    }));
                    <% } else { %>
                    modulesMenu.addChild(new MenuItem({
                        label: '<%=loopCategory.getLabel(locale)%>',
                        onClick: function() {
                            showWaitDialog(null,null,function(){
                                dojo.xhrGet({
                                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&category=<%=loopCategory.toString()%>",
                                    preventCache: true,
                                    error: function(errorObj) {
                                        showError("error reason: " + errorObj)
                                    },
                                    load: function(data) {
                                        loadMainPageBody();
                                    }
                                });
                            });
                        }
                    }));
                    <% } %>
                    <% } %>
                    topMenuBar.addChild(new PopupMenuBarItem({
                        label: "Modules",
                        popup: modulesMenu
                    }));
                }
                { // Display menu
                    var displayMenu = new Menu({});

                    <% for (final PwmConstants.EDITABLE_LOCALE_BUNDLES localeBundle : PwmConstants.EDITABLE_LOCALE_BUNDLES.values()) { %>
                    <% if (localeBundle == configManagerBean.getLocaleBundle() && configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.LOCALEBUNDLE) { %>
                    displayMenu.addChild(new MenuItem({
                        label: '<%=localeBundle.getTheClass().getSimpleName()%>',
                        disabled: true
                    }));
                    <% } else { %>
                    displayMenu.addChild(new MenuItem({
                        label: '<%=localeBundle.getTheClass().getSimpleName()%>',
                        onClick: function() {
                            showWaitDialog(null,null,function(){
                                dojo.xhrGet({
                                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&localeBundle=<%=localeBundle.toString()%>",
                                    preventCache: true,
                                    error: function(errorObj) {
                                        showError("error loading " + keyName + ", reason: " + errorObj)
                                    },
                                    load: function(data) {
                                        loadMainPageBody();
                                    }
                                });
                            });
                        }
                    }));
                    <% } %>
                    <% } %>
                    topMenuBar.addChild(new PopupMenuBarItem({
                        label: "Custom Text",
                        popup: displayMenu
                    }));
                }
                {
                    topMenuBar.addChild(
                            new MenuBarItem({
                                label: " | ",
                                disabled: true
                            }));
                }
                { // view
                    var viewMenu = new Menu({});
                    viewMenu.addChild(new CheckedMenuItem({
                        label: "Advanced Settings",
                        checked: <%=level == 1 ? "true" : "false"%>,
                        onClick: function() {
                            showWaitDialog(null,null,function(){
                                dojo.xhrGet({
                                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&level=<%=level == 1 ? "0" : "1"%>",
                                    preventCache: true,
                                    load: function(data) {
                                        loadMainPageBody();
                                    }
                                });
                            });
                        }
                    }));
                    viewMenu.addChild(new CheckedMenuItem({
                        label: "Display Help Text",
                        checked: <%=showDesc ? "true" : "false"%>,
                        onClick: function() {
                            showWaitDialog(null,null,function(){
                                dojo.xhrGet({
                                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&showDesc=<%=showDesc ? "false" : "true"%>",
                                    preventCache: true,
                                    load: function(data) {
                                        loadMainPageBody();
                                    }
                                });
                            });
                        }
                    }));
                    <% if (
                    ServletHelper.cookieEquals(request, "hide-warn-advanced", "true") ||
                    ServletHelper.cookieEquals(request, "hide-warn-shownotes", "true") ||
                    ServletHelper.cookieEquals(request, "hide-warn-showdesc", "true")
                    ){ %>
                    viewMenu.addChild(new MenuSeparator());
                    viewMenu.addChild(new MenuItem({
                        label: "Show Hidden Warnings",
                        checked: <%=showDesc ? "true" : "false"%>,
                        onClick: function() {
                            setCookie('hide-warn-advanced');
                            setCookie('hide-warn-shownotes');
                            setCookie('hide-warn-showdesc');
                            showWaitDialog();
                            window.location="ConfigManager";
                        }
                    }));
                    <% } %>
                    viewMenu.addChild(new MenuSeparator());
                    viewMenu.addChild(new MenuItem({
                        label: "Configuration Notes",
                        onClick: function() {
                            showConfigurationNotes();
                        }
                    }));
                    viewMenu.addChild(new MenuItem({
                        label: "Macro Help",
                        onClick: function() {
                            var idName = 'dialogPopup';
                            clearDijitWidget(idName);
                            var theDialog = new Dialog({
                                id: idName,
                                title: 'Macro Help',
                                style: "width: 550px",
                                href: PWM_GLOBAL['url-resources'] + "/text/macroHelp.html"
                            });
                            theDialog.show();
                        }
                    }));
                    topMenuBar.addChild(new PopupMenuBarItem({
                        label: "View",
                        popup: viewMenu
                    }));
                }

                { // Templates
                    var templateMenu = new Menu({});
                    <% for (final PwmSetting.Template template : PwmSetting.Template.values()) { %>
                    <% final boolean isCurrentTemplate = configManagerBean.getConfiguration().getTemplate() == template; %>
                    var confirmText = 'Are you sure you want to change the default settings template?  \n\nIf you proceed, be sure to closely review the resulting configuration as any settings using default values may change.';
                    templateMenu.addChild(new CheckedMenuItem({
                        label: "<%=template.getLabel(locale)%>",
                        checked: <%=isCurrentTemplate ? "true" : "false"%>,
                        onClick: function() {
                            showConfirmDialog(null,confirmText,function(){
                                showWaitDialog(null,null,function(){
                                    dojo.xhrGet({
                                        url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&template=<%=template.toString()%>",
                                        preventCache: true,
                                        error: function(errorObj) {
                                            showError("error loading " + keyName + ", reason: " + errorObj)
                                        },
                                        load: function(data) {
                                            window.location = "ConfigManager";
                                        }
                                    });
                                });
                            });
                        }
                    }));
                    <% } %>
                    templateMenu.addChild(new MenuSeparator());
                    templateMenu.addChild(new MenuItem({
                        label: "About Templates",
                        onClick: function() {
                            var idName = 'dialogPopup';
                            clearDijitWidget(idName);
                            var theDialog = new Dialog({
                                id: idName,
                                title: 'About Templates',
                                style: "width: 550px",
                                href: PWM_GLOBAL['url-resources'] + "/text/aboutTemplates.html"
                            });
                            theDialog.show();
                        }
                    }));

                    topMenuBar.addChild(new PopupMenuBarItem({
                        label: "Template",
                        popup: templateMenu
                    }));
                }
                {
                    topMenuBar.addChild(
                            new MenuBarItem({
                                label: " | ",
                                disabled: true
                            }));
                }
                { // Actions
                    var actionsMenu = new Menu({});
                    actionsMenu.addChild(new MenuItem({
                        label: "<pwm:Display key="MenuItem_DownloadConfig" bundle="Config"/>",
                        onClick: function() {
                            window.location='ConfigManager?processAction=generateXml&pwmFormID=' + PWM_GLOBAL['pwmFormID'];
                        }
                    }));
                    actionsMenu.addChild(new MenuItem({
                        label: "<pwm:Display key="MenuItem_UploadConfig" bundle="Config"/>",
                        onClick: function() {
                            showConfirmDialog(null,'<pwm:Display key="MenuDisplay_UploadConfig" bundle="Config" value1="<%=configFilePath%>"/>',function(){uploadConfigDialog()},null);
                        }
                    }));
                    actionsMenu.addChild(new MenuSeparator());
                    actionsMenu.addChild(new MenuItem({
                        label: "Set Configuration Password",
                        onClick: function() {
                            setConfigurationPassword();
                        }
                    }));
                    actionsMenu.addChild(new MenuSeparator());
                    actionsMenu.addChild(new MenuItem({
                        label: "Import LDAP Server Certificates",
                        onClick: function() {
                            importLdapCertificates();
                        }
                    }));
                    <% final X509CertificateValue ldapCerts = (X509CertificateValue)configManagerBean.getConfiguration().readSetting(PwmSetting.LDAP_SERVER_CERTS); %>
                    <% if (ldapCerts != null && ldapCerts.hasCertificates()) { %>
                    actionsMenu.addChild(new MenuItem({
                        label: "Clear Imported LDAP Server Certificates",
                        onClick: function() {
                            clearLdapCertificates();
                        }
                    }));
                    <% } %>
                    actionsMenu.addChild(new MenuSeparator());
                    actionsMenu.addChild(new MenuItem({
                        label: "Save",
                        iconClass: "dijitEditorIcon dijitEditorIconSave",
                        onClick: function() {
                            showConfirmDialog(null,'<pwm:Display key="MenuDisplay_SaveConfig" bundle="Config"/>',function(){saveConfiguration(true)});
                            buildMenuBar();
                        }
                    }));
                    actionsMenu.addChild(new MenuItem({
                        label: "Cancel",
                        iconClass: "dijitEditorIcon dijitEditorIconCancel",
                        onClick: function() {
                            document.forms['cancelEditing'].submit();
                        }
                    }));

                    topMenuBar.addChild(new PopupMenuBarItem({
                        label: "Actions",
                        popup: actionsMenu
                    }));
                }
                topMenuBar.placeAt("TopMenu");
                topMenuBar.startup();
            });
}

function loadMainPageBody() {
    window.location = '<%=request.getContextPath()%><pwm:url url="/config/ConfigManager"/>';
}

PWM_GLOBAL['startupFunctions'].push(function(){
    buildMenuBar();
});
</script>
<form action="<pwm:url url='ConfigManager'/>" method="post" name="cancelEditing"
      enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="processAction" value="cancelEditing"/>
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<div id="mainContentPane" style="width: 600px">
    <% if (configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.SETTINGS) { %>
    <jsp:include page="configmanager-editor-settings.jsp"/>
    <% } else if (configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.LOCALEBUNDLE) { %>
    <jsp:include page="configmanager-editor-localeBundle.jsp"/>
    <% } %>
</div>
</div>
<div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('localeSelectionMenu').style.display = 'none';
    });
</script>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
