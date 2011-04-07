<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2010 The PWM Project
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
<%@ page import="password.pwm.config.ConfigurationReader" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="java.util.Collection" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<% final Collection<Locale> localeList = ContextManager.getContextManager(session).getKnownLocales(); %>
<% localeList.remove(Helper.localeResolver(Locale.getDefault(), localeList)); %>
<% final PwmSetting.Level level = PwmSession.getPwmSession(session).getConfigManagerBean().getLevel(); %>
<body class="tundra">
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/configmanager.js"></script>
<script type="text/javascript"><% { for (final Locale loopLocale : localeList) { %>availableLocales['<%=loopLocale%>'] = '<%=loopLocale.getDisplayName()%>'; <% }
} %></script>
<div id="wrapper" style="border:1px">
    <jsp:include page="header-body.jsp">
        <jsp:param name="pwm.PageName" value="PWM Configuration Editor"/>
    </jsp:include>
    <div id="centerbody" style="width: 600px">
        <% if (PwmSession.getSessionStateBean(session).getSessionError() != null) { %>
        <span style="width:680px" id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
        <% } else { %>
        <span style="visibility:hidden; width:680px" id="error_msg" class="msg-success"> </span>
        <% } %>
        <br class="clear"/>
        <div id="TopMenu" style="width:600px">
        </div>
        <script type="text/javascript">
            dojo.require("dijit.MenuBar");
            dojo.require("dijit.MenuItem");
            dojo.require("dijit.PopupMenuBarItem");
            dojo.addOnLoad(function() {
                var pMenuBar = new dijit.MenuBar({});
                { // Category Menu
                    var pSubMenu = new dijit.Menu({});
                <% for (final PwmSetting.Category loopCategory : PwmSetting.valuesByCategory(PwmSession.getPwmSession(session).getConfigManagerBean().getLevel()).keySet()) { %>
                    pSubMenu.addChild(new dijit.MenuItem({
                        label: '<%=loopCategory.getLabel(request.getLocale())%>',
                        onClick: function() {
                            selectCategory('<%=loopCategory.toString()%>');
                        }
                    }));
                <% } %>
                    pMenuBar.addChild(new dijit.PopupMenuBarItem({
                        label: "Category",
                        popup: pSubMenu
                    }));
                }
                { // Edit
                    var pSubMenu = new dijit.Menu({});
                    pSubMenu.addChild(new dijit.MenuItem({
                        <% if (level == PwmSetting.Level.BASIC) { %>
                        label: "Show All Settings",
                        onClick: function() {
                            showWaitDialog('Loading...');
                            getObject('levelAdvanced').submit();
                        },
                        checked: true
                        <% } else { %>
                        label: "Show Basic Settings",
                        onClick: function() {
                            showWaitDialog('Loading...');
                            getObject('levelBasic').submit();
                        },
                        checked: true
                        <% } %>
                    }));
                    pMenuBar.addChild(new dijit.PopupMenuBarItem({
                        label: "View",
                        popup: pSubMenu
                    }));
                }
                { // Actions
                    var pSubMenu = new dijit.Menu({});

                <% if (ContextManager.getContextManager(session).getConfigReader().getConfigMode() == ConfigurationReader.MODE.RUNNING) { %>
                    pSubMenu.addChild(new dijit.MenuItem({
                        label: "Finish Editing",
                        onClick: function() {
                            showWaitDialog('Updating Configuration');
                            setTimeout(function() {
                                document.forms['completeEditing'].submit();
                            }, 1000)
                        }
                    }));
                <% } else { %>
                    pSubMenu.addChild(new dijit.MenuItem({
                        label: "Save",
                        onClick: function() {
                            if (confirm('Are you sure you want to save the changes to the current PWM configuration?')) {
                                saveConfiguration();
                            }
                        }
                    }));
                <% } %>
                    pSubMenu.addChild(new dijit.MenuItem({
                        label: "Cancel",
                        onClick: function() {
                            showWaitDialog('Canceling...');
                            setTimeout(function() {
                                document.forms['cancelEditing'].submit();
                            }, 1000)
                        }
                    }));

                    pMenuBar.addChild(new dijit.PopupMenuBarItem({
                        label: "Actions",
                        popup: pSubMenu
                    }));
                }
                pMenuBar.placeAt("TopMenu");
                pMenuBar.startup();
            });
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
        <form action="<pwm:url url='ConfigManager'/>" method="get" id="levelAdvanced"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="setLevel"/>
            <input type="hidden" name="level" value="ADVANCED"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <form action="<pwm:url url='ConfigManager'/>" method="get" id="levelBasic"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="setLevel"/>
            <input type="hidden" name="level" value="BASIC"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
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
                selectCategory('<%=PwmSetting.valuesByCategory(PwmSession.getPwmSession(session).getConfigManagerBean().getLevel()).keySet().iterator().next()%>');
            });
        </script>
    </div>
</div>
<%@ include file="footer.jsp" %>
<script type="text/javascript">
    dojo.addOnLoad(function() {
        clearDigitWidget('waitDialog');
    });
</script>
</body>
</html>
