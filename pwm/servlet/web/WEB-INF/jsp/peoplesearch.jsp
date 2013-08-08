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

<%@ page import="com.google.gson.Gson" %>
<%@ page import="password.pwm.bean.servlet.PeopleSearchBean" %>
<%@ page import="password.pwm.util.operations.UserSearchEngine" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final PeopleSearchBean peopleSearchBean = (PeopleSearchBean)pwmSession.getSessionBean(PeopleSearchBean.class); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PeopleSearch"/>
    </jsp:include>
    <div id="centerbody">
        <form action="<pwm:url url='PeopleSearch'/>" method="post" enctype="application/x-www-form-urlencoded" name="search"
              onsubmit="return handleFormSubmit('submitBtn',this)">
            <%@ include file="fragment/message.jsp" %>
            <p>&nbsp;</p>

            <p><pwm:Display key="Display_PeopleSearch"/></p>
            <input type="search" id="username" name="username" class="inputfield"
                   value="<%=peopleSearchBean.getSearchString()!=null?peopleSearchBean.getSearchString():""%>"/>
            <input type="submit" class="btn"
                   name="search"
                   value="<pwm:Display key="Button_Search"/>"
                   id="submitBtn"/>
            <input type="hidden"
                   name="processAction"
                   value="search"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
            <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                    onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                <pwm:Display key="Button_Cancel"/>
            </button>
            <% } %>
        </form>
        <br class="clear"/>
        <% final UserSearchEngine.UserSearchResults searchResults = peopleSearchBean.getSearchResults(); %>
        <% if (searchResults != null) { %>
        <% final Gson gson = new Gson(); %>
        <noscript>
            <span>Javascript is required to view this page.</span>
            <%--
            <div style="max-height: 360px; overflow: auto;">
                <table>
                    <tr>
                        <% for (final String keyName : searchResults.getHeaders()) { %>
                        <td class="key" style="text-align: left; white-space: nowrap;">
                            <%=keyName%>
                        </td>
                        <% } %>
                    </tr>
                    <% for (final String userDN: searchResults.getResults().keySet()) { %>
                    <tr>
                        <% for (final String attribute : searchResults.getAttributes()) { %>
                        <% final String value = searchResults.getResults().get(userDN).get(attribute); %>
                        <% final String userKey = PeopleSearchServlet.makeUserDetailKey(userDN,pwmSession); %>
                        <td id="userDN-<%=userDN%>">
                        <a href="PeopleSearch?pwmFormID=<%=Helper.buildPwmFormID(pwmSession.getSessionStateBean())%>&processAction=detail&userKey=<%=userKey%>">
                        <%= value == null || value.length() < 1 ? "&nbsp;" : value %>
                        </a>
                        </td>
                        <% } %>
                    </tr>
                    <% } %>
                </table>
            </div>
            --%>
        </noscript>
        <div id="waitMessage" style="width:100%; text-align: center; display: none">
            <pwm:Display key="Display_PleaseWait"/>
        </div>
        <div id="grid">
        </div>
        <script async="async">
            PWM_GLOBAL['startupFunctions'].push(function(){
                require(["dojo/domReady!"],function(){
                    getObject("waitMessage").style.display = 'inline';
                    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                            function(dojo,declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                                var data = <%=gson.toJson(searchResults.resultsAsJsonOutput(pwmSession))%>;
                                var columnHeaders = <%=gson.toJson(searchResults.getHeaderAttributeMap())%>;

                                // Create a new constructor by mixing in the components
                                var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                                // Now, create an instance of our custom grid which
                                // have the features we added!
                                var grid = new CustomGrid({
                                    columns: columnHeaders
                                }, "grid");
                                grid.renderArray(data);
                                grid.set("sort","<%=searchResults.getHeaderAttributeMap().keySet().iterator().next()%>");
                                grid.on(".dgrid-row .dgrid-cell:click", function(evt){
                                    var row = grid.row(evt);
                                    loadDetails(row.data['userKey']);
                                });
                                getObject("waitMessage").style.display = 'none';
                            });
                });
            });
        </script>
        <style scoped="scoped">
            .dgrid { height: auto; }
            .dgrid .dgrid-scroller { position: relative; max-height: 360px; overflow: auto; }
        </style>
        <br/>
        <% if (searchResults.isSizeExceeded()) { %>
        <div style="width:100%; text-align: center; font-size: smaller">
            <pwm:Display key="Display_SearchResultsExceeded"/>
        </div>
        <% } %>
        <% } else if (peopleSearchBean.getSearchString() != null && peopleSearchBean.getSearchString().length() > 0) { %>
        <div style="width:100%; text-align: center">
            <pwm:Display key="Display_SearchResultsNone"/>
        </div>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<form id="loadDetailsForm" name="loadDetailsForm" method="post" enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
    <input type="hidden" name="processAction" value="detail"/>
    <input type="hidden" name="userKey" id="userKey" value=""/>
</form>
<script type="text/javascript">
    function loadDetails(userKey) {
        showWaitDialog(null,null,function(){
            setTimeout(function(){
                getObject("userKey").value = userKey;
                getObject("loadDetailsForm").submit();
            },10);
        });
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('username').focus();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
