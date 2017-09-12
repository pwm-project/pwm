<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<table>
    <tr>
        <td>Forward URL</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getForwardUrl())%></td>
    </tr>
    <tr>
        <td>Logout URL</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getLogoutURL())%></td>
    </tr>
    <tr>
        <td>Locale</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getLocale().toString())%></td>
    </tr>
    <tr>
        <td>Theme</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getPwmSession().getSessionStateBean().getTheme())%></td>
    </tr>
    <tr>
        <td>Instance ID</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getPwmApplication().getInstanceID())%></td>
    </tr>
</table>
