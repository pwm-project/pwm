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
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
    <%@ include file="fragment/header.jsp" %>
    <body class="nihilo">
        <!--
    <script type="text/javascript" defer="defer" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/responses.js'/>"></script>
        -->
        <div id="wrapper">
            <jsp:include page="fragment/header-body.jsp">
                <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
            </jsp:include>
            <div id="centerbody">
                <h1><pwm:Display key="Display_SetupOtpSecret"/></h1>
                <p><pwm:Display key="Display_WarnExistingOtpSecret"/></p>
                <form action="<pwm:url url='SetupOtpSecret'/>" method="post" name="setupOtpSecret"
                      enctype="application/x-www-form-urlencoded" onchange="" id="setupOtpSecret"
                      onsubmit="handleFormSubmit('setotpsecret_button', this);
                      return false;">
                    <%@ include file="fragment/message.jsp" %>
                    <div id="buttonbar">
                        <input type="hidden" name="processAction" value="clearOtp"/>
                        <input type="submit" name="Button_Continue" class="btn" id="continue_button"
                               value="<pwm:Display key="Button_Continue"/>"/>
                        <button name="button" class="btn" id="button_cancel" onclick="handleFormCancel();
                        return false;">
                            <pwm:Display key="Button_Cancel"/>
                        </button>
                        <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                    </div>
                </form>
            </div>
            <div class="push"></div>
        </div>
        <%@ include file="fragment/footer.jsp" %>
    </body>
</html>
