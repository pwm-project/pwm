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
    <div id="centerbody" style="width: 800px">
        <% if (PwmSession.getSessionStateBean(session).getSessionError() != null) { %>
        <span style="width:680px" id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
        <% } else { %>
        <span style="visibility:hidden; width:680px" id="error_msg" class="msg-success"> </span>
        <% } %>
        <br class="clear"/>
        <table border="0" style="border:0">
            <tr style="border:0">
                <td style="vertical-align:top; border:0">
                    <div id="leftNav" style="text-align:right;" class="tundra">
                        <div id="navMenu">
                            <% for (final PwmSetting.Category loopCategory : PwmSetting.valuesByCategory(PwmSession.getPwmSession(session).getConfigManagerBean().getLevel()).keySet()) { %>
                            <div id="categoryMenu_<%=loopCategory.toString()%>">
                                <%=loopCategory.getLabel(request.getLocale())%>
                            </div>
                            <script type="text/javascript">
                                dojo.require("dijit.MenuItem");
                                var item = new dijit.MenuItem({
                                    onClick: function() {
                                        selectCategory('<%=loopCategory.toString()%>');
                                    }
                                }, "categoryMenu_<%=loopCategory.toString()%>");
                                menuItems.push(item);
                            </script>
                            <% } %>
                        </div>
                        <script type="text/javascript">
                            <%-- create nav menu --%>
                            dojo.require("dijit.Menu");
                            var menuBar = new dijit.Menu({}, "navMenu");

                            <%-- add menu seperator --%>
                            menuBar.addChild(new dijit.MenuSeparator());

                            /*
                             var fieldEditor = new dijit.MenuItem({
                             id: "fieldEditor",
                             label: "Display Field Editor",
                             onClick: function() {
                             dijit.byId('mainContentPane').set('href', 'ConfigManager?processAction=editorPanel&category=fieldEditor');
                             }
                             });
                             menuBar.addChild(fieldEditor);
                            <%-- add menu seperator --%>
                             menuBar.addChild(new dijit.MenuSeparator());
                             */

                            <% if (level == PwmSetting.Level.BASIC) { %>
                            var switchModeAdvanced = new dijit.MenuItem({
                                id: "switchMode",
                                label: "Show All Settings",
                                onClick: function() {
                                    showWaitDialog('Loading...');
                                    getObject('levelAdvanced').submit();
                                }
                            });
                            menuBar.addChild(switchModeAdvanced);
                            <% } else { %>
                            var switchModeBasic = new dijit.MenuItem({
                                id: "switchMode",
                                label: "Show Basic Settngs",
                                onClick: function() {
                                    showWaitDialog('Loading...');
                                    getObject('levelBasic').submit();
                                }
                            });
                            menuBar.addChild(switchModeBasic);
                            <% } %>

                            <%-- add save/update and cancel button --%>
                            menuBar.addChild(new dijit.MenuSeparator());
                            <%-- add menu seperator --%>
                            <% if (ContextManager.getContextManager(session).getConfigReader().getConfigMode() == ConfigurationReader.MODE.RUNNING) { %>
                            var updateButtonMenuItem = new dijit.MenuItem({
                                id: "updateButton",
                                label: "Finished",
                                onClick: function() {
                                    showWaitDialog('Updating Configuration');
                                    setTimeout(function() {
                                        document.forms['completeEditing'].submit();
                                    }, 1000)
                                }
                            });
                            menuBar.addChild(updateButtonMenuItem);
                            <% } else { %>
                            var saveButtonMenuItem = new dijit.MenuItem({
                                id: "saveButton",
                                label: "Save",
                                onClick: function() {
                                    if (confirm('Are you sure you want to save the changes to the current PWM configuration?')) {
                                        saveConfiguration();
                                    }
                                }
                            });
                            menuBar.addChild(saveButtonMenuItem);
                            <% } %>
                            var cancelButtonMenuItem = new dijit.MenuItem({
                                id: "cancelButton",
                                label: "Cancel",
                                onClick: function() {
                                    showWaitDialog('Canceling...');
                                    setTimeout(function() {
                                        document.forms['cancelEditing'].submit();
                                    }, 1000)
                                }
                            });
                            menuBar.addChild(cancelButtonMenuItem);

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
                    </div>
                </td>
                <td style="border:0" width="600">
                    <div id="mainContentPane" style="width: 600px">
                    </div>
                </td>
            </tr>
        </table>
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
