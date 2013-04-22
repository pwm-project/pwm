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
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<script type="text/javascript"
        src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/changepassword.js'/>"></script>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Random Passwords"/>
    </jsp:include>
    <table id="mastertable">
    </table>
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
                getObject('mastertable').innerHTML = innerHtmlText;
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
            fetchRandoms(randomConfig);
        }


        require(["dojo/ready"],function(){
            makeTable();
            window.onresize = function(event) {
                makeTable();
            }
        });
    </script>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>

