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

<%@ page import="password.pwm.http.servlet.admin.AdminServlet" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.JspUtility" %>

<%@ taglib uri="pwm" prefix="pwm" %>
<% final AdminServlet.Page currentPage = AdminServlet.Page.forUrl(JspUtility.getPwmRequest(pageContext).getURL()); %>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<pwm:script>
    <script type="text/javascript">
        var PWM_ADMIN = PWM_ADMIN || {};

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_ADMIN.initAdminNavMenu();
        });
    </script>
</pwm:script>
<div style="text-align: center; margin-bottom: 10px;">
    <% boolean selected = currentPage == AdminServlet.Page.dashboard; %>
    <form action="<%=AdminServlet.Page.dashboard%>" method="get" id="dashboard" name="dashboard">
        <button type="submit" class="navbutton<%=selected?" selected":""%>">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-dashboard"></span></pwm:if>
            <pwm:display key="Title_Dashboard" bundle="Admin"/>
        </button>
    </form>
    <% selected = currentPage == AdminServlet.Page.activity; %>
    <form action="<%=AdminServlet.Page.activity%>" method="get" id="activity" name="activity">
        <button type="submit" class="navbutton<%=selected?" selected":""%>">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-users"></span></pwm:if>
            <pwm:display key="Title_UserActivity" bundle="Admin"/>
        </button>
    </form>
    <% selected = currentPage == AdminServlet.Page.analysis; %>
    <form action="<%=AdminServlet.Page.analysis%>" method="get" id="analysis" name="analysis">
        <button type="submit" class="navbutton<%=selected?" selected":""%>">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-bar-chart-o"></span></pwm:if>
            <pwm:display key="Title_DataAnalysis" bundle="Admin"/>
        </button>
    </form>
    <div style="display: inline" id="admin-nav-menu-container">
    </div>
</div>


