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

<%@ page import="password.pwm.PwmSession" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="" class="tundra">
<script type="text/javascript"
        src="<%=request.getContextPath()%>/resources/<pwm:url url='changepassword.js'/>"></script>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Random Passwords"/>
    </jsp:include>
    <script type="text/javascript">
        PWM_STRINGS['url-changepassword'] = '<pwm:url url='/pwm/private/ChangePassword'/>';
    </script>
    <audio src="/pwm/resources/fill.mp3" autoplay="autoplay"></audio>
    <table style="border:0">
        <% for (int i = 0; i < 584; i++) { %>
        <tr style="border:0">
            <% for (int j = 0; j < 12; j++) { %>
            <td id="randomGen<%=i%>" style="text-align:center; border:0">&nbsp;</td>
            <% i++;
            } %>
        </tr>
        <% } %>

    </table>
    <script type="text/javascript">
        var fetchList = new Array();
        for (var counter = 0; counter < 580; counter++) {
            fetchList[counter] = 'randomGen' + counter;
        }
        fetchList.sort(function() {
            return 0.5 - Math.random()
        });
        fetchList.sort(function() {
            return 0.5 - Math.random()
        });
        fetchList.sort(function() {
            return 0.5 - Math.random()
        });

        doNextFetch();
        var outstandingFetches = 0;

        function doNextFetch() {
            if (fetchList.length < 1) {
                return;
            }
            var name = fetchList.splice(0, 1);
            var element = getObject(name);
            if (element != null) {
                element.firstChild.nodeValue = '\u00A0';
            }

            if (outstandingFetches > 5) {
                setTimeout(function() {
                    doNextFetch();
                }, 10);
            } else {
                var moreButton = getObject('moreRandomsButton');
                if (moreButton != null) {
                    moreButton.disabled = false;
                    moreButton.focus();
                }
                fetchRandom(function(data) {
                    handleRandomResponse(data, name);
                    outstandingFetches--;
                }, function(errorObj) {
                    outstandingFetches--;
                });
                outstandingFetches++;
                setTimeout(function() {
                    doNextFetch();
                }, 10);
            }
        }
    </script>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>

