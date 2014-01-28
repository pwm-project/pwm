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

<%@ taglib uri="pwm" prefix="pwm" %>
<div id="TopMenu" style="width: 600px">
</div>
<br/>
<script defer type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/admin.js'/>"></script>
<script type="text/javascript" async="async">
    var PWM_ADMIN = PWM_ADMIN || {};

    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_ADMIN.initAdminOtherMenu();
    });
</script>
<style type="text/css">
    .menubutton {
        cursor: pointer;
        display: inline;
        font-weight: normal;
    }

    .menubutton.selected {
        box-shadow: none;
    }
</style>
<div style="text-align: center">
    <% boolean selected = request.getRequestURI().contains("dashboard.jsp"); %>
    <a class="menubutton<%=selected?" selected":""%>" onclick="PWM_MAIN.goto('/private/admin/dashboard.jsp')">
        <span class="fa fa-dashboard"></span>&nbsp;Dashboard
    </a>
    <% selected = request.getRequestURI().contains("activity.jsp"); %>
    <a class="menubutton<%=selected?" selected":""%>" onclick="PWM_MAIN.goto('/private/admin/activity.jsp')">
        <span class="fa fa-users"></span>&nbsp;User Activity
    </a>
    <% selected = request.getRequestURI().contains("analysis.jsp"); %>
    <a class="menubutton<%=selected?" selected":""%>" onclick="PWM_MAIN.goto('/private/admin/analysis.jsp')">
        <span class="fa fa-bar-chart-o"></span>&nbsp;Data Analysis
    </a>
    <div style="display: inline" id="dropDownButtonContainer">
    </div>
</div>
<br/>
