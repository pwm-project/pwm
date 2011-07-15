<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2011 The PWM Project
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/header.jsp" %>
<% password.pwm.PwmSession.getPwmSession(session).unauthenticateUser(); %>
<body class="tundra">
<div id="wrapper">
    <div id="centerbody">
        <table class="tablemain">
            <tr>
                <td class="title" colspan="10">
                    <pwm:Display key="APPLICATION-TITLE"/> Health
                </td>
            </tr>
            <tr>
                <td colspan="10" style="border:0; margin:0; padding:0">
                    <div id="healthBody" style="border:0; margin:0; padding:0"></div>
                    <script type="text/javascript">
                        dojo.addOnLoad(function() {
                            showPwmHealth('healthBody', false);
                        });
                    </script>
                </td>
            </tr>
        </table>
        <br/>
        <br/>

        <p style="text-align:center;">This page refreshes automatically.</p>
    </div>
</div>
</body>
</html>
