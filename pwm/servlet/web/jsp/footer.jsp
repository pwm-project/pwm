<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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
<%@ page import="java.text.SimpleDateFormat" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%-- begin pwm footer --%>
<div id="footer">
    
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/<pwm:url url='idletimer.js'/>"></script>
    <span class="idle_status" id="idle_status">
        &nbsp;
    </span>
    <br/>
    PWM <%= password.pwm.Constants.SERVLET_VERSION %> |
    <% // can't use <jsp:UseBean> because it will cause duplicate useBean error.
        final password.pwm.bean.UserInfoBean uiBean = PwmSession.getUserInfoBean(request.getSession());
        final String userID = uiBean.getUserID();
        if (userID != null && userID.length() > 0) {
    %>
    <%= userID %> |
    <% } %>

    <%
        final password.pwm.bean.SessionStateBean sessionStateBean = PwmSession.getSessionStateBean(request.getSession());
        final java.text.DateFormat df = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.DEFAULT, SimpleDateFormat.DEFAULT, request.getLocale());
        out.write(df.format(new java.util.Date()));
    %>
    |
    src:
    <%
        final String userIP = sessionStateBean.getSrcAddress();
        if (userIP == null) {
            out.write("unknown");
        } else {
            out.write(userIP);
        }
    %>
    <%-- hidden fields for javascript display fields --%>
    <form action="" name="footer_i18n">
        <input type="hidden" name="Js_Display_IdleTimeout" id="Js_Display_IdleTimeout" value="<pwm:Display key="Display_IdleTimeout"/>"/>
        <input type="hidden" name="Js_Display_Day" id="Js_Display_Day" value="<pwm:Display key="Display_Day"/>"/>
        <input type="hidden" name="Js_Display_Days" id="Js_Display_Days" value="<pwm:Display key="Display_Days"/>"/>
        <input type="hidden" name="Js_Display_Hour" id="Js_Display_Hour" value="<pwm:Display key="Display_Hour"/>"/>
        <input type="hidden" name="Js_Display_Hours" id="Js_Display_Hours" value="<pwm:Display key="Display_Hours"/>"/>
        <input type="hidden" name="Js_Display_Minute" id="Js_Display_Minute" value="<pwm:Display key="Display_Minute"/>"/>
        <input type="hidden" name="Js_Display_Minutes" id="Js_Display_Minutes" value="<pwm:Display key="Display_Minutes"/>"/>
        <input type="hidden" name="Js_Display_Second" id="Js_Display_Second" value="<pwm:Display key="Display_Second"/>"/>
        <input type="hidden" name="Js_Display_Seconds" id="Js_Display_Seconds" value="<pwm:Display key="Display_Seconds"/>"/>
        <input type="hidden" name="Js_Display_PleaseWait" id="Js_Display_PleaseWait" value="   <pwm:Display key="Display_PleaseWait"/>   "/>

        <%-- add the url's here so that anything rewriting html (ichain/nam) has an opportunity to see these --%>
        <input type="hidden" name="Js_LogoutURL" id="Js_LogoutURL" value="<%=request.getContextPath()%>/public/<pwm:url url='Logout?idle=true'/>"/>
        <input type="hidden" name="Js_CommandURL" id="Js_CommandURL" value="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>"/>
    </form>
    <script type="text/javascript">initCountDownTimer(<%= request.getSession().getMaxInactiveInterval() - 10 %>);</script>
</div>
