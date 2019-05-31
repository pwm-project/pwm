<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.util.logging.PwmLogLevel" %>

<%@ taglib uri="pwm" prefix="pwm" %>

<div id="logViewer-wrapper">
    <div>
        <form action="<pwm:context/>/private/admin" method="post" enctype="application/x-www-form-urlencoded"
              name="form-loadLog" id="form-loadLog" class="">
            <table style="max-width: 700px">
                <tr class="noborder">
                    <td class="noborder">
                        <label for="level">Level</label>
                    </td>
                    <td class="noborder">
                        <label for="type">Type</label>
                    </td>
                    <td class="noborder">
                        <label for="username">Username</label>
                    </td>
                    <td class="noborder">
                        <label for="text">Containing text</label>
                    </td>
                    <td class="noborder">
                        <label for="count">Max Rows</label>
                    </td>
                    <td class="noborder">
                        <label for="maxTime">Max Time (Seconds)</label>
                    </td>
                    <td class="noborder">
                        <label for="displayType">Display</label>
                    </td>
                    <td class="noborder">
                    </td>
                </tr>
                <tr class="noborder">
                    <td class="noborder">
                        <% final PwmLogLevel configuredLevel = JspUtility.getPwmRequest( pageContext ).getConfig().readSettingAsEnum(PwmSetting.EVENTS_LOCALDB_LOG_LEVEL,PwmLogLevel.class); %>
                        <select name="level" style="width: auto;" id="level">
                            <% for (final PwmLogLevel level : PwmLogLevel.values()) { %>
                            <% final boolean disabled = level.compareTo(configuredLevel) < 0; %>
                            <option value="<%=level%>"<%=disabled ? " disabled" : ""%>><%=level%></option>
                            <% } %>
                        </select>
                    </td>
                    <td class="noborder">
                        <select id="type" name="type" style="width:auto">
                            <option value="User">User</option>
                            <option value="System">System
                            </option>
                            <option value="Both" selected="selected">Both</option>
                        </select>
                    </td>
                    <td class="noborder">
                        <input name="username" type="text" id="username"/>
                    </td>
                    <td class="noborder">
                        <input style="width: 100%" name="text" type="text" id="text"/>
                    </td>
                    <td class="noborder">
                        <input type="number" id="count" name="count" step="500" value="1000" min="100" max="100000"/>
                    </td>
                    <td class="noborder">
                        <input type="number" id="maxTime" name="maxTime" step="5" value="30" min="10" max="120"/>
                    </td>
                    <td class="noborder">
                        <select id="displayType" name="displayText" style="width: auto">
                            <option value="lines">Text</option>
                            <option value="grid">Table</option>
                        </select>
                    </td>
                    <td class="key noborder" style="vertical-align: middle">
                        <button type="button" name="button-search" id="button-search" class="btn" style="white-space: nowrap">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-search"></span></pwm:if>
                            Load Data
                        </button>
                    </td>
                </tr>
            </table>
        </form>
    </div>
    <br/>
    <div id="div-noResultsMessage" class="hidden" style="min-height: 200px">
        <p style="text-align:center;" >
            No events matched the search settings. Please refine your search query and try again.
        </p>
    </div>
    <div style="margin: 20px; min-height: 200px">
        <div id="wrapper-logViewerGrid" class="hidden">
            <div id="logViewerGrid" >
            </div>
        </div>
        <div id="wrapper-lineViewer" class="hidden">
            <div id="lineViewer" class="logViewer">

            </div>
        </div>
    </div>
    <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                PWM_MAIN.addEventHandler('button-search','click',function(){
                    PWM_ADMIN.refreshLogData();
                });
                PWM_ADMIN.initLogGrid();
                PWM_ADMIN.refreshLogData();
            });
        </script>
    </pwm:script>
</div>
