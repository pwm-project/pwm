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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_GuestUpdate"/>
    </jsp:include>
    <div id="centerbody">
        <p id="registration-menu-bar" style="text-align:center;">
            <a class="menubutton" href="GuestRegistration?menuSelect=create&pwmFormID=<pwm:FormID/>"><pwm:display key="Title_GuestRegistration"/></a>
            &nbsp;&nbsp;&nbsp;
            <a class="menubutton" href="GuestRegistration?menuSelect=search&pwmFormID=<pwm:FormID/>"><pwm:display key="Title_GuestUpdate"/></a>
        </p>
        <br/>
        <p><pwm:display key="Display_GuestUpdate"/></p>
                                                                                      co
        <form action="<pwm:url url='GuestRegistration'/>" method="post" enctype="application/x-www-form-urlencoded" name="searchForm" class="pwm-form" id="searchForm">
            <%@ include file="fragment/message.jsp" %>
            <h2><label for="username"><pwm:display key="Field_Username"/></label></h2>
            <input type="text" id="username" name="username" class="inputfield"/>

            <div id="buttonbar">
                <input type="hidden" name="processAction" value="search"/>
                <input type="submit" class="btn" name="search" value="<pwm:display key="Button_Search"/>" id="submitBtn"/>
                <%@ include file="/WEB-INF/jsp/fragment/button-reset.jsp" %>
                <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.getObject('username').focus();
    });
</script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

