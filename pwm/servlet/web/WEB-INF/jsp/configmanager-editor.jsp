<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2011 The PWM Project
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

<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.bean.ConfigManagerBean" %>
<%@ page import="password.pwm.config.ConfigurationReader" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.servlet.ConfigManagerServlet" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Locale" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<% final Collection<Locale> localeList = ContextManager.getContextManager(session).getKnownLocales(); %>
<% localeList.remove(Helper.localeResolver(PwmConstants.DEFAULT_LOCALE, localeList)); %>
<% final Locale locale = password.pwm.PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final password.pwm.config.PwmSetting.Level level = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().getLevel(); %>
<% final boolean showDesc = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().isShowDescr(); %>
<% final boolean showNotes = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().isShowNotes(); %>
<% final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean(); %>
<% final password.pwm.config.PwmSetting.Category category = configManagerBean.getCategory(); %>
<% final ConfigurationReader.MODE configMode = password.pwm.ContextManager.getContextManager(session).getConfigReader().getConfigMode(); %>
<body class="tundra">
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/configmanager.js"></script>
<script type="text/javascript">
    <% { for (final Locale loopLocale : localeList) { %>availableLocales['<%=loopLocale%>'] = '<%=loopLocale.getDisplayName()%>'; <% }
} %></script>
<div id="wrapper" style="border:1px; background-color: black">
<div id="header">
    <div id="header-page">
        PWM Configuration Editor
    </div>
    <div id="header-title" style="text-align: right;">
        <% if (configMode == ConfigurationReader.MODE.CONFIGURING || configMode == ConfigurationReader.MODE.NEW) { %>
        Editing Live Configuration
        <% } else { %>
        Editing In Memory Configuration
        <% } %>
    </div>
</div>
<div id="TopMenu" style="width:620px; position: relative; margin-left: auto; margin-right: auto; margin-top: 0; clear: both;">
</div>
<div id="centerbody" style="width: 600px; align: center; background-color: white; padding: 10px; margin-top: 0" >
<% if (PwmSession.getPwmSession(session).getSessionStateBean().getSessionError() != null) { %>
<span id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
<% } else { %>
<span style="visibility:hidden;" id="error_msg" class="msg-success"> </span>
<% } %>
<script type="text/javascript">
function buildMenuBar() {
    dojo.require("dijit.MenuBar");
    dojo.require("dijit.MenuItem");
    dojo.require("dijit.MenuBarItem");
    dojo.require("dijit.PopupMenuBarItem");
    dojo.require("dijit.CheckedMenuItem");

    var topMenuBar = new dijit.MenuBar({id:"topMenuBar"});
    { // Settings Menu
        var settingsMenu = new dijit.Menu({});
    <% for (final PwmSetting.Category loopCategory : PwmSetting.Category.valuesByGroup(0)) { %>
    <% if (loopCategory == category && configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.SETTINGS) { %>
        settingsMenu.addChild(new dijit.CheckedMenuItem({
            label: '<%=loopCategory.getLabel(locale)%>',
            checked: true,
            onClick: function() {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&category=<%=loopCategory.toString()%>",
                    sync: true,
                    error: function(errorObj) {
                        showError("error reason: " + errorObj)
                    },
                    load: function(data) {
                        loadMainPageBody();
                    }
                });
            }
        }));
    <% } else { %>
        settingsMenu.addChild(new dijit.MenuItem({
            label: '<%=loopCategory.getLabel(locale)%>',
            onClick: function() {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&category=<%=loopCategory.toString()%>",
                    sync: true,
                    error: function(errorObj) {
                        showError("error reason: " + errorObj)
                    },
                    load: function(data) {
                        loadMainPageBody();
                    }
                });
            }
        }));
    <% } %>
    <% } %>
        topMenuBar.addChild(new dijit.PopupMenuBarItem({
            label: "Settings",
            popup: settingsMenu
        }));
    }
    { // Modules Menu
        var modulesMenu = new dijit.Menu({});
    <% for (final PwmSetting.Category loopCategory : PwmSetting.Category.valuesByGroup(1)) { %>
    <% if (loopCategory == category && configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.SETTINGS) { %>
        modulesMenu.addChild(new dijit.CheckedMenuItem({
            label: '<%=loopCategory.getLabel(locale)%>',
            checked: true,
            onClick: function() {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&category=<%=loopCategory.toString()%>",
                    sync: true,
                    error: function(errorObj) {
                        showError("error reason: " + errorObj)
                    },
                    load: function(data) {
                        loadMainPageBody();
                    }
                });
            }
        }));
    <% } else { %>
        modulesMenu.addChild(new dijit.MenuItem({
            label: '<%=loopCategory.getLabel(locale)%>',
            onClick: function() {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&category=<%=loopCategory.toString()%>",
                    sync: true,
                    error: function(errorObj) {
                        showError("error reason: " + errorObj)
                    },
                    load: function(data) {
                        loadMainPageBody();
                    }
                });
            }
        }));
    <% } %>
    <% } %>
        topMenuBar.addChild(new dijit.PopupMenuBarItem({
            label: "Modules",
            popup: modulesMenu
        }));
    }
    { // Display menu
        var displayMenu = new dijit.Menu({});

    <% for (final PwmConstants.EDITABLE_LOCALE_BUNDLES localeBundle : PwmConstants.EDITABLE_LOCALE_BUNDLES.values()) { %>
    <% if (localeBundle == configManagerBean.getLocaleBundle() && configManagerBean.getEditMode() == ConfigManagerServlet.EDIT_MODE.LOCALEBUNDLE) { %>
        displayMenu.addChild(new dijit.CheckedMenuItem({
            label: '<%=localeBundle.getTheClass().getSimpleName()%>',
            checked: true,
            onClick: function() {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&localeBundle=<%=localeBundle.toString()%>",
                    sync: true,
                    error: function(errorObj) {
                        showError("error loading " + keyName + ", reason: " + errorObj)
                    },
                    load: function(data) {
                        loadMainPageBody();
                    }
                });
            }
        }));
    <% } else { %>
        displayMenu.addChild(new dijit.MenuItem({
            label: '<%=localeBundle.getTheClass().getSimpleName()%>',
            onClick: function() {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&localeBundle=<%=localeBundle.toString()%>",
                    sync: true,
                    error: function(errorObj) {
                        showError("error loading " + keyName + ", reason: " + errorObj)
                    },
                    load: function(data) {
                        loadMainPageBody();
                    }
                });
            }
        }));
    <% } %>
    <% } %>
        topMenuBar.addChild(new dijit.PopupMenuBarItem({
            label: "Display",
            popup: displayMenu
        }));
    }
    { // view
        var viewMenu = new dijit.Menu({});
        viewMenu.addChild(new dijit.CheckedMenuItem({
            label: "Show Advanced Settings",
            checked: <%=level == PwmSetting.Level.ADVANCED ? "true" : "false"%>,
            onClick: function() {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&level=<%=level == PwmSetting.Level.ADVANCED ? "BASIC" : "ADVANCED"%>",
                    sync: true,
                    load: function(data) {
                        loadMainPageBody();
                    }
                });
            }
        }));
        viewMenu.addChild(new dijit.CheckedMenuItem({
            label: "Display Help Text",
            checked: <%=showDesc ? "true" : "false"%>,
            onClick: function() {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&showDesc=<%=showDesc ? "false" : "true"%>",
                    sync: true,
                    load: function(data) {
                        loadMainPageBody();
                    }
                });
            }
        }));
        viewMenu.addChild(new dijit.CheckedMenuItem({
            label: "Show Configuration Notes",
            checked: <%=showNotes ? "true" : "false"%>,
            onClick: function() {
                showWaitDialog('Loading...');
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&showNotes=<%=showNotes ? "false" : "true"%>",
                    sync: true,
                    load: function(data) {
                        window.location = "ConfigManager";
                    }
                });
            }
        }));
        topMenuBar.addChild(new dijit.PopupMenuBarItem({
            label: "View",
            popup: viewMenu
        }));
    }
    { // Templates
        var templateMenu = new dijit.Menu({});
    <% for (final PwmSetting.Template template : PwmSetting.Template.values()) { %>
    <% final boolean isCurrentTemplate = configManagerBean.getConfiguration().template() == template; %>
        templateMenu.addChild(new dijit.CheckedMenuItem({
            label: "<%=template.getDescription()%>",
            checked: <%=isCurrentTemplate ? "true" : "false"%>,
            onClick: function() {
                if (!confirm('Are you sure you want to change the default settings template?  \n\nIf you proceed, be sure to closely review the resulting configuration as any settings using default values may change.')) {
                    return;
                }
                showWaitDialog('Loading...');
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&template=<%=template.toString()%>",
                    sync: true,
                    error: function(errorObj) {
                        showError("error loading " + keyName + ", reason: " + errorObj)
                    },
                    load: function(data) {
                        window.location = "ConfigManager";
                    }
                });
            }
        }));
    <% } %>
        topMenuBar.addChild(new dijit.PopupMenuBarItem({
            label: "Template",
            popup: templateMenu
        }));
    }
    { // Actions
        var actionsMenu = new dijit.Menu({});

    <% if (ContextManager.getContextManager(session).getConfigReader().getConfigMode() == ConfigurationReader.MODE.RUNNING) { %>
        actionsMenu.addChild(new dijit.MenuItem({
            label: "Finish Editing",
            onClick: function() {
                showWaitDialog('Updating Configuration');
                setTimeout(function() {
                    document.forms['completeEditing'].submit();
                }, 1000)
            }
        }));
    <% } else { %>
        actionsMenu.addChild(new dijit.MenuItem({
            label: "Save",
            iconClass: "dijitEditorIcon dijitEditorIconSave",
            onClick: function() {
                if (confirm('Are you sure you want to save the changes to the current PWM configuration?')) {
                    saveConfiguration();
                }
            }
        }));
    <% } %>
        actionsMenu.addChild(new dijit.MenuItem({
            label: "Cancel",
            iconClass: "dijitEditorIcon dijitEditorIconCancel",
            onClick: function() {
                document.forms['cancelEditing'].submit();
            }
        }));

        topMenuBar.addChild(new dijit.PopupMenuBarItem({
            label: "Actions",
            popup: actionsMenu
        }));
    }
    topMenuBar.placeAt("TopMenu");
    topMenuBar.startup();
}
</script>
<form action="<pwm:url url='ConfigManager'/>" method="post" name="completeEditing"
      enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="processAction" value="finishEditing"/>
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<form action="<pwm:url url='ConfigManager'/>" method="post" name="cancelEditing"
      enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="processAction" value="cancelEditing"/>
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<% if (showNotes) { %>
<br/>
<div style="width:600px;">
    <div style="width:600px; text-align:center;"><label for="notesTextarea"><h2>Configuration Notes</h2></label></div>
    <textarea style="height:10px" cols="40" rows="1" id="notesTextarea"></textarea>
</div>
<script type="text/javascript">
    dojo.require("dijit.form.Textarea");
    var notesTextarea = new dijit.form.Textarea({
        disabled: false,
        style: "width: 600px",
        onChange: function() {
            dojo.xhrPost({
                url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&updateNotesText=true",
                postData: dojo.toJson(this.value),
                contentType: "application/json;charset=utf-8",
                dataType: "json",
                handleAs: "text",
                sync: true,
                error: function(errorObj) {
                    showError("error saving notes text, reason: " + errorObj)
                }
            });
        }
    }, "notesTextarea");
    dojo.xhrGet({
        url:"ConfigManager?processAction=getOptions&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
        dataType: "json",
        handleAs: "json",
        error: function(errorObj) {
            showError("error reading notes text, reason: " + errorObj)
        },
        load: function(data){
            var value = data['notesText'];
            notesTextarea.set('value',value);
        }
    });
</script>
<br/>
<% } %>
<div id="mainContentPane" style="width: 600px">
</div>
<script type="text/javascript">
    var mainPane = dojo.addOnLoad(function() {
        dojo.require("dojox.layout.ContentPane");
        new dojox.layout.ContentPane({
            executeScripts: true
        }, "mainContentPane");
    });
</script>
<script type="text/javascript">
    dojo.addOnLoad(function() { <%-- select the first category --%>
        dijit.byId('mainContentPane').set('href', 'ConfigManager?processAction=editorPanel');
        buildMenuBar();
    });
</script>
</div>
</div>
<br/>
<br/>
<div style="background:  black; color: white;"><%@ include file="footer.jsp" %></div>
<script type="text/javascript">
    function loadMainPageBody() {
        window.location = "ConfigManager";
    }
</script>
</body>
<script type="text/javascript">
    if(dojo.isIE <= 7){ // only IE7 and below
        alert('Internet Explorer 7 and below is not able to correctly load this page.  Please use a newer version of IE or a different browser.');
    }
</script>
</html>
