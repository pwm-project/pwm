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
    <div id="splatbutton" style="text-align: center; padding-top: 80px">
        <button onclick="fetchRandomPassword();getObject('splatbutton').style.visibility = 'hidden'">Splat</button>
    </div>
    <script type="text/javascript">
        var MAX_NODES = 5 * 1000;
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

            var topPos = Math.floor((Math.random() * vs.h));
            var leftPos = Math.floor((Math.random() * vs.w));
            var bottomPos = vs.h - topPos;
            var rightPos = vs.w - leftPos;

            var styleText = "position: absolute; ";
            if (topPos > (vs.h / 2)) {
                styleText = styleText + "bottom: " + bottomPos + "px; ";
            } else {
                styleText = styleText + "top: " + topPos + "px; ";
            }
            if (leftPos > (vs.w / 2)) {
                styleText = styleText + "right: " + rightPos +"px; ";
            } else {
                styleText = styleText + "left: " + leftPos +"px; ";
            }


            styleText = styleText + "padding: 4px; z-index:2; border-radius: 5px;";

            var div = document.createElement('div');
            div.innerHTML = text;
            div.id = id;
            div.setAttribute("class",'health-' + PWM_GLOBAL['pwm-health']);

            div.setAttribute("style",styleText);
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
                    var password = "server unreachable";
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
            dojo.require()
        });

    </script>
</div>
</body>
</html>
