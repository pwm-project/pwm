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
