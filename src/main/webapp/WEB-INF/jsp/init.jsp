<%@ page import="password.pwm.util.StringUtil" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final String nextURL = (String)request.getAttribute("Location"); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<meta id="meta-redirect" http-equiv="refresh" content="30;url='<%=StringUtil.escapeHtml(nextURL)%>'"><%-- failsafe... --%>
<div id="wrapper">
    <div style="height:100px">&nbsp;</div>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p style="width: 100%; text-align: center">
            <pwm:display key="Display_PleaseWait"/>
            <br/>
            <br/>
        <div class="WaitDialogBlank"></div>

        </p>

        <div class="buttonbar"></div>
        <p id="failsafeAnchor">
            <a href="<%=StringUtil.escapeHtml(nextURL)%>">Click here to continue...</a>
        </p>
        <pwm:script>
            <script type="text/javascript">
                var div = document.getElementById('failsafeAnchor');
                while(div.firstChild){
                    div.removeChild(div.firstChild);
                }
            </script>
        </pwm:script>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.preloadAll(function(){
                PWM_MAIN.goto('<%=StringUtil.escapeJS(nextURL)%>');
            });
        });
    </script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_IDLE); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
