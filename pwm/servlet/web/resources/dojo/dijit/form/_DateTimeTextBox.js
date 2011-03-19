/*
	Copyright (c) 2004-2011, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.form._DateTimeTextBox"]){
dojo._hasResource["dijit.form._DateTimeTextBox"]=true;
dojo.provide("dijit.form._DateTimeTextBox");
dojo.require("dojo.date");
dojo.require("dojo.date.locale");
dojo.require("dojo.date.stamp");
dojo.require("dijit.form.ValidationTextBox");
dojo.require("dijit._HasDropDown");
new Date("X");
dojo.declare("dijit.form._DateTimeTextBox",[dijit.form.RangeBoundTextBox,dijit._HasDropDown],{templateString:dojo.cache("dijit.form","templates/DropDownBox.html","<div class=\"dijit dijitReset dijitInlineTable dijitLeft\"\n\tid=\"widget_${id}\"\n\trole=\"combobox\"\n\t><div class='dijitReset dijitRight dijitButtonNode dijitArrowButton dijitDownArrowButton dijitArrowButtonContainer'\n\t\tdojoAttachPoint=\"_buttonNode, _popupStateNode\" role=\"presentation\"\n\t\t><input class=\"dijitReset dijitInputField dijitArrowButtonInner\" value=\"&#9660; \" type=\"text\" tabIndex=\"-1\" readonly=\"readonly\" role=\"presentation\"\n\t\t\t${_buttonInputDisabled}\n\t/></div\n\t><div class='dijitReset dijitValidationContainer'\n\t\t><input class=\"dijitReset dijitInputField dijitValidationIcon dijitValidationInner\" value=\"&#935;\" type=\"text\" tabIndex=\"-1\" readonly=\"readonly\" role=\"presentation\"\n\t/></div\n\t><div class=\"dijitReset dijitInputField dijitInputContainer\"\n\t\t><input class='dijitReset dijitInputInner' ${!nameAttrSetting} type=\"text\" autocomplete=\"off\"\n\t\t\tdojoAttachPoint=\"textbox,focusNode\" role=\"textbox\" aria-haspopup=\"true\"\n\t/></div\n></div>\n"),hasDownArrow:true,openOnClick:true,regExpGen:dojo.date.locale.regexp,datePackage:"dojo.date",compare:function(_1,_2){
return dojo.date.compare(_1,_2,this._selector);
},forceWidth:true,format:function(_3,_4){
if(!_3){
return "";
}
return this.dateLocaleModule.format(_3,_4);
},"parse":function(_5,_6){
return this.dateLocaleModule.parse(_5,_6)||(this._isEmpty(_5)?null:undefined);
},serialize:function(_7,_8){
if(_7.toGregorian){
_7=_7.toGregorian();
}
return dojo.date.stamp.toISOString(_7,_8);
},dropDownDefaultValue:new Date(),value:new Date(""),_blankValue:null,popupClass:"",_selector:"",constructor:function(_9){
var _a=_9.datePackage?_9.datePackage+".Date":"Date";
this.dateClassObj=dojo.getObject(_a,false);
this.value=new this.dateClassObj("");
this.datePackage=_9.datePackage||this.datePackage;
this.dateLocaleModule=dojo.getObject(this.datePackage+".locale",false);
this.regExpGen=this.dateLocaleModule.regexp;
this._invalidDate=dijit.form._DateTimeTextBox.prototype.value.toString();
},buildRendering:function(){
this.inherited(arguments);
if(!this.hasDownArrow){
this._buttonNode.style.display="none";
}
if(this.openOnClick||!this.hasDownArrow){
this._buttonNode=this.domNode;
this.baseClass+=" dijitComboBoxOpenOnClick";
}
},_setConstraintsAttr:function(_b){
_b.selector=this._selector;
_b.fullYear=true;
var _c=dojo.date.stamp.fromISOString;
if(typeof _b.min=="string"){
_b.min=_c(_b.min);
}
if(typeof _b.max=="string"){
_b.max=_c(_b.max);
}
this.inherited(arguments,[_b]);
},_isInvalidDate:function(_d){
return !_d||isNaN(_d)||typeof _d!="object"||_d.toString()==this._invalidDate;
},_setValueAttr:function(_e,_f,_10){
if(_e!==undefined){
if(typeof _e=="string"){
_e=dojo.date.stamp.fromISOString(_e);
}
if(this._isInvalidDate(_e)){
_e=null;
}
if(_e instanceof Date&&!(this.dateClassObj instanceof Date)){
_e=new this.dateClassObj(_e);
}
}
this.inherited(arguments,[_e,_f,_10]);
if(this.dropDown){
this.dropDown.set("value",_e,false);
}
},_set:function(_11,_12){
if(_11=="value"&&this.value instanceof Date&&((this._isInvalidDate(this.value)&&this._isInvalidDate(_12))||this.compare(_12,this.value)==0)){
return;
}
this.inherited(arguments);
},_setDropDownDefaultValueAttr:function(val){
if(this._isInvalidDate(val)){
val=new this.dateClassObj();
}
this.dropDownDefaultValue=val;
},openDropDown:function(_13){
if(this.dropDown){
this.dropDown.destroy();
}
var _14=dojo.getObject(this.popupClass,false),_15=this,_16=this.get("value");
this.dropDown=new _14({onChange:function(_17){
dijit.form._DateTimeTextBox.superclass._setValueAttr.call(_15,_17,true);
},id:this.id+"_popup",dir:_15.dir,lang:_15.lang,value:_16,currentFocus:!this._isInvalidDate(_16)?_16:this.dropDownDefaultValue,constraints:_15.constraints,filterString:_15.filterString,datePackage:_15.datePackage,isDisabledDate:function(_18){
return !_15.rangeCheck(_18,_15.constraints);
}});
this.inherited(arguments);
},_getDisplayedValueAttr:function(){
return this.textbox.value;
},_setDisplayedValueAttr:function(_19,_1a){
this._setValueAttr(this.parse(_19,this.constraints),_1a,_19);
}});
}
