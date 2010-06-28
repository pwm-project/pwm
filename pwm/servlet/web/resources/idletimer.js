/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

var SETTING_LOOP_FREQUENCY = 1000;
var SETTING_PING_FREQUENCY = 10;

var dateFuture = new Date();
var idleTimeout = 0;
var sendPing = false;
var lastPingTime = 0;

function initCountDownTimer(secondsRemaining)
{
    idleTimeout = secondsRemaining;
    dateFuture = new Date(new Date().getTime() + (secondsRemaining * 1000));
    lastPingTime = new Date().getTime();
    resetIdleCounter();
    setInterval("pollActivity()",SETTING_LOOP_FREQUENCY); //poll scrolling
    document.onclick=resetIdleCounter;
    document.onkeydown=resetIdleCounter;
}

function resetIdleCounter(){
    var idleSeconds = calcIdleSeconds();
    getObject("idle_status").firstChild.nodeValue = makeIdleDisplayString(idleSeconds);

    dateFuture = new Date(new Date().getTime() + (idleTimeout * 1000));
    {
        var dateNow = new Date().getTime();
        var amount = dateNow - lastPingTime;

        if (amount > SETTING_PING_FREQUENCY * 1000) { //calc milliseconds between dates
            pingServer();
            lastPingTime = dateNow;
            sendPing = false;
        }
    }
}

function pollActivity(){
    var idleSeconds = calcIdleSeconds();
    getObject("idle_status").firstChild.nodeValue = makeIdleDisplayString(idleSeconds);
    if (idleSeconds < 0) {
        dirtyPageLeaveFlag = false;
        window.location = getObject("Js_LogoutURL").value;
    }
}

function pingServer() {
    var pingURL = getObject("Js_CommandURL").value + "?processAction=idleUpdate&time=" + new Date().getTime();
    var xmlhttp = createXmlHttpObject();
    xmlhttp.abort();
    xmlhttp.open("POST", pingURL, true);
    xmlhttp.send(null);
}

function calcIdleSeconds()
{
    var amount = dateFuture.getTime() - (new Date()).getTime(); //calc milliseconds between dates
    amount = Math.floor(amount / 1000); //kill the "milliseconds" so just secs
    return amount;
}

function makeIdleDisplayString(amount)
{
    var output = "";
    var days = 0, hours = 0, mins = 0, secs = 0;

    days = Math.floor(amount / 86400);

    amount = amount % 86400;
    hours = Math.floor(amount / 3600);

    amount = amount % 3600;
    mins = Math.floor(amount / 60);

    amount = amount % 60;
    secs = Math.floor(amount);

    // write number of days
    if (days != 0) {
        output += days + " ";
        if (days != 1) {
            output += getObject("Js_Display_Days").value;
        } else {
            output += getObject("Js_Display_Day").value;
        }
    }

    // write number of hours
    if (days != 0 || hours != 0) {
        if (output.length > 0) {
            output += ", ";
        }

        output += hours + " ";
        if (hours != 1) {
            output += getObject("Js_Display_Hours").value;
        } else {
            output += getObject("Js_Display_Hour").value;
        }
    }

    // write number of minutes
    if (days != 0 || hours != 0 || mins != 0) {
        if (output.length > 0) {
            output += ", ";
        }

        output += mins + " ";
        if (mins != 1) {
            output += getObject("Js_Display_Minutes").value;
        } else {
            output += getObject("Js_Display_Minute").value;
        }
    }

    // write number of seconds
    if (mins < 5) {
        if (output.length > 0) {
            output += ", ";
        }

        output += secs + " ";

        if (secs != 1) {
            output += getObject("Js_Display_Seconds").value;
        } else {
            output += getObject("Js_Display_Second").value;
        }
    }

    output = getObject("Js_Display_IdleTimeout").value + " " + output;
    return output;
}

