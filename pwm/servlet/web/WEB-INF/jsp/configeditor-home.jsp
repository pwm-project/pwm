<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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

<div>
    <table style="margin-left: auto; margin-right: auto; max-width: 450px" class="noborder">
        <tr>
            <td>
                <input type="search" id="homeSettingSearch" name="homeSettingSearch" class="inputfield" oninput="theThing()" style="width: 400px" autofocus/>
            </td>
            <td>
                <div id="searchIndicator" style="visibility: hidden">
                    <span style="" class="fa fa-lg fa-spin fa-spinner"></span>
                </div>
            </td>
            <td>
                <div id="maxResultsIndicator" style="visibility: hidden;">
                    <span style="color: #ffcd59;" class="fa fa-lg fa-exclamation-circle"></span>
                </div>
            </td>
        </tr>
        <tr>
            <td>
                <div id="searchResults" style="border: 1px solid; height: 200px; overflow-y: auto"></div>
            </td>

        </tr>
    </table>
    <script type="text/javascript">
        function theThing() {
            PWM_CFGEDIT.processSettingSearch(
                    PWM_MAIN.getObject('homeSettingSearch').value,
                    PWM_MAIN.getObject('searchResults')
            );
        }
    </script>
</div>