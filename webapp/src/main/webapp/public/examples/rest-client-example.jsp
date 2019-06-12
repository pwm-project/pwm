<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<html>
<head>
</head>
<body>
<div>
    <form name="url-form">
        <input type="text" name="base-url" id="base-url"/>
        <select name="module-url" id="module-url">
            <option value="/public/rest/forgottenpassword">/public/rest/forgottenpassword</option>
        </select>
    </form>
</div>
<br/>
<div>
    <span id="error-display"></span>
</div>
<br/>
<div>
    <span>Interaction Form</span>
    <form name="data-form" id="data-form">
        <table id="table-data-form" border="1">
        </table>
        <button type="submit" name="button-send" id="button-send">Send Data</button>
        <button type="button" name="button-reset" id="button-reset">Reset</button>
    </form>
</div>
<br/>
<div>
    <span>Debug Data</span>
    <table id="table-debug-data" border="1">
    </table>
</div>
<br/>

<script type="text/javascript" src="rest-client-example.js"></script>
</body>
</html>
