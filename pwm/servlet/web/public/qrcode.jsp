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
<%@page import="java.net.URLDecoder"%>
<%@page import="java.io.ByteArrayOutputStream"%>
<%@page import="net.glxn.qrgen.image.ImageType"%>
<%@page import="net.glxn.qrgen.QRCode"%>
<%
String content = request.getParameter("content");
String widthStr = request.getParameter("width");
String heightStr = request.getParameter("height");

if (content == null || content.length() == 0) {
  throw(new Exception("No content string"));
}

Integer height = 200;
Integer width = 200;
if (widthStr != null) try {
    Integer _width = Integer.parseInt(widthStr);
    width = _width;
} catch (NumberFormatException e) {
}
if (heightStr != null) try {
    Integer _height = Integer.parseInt(heightStr);
    height = _height;
} catch (NumberFormatException e) {
}

QRCode code = QRCode.from(URLDecoder.decode(content, "UTF-8")).withCharset("UTF-8").withSize(width, height);
ByteArrayOutputStream stream = code.to(ImageType.PNG).stream();
response.setContentType("image/png");
response.getOutputStream().write(stream.toByteArray());

%>
