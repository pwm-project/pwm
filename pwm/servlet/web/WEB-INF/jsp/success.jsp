<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Success"/>
    </jsp:include>
    <div id="centerbody">
        <form action="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>" method="post"
              enctype="application/x-www-form-urlencoded" onsubmit="handleFormSubmit('submitBtn',this);return false">
            <p><pwm:SuccessMessage/></p>
            <% try { PwmSession.getPwmSession(session).getSessionStateBean().setSessionSuccess(null,null); } catch (Exception e) {} %>
            <div id="buttonbar">
                <input type="hidden"
                       name="processAction"
                       value="continue"/>
                <input type="submit" name="button" class="btn"
                       value="<pwm:Display key="Button_Continue"/>"
                       id="submitBtn"/>
            </div>
        </form>
    </div>
   <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
