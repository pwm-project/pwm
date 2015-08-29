<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.bean.GuestRegistrationBean" %>
<%@ page import="password.pwm.http.servlet.GuestRegistrationServlet" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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
<%
    final PwmRequest pwmRequest = PwmRequest.forRequest(request,response);
    final GuestRegistrationBean guestBean = pwmRequest.getPwmSession().getSessionBean(GuestRegistrationBean.class);
    final GuestRegistrationServlet.Page currentPage = guestBean.getCurrentPage();
%>
<br/>
<div style="text-align: center">
    <% boolean selected = currentPage == GuestRegistrationServlet.Page.create; %>
    <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" id="dashboard" name="dashboard">
        <button type="submit" class="navbutton<%=selected?" selected":""%>">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-user-plus"></span></pwm:if>
            <pwm:display key="Title_GuestRegistration"/>
        </button>
        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        <input type="hidden" name="processAction" value="selectPage"/>
        <input type="hidden" name="page" value="create"/>
    </form>
    <% selected = currentPage == GuestRegistrationServlet.Page.search; %>
    <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" id="activity" name="activity">
        <button type="submit" class="navbutton<%=selected?" selected":""%>">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-calendar"></span></pwm:if>
            <pwm:display key="Title_GuestUpdate"/>
        </button>
        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        <input type="hidden" name="processAction" value="selectPage"/>
        <input type="hidden" name="page" value="search"/>
    </form>
</div>
<br/>
