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
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% try { password.pwm.PwmSession.getPwmSession(session).unauthenticateUser(); } catch (Exception e) { }%>
<body class="tundra">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="PWM Health"/>
</jsp:include>
<div id="wrapper">
    <div id="centerbody">
        <div id style="z-index: 3; position: relative; background: white; opacity: 0.9">
            <table class="tablemain">
                <tr>
                    <td class="title" colspan="10">
                        <pwm:Display key="Title_Application"/> Health
                    </td>
                </tr>
                <tr>
                    <td colspan="10"  style="margin:0; padding:0">
                        <div id="healthBody" style="border:0; margin:0; padding:0"></div>
                    </td>
                </tr>
            </table>
        </div>
        <div id style="z-index: 3; position: relative; background: white; text-align: center; opacity: 0.8; margin-left: auto; margin-right: auto; white-space: nowrap;">
        This page refreshes automatically.
            </div>
    </div>
    <div id="floatparent">
    </div>
    <script type="text/javascript">
        var MAX_NODES = 50 * 1000;
        PWM_GLOBAL['pwm-health'] = 'GOOD';

        function displayRandomFloat(id, text) {
            var floatParent = getObject("floatparent");
            { // remove old node
                var existingDiv = getObject(id);
                if (existingDiv != null) {
                    floatParent.removeChild(existingDiv);
                }
            }
            dojo.require("dojo.window");
            var vs = dojo.window.getBox();
            var cellHeight = 20;
            var cellWidth = 80;

            var topPos = Math.floor((Math.random() * (vs.h - 70)) - cellHeight) + 70; //header is 70px
            var leftPos = Math.floor((Math.random() * vs.w) - cellWidth);

            var div = document.createElement('div');
            div.innerHTML = text;
            div.id = id;
            div.setAttribute("class",'health-' + PWM_GLOBAL['pwm-health']);
            div.setAttribute("style","position: absolute; top: " + topPos + "px; left: " + leftPos +"px; padding: 4px; z-index:2; border-radius: 5px;");
            floatParent.appendChild(div);
        }

        function fetchRandomPassword() {
            dojo.xhrGet({
                url: PWM_GLOBAL['url-rest-public'] + "/randompassword",
                headers: {"Accept":"application/json"},
                dataType: "json",
                timeout: 15000,
                sync: false,
                handleAs: "json",
                load:  function(resultInfo) {
                    if (resultInfo["version"] != "1") {
                        return;
                    }
                    var password = resultInfo["password"];
                    var randomId = "randomPwDiv" + Math.floor(Math.random() * MAX_NODES);
                    displayRandomFloat(randomId,password);
                    setTimeout(function(){
                        fetchRandomPassword();
                    },5 * 1000);
                },
                error: function(errorObj){
                    var password = "------------------";
                    var randomId = "randomPwDiv" + Math.floor(Math.random() * MAX_NODES);
                    displayRandomFloat(randomId,password);
                    setTimeout(function(){
                        fetchRandomPassword();
                    },5 * 1000);
                }
            });
        }

        function clearAllNodes() {
            var floatParent = getObject("floatparent");
            for (var i = 0; i < MAX_NODES; i++) {
                var existingDiv = getObject("randomPwDiv"+i);
                if (existingDiv != null) {
                    floatParent.removeChild(existingDiv);
                }
            }
        }

        dojo.addOnLoad(function(){
            showPwmHealth('healthBody', false);
            setTimeout(function(){
                fetchRandomPassword();
            },10 * 1000);
            /*
            window.onresize = function(event) {
                clearAllNodes();
            }
            */
        });


    </script>
</div>
</body>
</html>
