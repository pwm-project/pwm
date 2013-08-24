<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="java.io.ByteArrayInputStream" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="java.text.SimpleDateFormat" %>
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

<% X509Certificate certificate = (X509Certificate)request.getAttribute("certificate"); %>
<% final String md5sum = Helper.checksum(new ByteArrayInputStream(certificate.getEncoded()), "MD5"); %>
<% final String sha1sum = Helper.checksum(new ByteArrayInputStream(certificate.getEncoded()), "SHA1"); %>
<table style="width:100%" id="table_certificate0">
    <tr><td colspan="2" class="key" style="text-align: center">
        Certificate
    </td></tr>
    <tr><td>Subject Name</td><td><%=certificate.getSubjectX500Principal().getName()%></td></tr>
    <tr><td>Issuer Name</td><td><%=certificate.getIssuerX500Principal().getName()%></td></tr>
    <tr><td>Serial Number</td><td style="word-break: break-all"><%=certificate.getSerialNumber().toString(16).toUpperCase()%></td></tr>
    <tr><td>Valid</td><td>Start <%=SimpleDateFormat.getDateTimeInstance().format(certificate.getNotBefore())%>, Expire <%=SimpleDateFormat.getDateTimeInstance().format(certificate.getNotAfter())%></td></tr>
    <tr><td colspan="2" class="key" style="text-align: center; font-size: smaller">
        <a href="#" onclick="showCert_<%=md5sum%>()">details</a>
    </td></tr>
</table>
<script type="text/javascript">
    function showCert_<%=md5sum%>() {
        var body = '<pre>' + '<%=StringEscapeUtils.escapeJavaScript(certificate.toString())%>' + '</pre>';
        require(["dijit/Dialog"], function(Dialog){
            new Dialog({
                title: "Certificate Detail",
                content: body
            }).show();
        });
    };
</script>
