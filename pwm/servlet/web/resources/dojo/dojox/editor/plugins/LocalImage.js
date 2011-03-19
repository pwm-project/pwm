/*
	Copyright (c) 2004-2011, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.editor.plugins.LocalImage"]){
dojo._hasResource["dojox.editor.plugins.LocalImage"]=true;
dojo.provide("dojox.editor.plugins.LocalImage");
dojo.require("dijit._editor.plugins.LinkDialog");
dojo.require("dojox.form.FileUploader");
dojo.require("dojo.i18n");
dojo.requireLocalization("dojox.editor.plugins","LocalImage",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,kk,ko,nb,nl,pl,pt,pt-pt,ru,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dojox.editor.plugins.LocalImage",dijit._editor.plugins.ImgLinkDialog,{uploadable:false,uploadUrl:"",baseImageUrl:"",fileMask:"*.jpg;*.jpeg;*.gif;*.png;*.bmp",urlRegExp:"",_fileUploader:null,htmlFieldName:"uploadedfile",_isLocalFile:false,_messages:"",_cssPrefix:"dijitEditorEilDialog",_closable:true,linkDialogTemplate:["<div style='border-bottom: 1px solid black; padding-bottom: 2pt; margin-bottom: 4pt;'></div>","<div class='dijitEditorEilDialogDescription'>${prePopuTextUrl}${prePopuTextBrowse}</div>","<table><tr><td colspan='2'>","<label for='${id}_urlInput' title='${prePopuTextUrl}${prePopuTextBrowse}'>${url}</label>","</td></tr><tr><td class='dijitEditorEilDialogField'>","<input dojoType='dijit.form.ValidationTextBox' class='dijitEditorEilDialogField'"+"regExp='${urlRegExp}' title='${prePopuTextUrl}${prePopuTextBrowse}'  selectOnClick='true' required='true' "+"id='${id}_urlInput' name='urlInput' intermediateChanges='true' invalidMessage='${invalidMessage}' "+"prePopuText='&lt;${prePopuTextUrl}${prePopuTextBrowse}&gt'>","</td><td>","<div id='${id}_browse' style='display:${uploadable}'>${browse}</div>","</td></tr><tr><td colspan='2'>","<label for='${id}_textInput'>${text}</label>","</td></tr><tr><td>","<input dojoType='dijit.form.TextBox' required='false' id='${id}_textInput' "+"name='textInput' intermediateChanges='true' selectOnClick='true' class='dijitEditorEilDialogField'>","</td><td></td></tr><tr><td>","</td><td>","</td></tr><tr><td colspan='2'>","<button dojoType='dijit.form.Button' id='${id}_setButton'>${set}</button>","</td></tr></table>"].join(""),_initButton:function(){
var _1=this,_2=this._messages=dojo.i18n.getLocalization("dojox.editor.plugins","LocalImage");
this.tag="img";
var _3=(this.dropDown=new dijit.TooltipDialog({title:_2[this.command+"Title"],onOpen:function(){
if(!dojo.IE&&!_1.blurHandler){
_1.blurHandler=dojo.connect(dojo.global,"blur",function(_4){
dojo.stopEvent(_4);
_1._urlInput.isReadyToValidate=true;
_1._urlInput.focus();
});
}
_1._initialFileUploader();
_1._onOpenDialog();
dijit.TooltipDialog.prototype.onOpen.apply(this,arguments);
setTimeout(function(){
dijit.selectInputText(_1._urlInput.textbox);
_1._urlInput.isLoadComplete=true;
},0);
},onClose:function(){
dojo.disconnect(_1.blurHandler);
_1.blurHandler=null;
this.onHide();
},onCancel:function(){
setTimeout(dojo.hitch(_1,"_onCloseDialog"),0);
}}));
var _5=this.getLabel(this.command),_6=this.iconClassPrefix+" "+this.iconClassPrefix+this.command.charAt(0).toUpperCase()+this.command.substr(1),_7=dojo.mixin({label:_5,showLabel:false,iconClass:_6,dropDown:this.dropDown,tabIndex:"-1"},this.params||{});
if(dojo.isSafari==5){
_7.closeDropDown=function(_8){
if(_1._closable){
if(this._opened){
dijit.popup.close(this.dropDown);
if(_8){
this.focus();
}
this._opened=false;
this.state="";
}
}
setTimeout(function(){
_1._closable=true;
},10);
};
}
this.button=new dijit.form.DropDownButton(_7);
var _9=this.fileMask.split(";"),_a="";
dojo.forEach(_9,function(m){
m=m.replace(/\./,"\\.").replace(/\*/g,".*");
_a+="|"+m+"|"+m.toUpperCase();
});
_2.urlRegExp=this.urlRegExp=_a.substring(1);
if(!this.uploadable){
_2["prePopuTextBrowse"]=".";
}
_2.id=dijit.getUniqueId(this.editor.id);
_2.uploadable=this.uploadable?"inline":"none";
this._uniqueId=_2.id;
this._setContent("<div class='"+this._cssPrefix+"Title'>"+_3.title+"</div>"+dojo.string.substitute(this.linkDialogTemplate,_2));
_3.startup();
var _b=this._urlInput=dijit.byId(this._uniqueId+"_urlInput");
this._textInput=dijit.byId(this._uniqueId+"_textInput");
this._setButton=dijit.byId(this._uniqueId+"_setButton");
if(_b){
var pt=dijit.form.ValidationTextBox.prototype;
_b=dojo.mixin(_b,{isLoadComplete:false,isValid:function(_c){
if(this.isLoadComplete){
return pt.isValid.apply(this,arguments);
}else{
return this.get("value").length>0;
}
},reset:function(){
this.isLoadComplete=false;
pt.reset.apply(this,arguments);
}});
this.connect(_b,"onKeyDown","_cancelFileUpload");
this.connect(_b,"onChange","_checkAndFixInput");
}
if(this._setButton){
this.connect(this._setButton,"onClick","_checkAndSetValue");
}
this._connectTagEvents();
},_initialFileUploader:function(){
var _d=null,_e=this,_f=_e._uniqueId,_10=_f+"_browse",_11=_e._urlInput;
if(_e.uploadable&&!_e._fileUploader){
_d=_e._fileUploader=new dojox.form.FileUploader({force:"html",uploadUrl:_e.uploadUrl,htmlFieldName:_e.htmlFieldName,uploadOnChange:false,selectMultipleFiles:false,showProgress:true},_10);
_d.reset=function(){
_e._isLocalFile=false;
_d._resetHTML();
};
_e.connect(_d,"onClick",function(){
_11.isReadyToValidate=false;
_11.validate(false);
if(dojo.isSafari==5){
_e._closable=false;
}
});
_e.connect(_d,"onChange",function(_12){
_e._isLocalFile=true;
_11.set("value",_12[0].name);
});
_e.connect(_d,"onComplete",function(_13){
var _14=_e.baseImageUrl;
_14=_14&&_14.charAt(_14.length-1)=="/"?_14:_14+"/";
_11.set("value",_14+_13[0].file);
_e._isLocalFile=false;
_e._setDialogStatus(true);
_e.setValue(_e.dropDown.get("value"));
});
_e.connect(_d,"onError",function(_15){
_e._setDialogStatus(true);
});
}
},_checkAndFixInput:function(){
this._setButton.set("disabled",!this._isValid());
},_isValid:function(){
return this._urlInput.isValid();
},_cancelFileUpload:function(){
this._fileUploader.reset();
this._isLocalFile=false;
},_checkAndSetValue:function(){
if(this._fileUploader&&this._isLocalFile){
this._setDialogStatus(false);
this._fileUploader.upload();
}else{
this.setValue(this.dropDown.get("value"));
}
},_setDialogStatus:function(_16){
this._urlInput.set("disabled",!_16);
this._textInput.set("disabled",!_16);
this._setButton.set("disabled",!_16);
},destroy:function(){
this.inherited(arguments);
if(this._fileUploader){
this._fileUploader.destroy();
this._fileUploader=null;
}
}});
dojo.subscribe(dijit._scopeName+".Editor.getPlugin",null,function(o){
if(o.plugin){
return;
}
var _17=o.args.name.toLowerCase();
if(_17==="localimage"){
o.plugin=new dojox.editor.plugins.LocalImage({command:"insertImage",uploadable:("uploadable" in o.args)?o.args.uploadable:false,uploadUrl:("uploadable" in o.args&&"uploadUrl" in o.args)?o.args.uploadUrl:"",htmlFieldName:("uploadable" in o.args&&"htmlFieldName" in o.args)?o.args.htmlFieldName:"uploadedfile",baseImageUrl:("uploadable" in o.args&&"baseImageUrl" in o.args)?o.args.baseImageUrl:"",fileMask:("fileMask" in o.args)?o.args.fileMask:"*.jpg;*.jpeg;*.gif;*.png;*.bmp"});
}
});
}
