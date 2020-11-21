<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Random Passwords"/>
    </jsp:include>
    <table id="mastertable">
    </table>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    var cellCount = 0;
    function makeTable() {
        require(["dojo","dojo/window"],function(dojo){
            var vs = dojo.window.getBox();
            var cellHeight = 20;
            var cellWidth = 150;

            var rows = Math.floor(vs.w / cellWidth);
            var columns = Math.floor((vs.h - 70 - 20) / cellHeight); //70 for header, 50 for footer

            var innerHtmlText = "";
            var position = 0;
            var fetchList = new Array();
            for (var columnCounter = 0; columnCounter < columns; columnCounter++) {
                innerHtmlText = innerHtmlText + '<tr>';
                for (var rowCounter = 0; rowCounter < rows; rowCounter++) {
                    position = position + 1;
                    var idString = "randomGen" + position;
                    fetchList[position] = idString;
                    innerHtmlText = innerHtmlText + '<td id="' + idString + '" style="text-align: center;" width="'+cellWidth+'">&nbsp;</td>';
                }
                innerHtmlText = innerHtmlText + '</tr>';
            }
            PWM_MAIN.getObject('mastertable').innerHTML = innerHtmlText;
            initFetchProcess(fetchList);
            cellCount = position;
        });
    }

    function initFetchProcess(fetchList) {
        for (var i = 0; i < fetchList.length; i++) {
            fetchList.sort(function() {
                return 0.5 - Math.random()
            });
        }
        var randomConfig = {};
        randomConfig['fetchList'] = fetchList;
        PWM_CHANGEPW.fetchRandoms(randomConfig);
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/ready"],function(){
            makeTable();
            window.onresize = function(event) {
                makeTable();
            }
        });
    });
</script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/changepassword.js"/>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
