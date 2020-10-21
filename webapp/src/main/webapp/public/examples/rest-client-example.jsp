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
