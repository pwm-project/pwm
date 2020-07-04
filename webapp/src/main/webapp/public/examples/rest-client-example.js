/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

let lastState;

function initPage() {
    const currentUrl = window.location.origin;
    let path = window.location.pathname;
    if (path) {
        path = path.split("/")[1]
    }
    document.getElementById("base-url").value = currentUrl + '/' + path;
}

function handleSendForm(event) {
    event.preventDefault();
    resetDebugForm();

    const sendUrl = document.getElementById("base-url").value + valueOfSelectedOption("module-url");
    writeDebugData("Send URL", sendUrl);

    const sendData = {};
    if ( lastState ) {
        sendData.state = lastState;
    }
    sendData.form = readFormData();

    const postBody = JSON.stringify(sendData);
    resetDataForm();
    document.getElementById('button-send').disabled = true;
    const xmlHttpReq = new XMLHttpRequest();
    xmlHttpReq.open("POST", sendUrl);
    xmlHttpReq.setRequestHeader("Content-Type", "application/json;charset=UTF-8");
    xmlHttpReq.send(postBody);
    writeDebugData("Sent JSON",postBody);
    writeDebugData("curl command", makeCurlString(sendUrl,postBody));
    xmlHttpReq.onreadystatechange = function() {
        if (this.readyState === 4) {
            if (this.status === 200) {
                writeDebugData("Received JSON", this.responseText);
                const responseData = JSON.parse(this.responseText);
                processResponse(responseData);
            } else if (this.readyState === 4) {
                alert('send failed: ' + this.status + this.statusText);
            }
            document.getElementById('button-send').disabled = false;
        }
    };
}

function handleResetForm()
{
    lastState = null;
    resetDataForm();
    resetDebugForm();
}

function resetDataForm()
{
    document.getElementById("table-data-form").innerHTML = '';
}

function resetDebugForm()
{
    document.getElementById('table-debug-data').innerHTML = '';
}

function readFormData()
{
    const formData = {};
    const forms = document.getElementById("data-form");
    for(const field of forms.elements) {
        if (field.tagName === 'INPUT') {
            const fieldName = field.name;
            formData[fieldName] = field.value;
        } else if (field.tagName === 'SELECT') {
            const fieldName = field.name;
            formData[fieldName] = field.options[field.selectedIndex].value;
        }
    }
    return formData;
}
function processResponse(responseData)
{
    document.getElementById("error-display").innerText = (responseData.error === true)
        ? 'Error: ' + responseData.errorMessage + (responseData.errorDetail !== undefined ? ' ' + responseData.errorDetail : '')
        : '';

    if ( responseData.data ) {
        if (responseData.data.stage) {
            writeDebugData("Received Stage",responseData.data.stage);
        }
        if (responseData.data.method) {
            writeDebugData("Received Method",responseData.data.method);
        }

        if (responseData.data.state) {
            writeDebugData("Received State",responseData.data.state);
            lastState = responseData.data.state;
        }

        if (responseData.data.form) {
            resetDataForm();

        }
        const formData = responseData.data.form;
        if (formData) {
            createDataForm(formData);
        }
    }
}

function createDataForm(formData) {

    if (formData.label) {
        const element = document.createElement('span');
        element.innerText = formData.label;
        createRow('table-data-form', "Label", element);
    }

    if (formData.message) {
        const element = document.createElement('span');
        element.innerText = formData.message;
        createRow('table-data-form', "Message", element);
    }

    if (formData.formRows) {
        createDataFormRows(formData.formRows);
    }
}

function createDataFormRows(formRows) {
    for (const key of Object.keys(formRows)) {
        const formRow = formRows[key];

        let formElement;
        if (formRow.type === 'select') {
            formElement = document.createElement("select");
            for (const key of Object.keys(formRow.selectOptions)) {
                const value = formRow.selectOptions[key];
                const optionElement = document.createElement("option");
                optionElement.value = key;
                optionElement.innerText = value;
                formElement.appendChild(optionElement);
            }
        } else {
            formElement = document.createElement("input");
            formElement.type = formRow.type;
        }
        formElement.name = formRow.name;
        formElement.id = formRow.name;

        createRow('table-data-form',formRow.label,formElement);
    }
}

function createRow(tableID,label,valueElement) {
    const tableElement = document.getElementById(tableID);
    const tableRow = tableElement.insertRow(-1);
    const labelRow = tableRow.insertCell(0);
    labelRow.innerText = label;
    const dataRow = tableRow.insertCell(-1);
    dataRow.appendChild(valueElement);
}

function writeDebugData(key,value)
{
    const tableElement = document.getElementById('table-debug-data');
    const tableRow = tableElement.insertRow(-1);
    const labelCell = tableRow.insertCell(-1);
    labelCell.innerText = key;
    const valueCell = tableRow.insertCell(-1);
    valueCell.innerText = value;
}

function valueOfSelectedOption(elementID)
{
    var e = document.getElementById(elementID);
    return  e.options[e.selectedIndex].value;
}

function makeCurlString(url,jsonBody) {
    return "curl -kv -H 'Content-Type: application/json' -d '" + jsonBody + "' '" + url + "'";
}

initPage();
document.getElementById("button-send").addEventListener("click",handleSendForm);
document.getElementById("button-reset").addEventListener("click",handleResetForm);

