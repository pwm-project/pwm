<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideServlet" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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

<% ConfigGuideBean headerCgb = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<% float pctComplete = ConfigGuideServlet.stepProgress(headerCgb.getStep()).asFloat(); %>
<div id="header">
    <div id="header-center">
        <div id="header-page">
            <pwm:display key="title" bundle="ConfigGuide"/>
        </div>
    </div>
</div>
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/configeditor.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<progress style="opacity: 0.5; width:96%; height:7px; padding: 0; margin: 0 1%;" value="<%=pctComplete%>" max="100"></progress>
