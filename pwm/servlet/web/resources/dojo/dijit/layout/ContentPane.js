/*
	Copyright (c) 2004-2011, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit.layout.ContentPane"]){
dojo._hasResource["dijit.layout.ContentPane"]=true;
dojo.provide("dijit.layout.ContentPane");
dojo.require("dijit._Widget");
dojo.require("dijit.layout._LayoutWidget");
dojo.require("dijit.layout._ContentPaneResizeMixin");
dojo.require("dojo.string");
dojo.require("dojo.html");
dojo.requireLocalization("dijit","loading",null,"ROOT,ar,ca,cs,da,de,el,es,fi,fr,he,hu,it,ja,kk,ko,nb,nl,pl,pt,pt-pt,ro,ru,sk,sl,sv,th,tr,zh,zh-tw");
dojo.declare("dijit.layout.ContentPane",[dijit._Widget,dijit.layout._ContentPaneResizeMixin],{href:"",extractContent:false,parseOnLoad:true,parserScope:dojo._scopeName,preventCache:false,preload:false,refreshOnShow:false,loadingMessage:"<span class='dijitContentPaneLoading'>${loadingState}</span>",errorMessage:"<span class='dijitContentPaneError'>${errorState}</span>",isLoaded:false,baseClass:"dijitContentPane",ioArgs:{},onLoadDeferred:null,attributeMap:dojo.delegate(dijit._Widget.prototype.attributeMap,{title:[]}),stopParser:true,template:false,create:function(_1,_2){
if((!_1||!_1.template)&&_2&&!("href" in _1)&&!("content" in _1)){
var df=dojo.doc.createDocumentFragment();
_2=dojo.byId(_2);
while(_2.firstChild){
df.appendChild(_2.firstChild);
}
_1=dojo.delegate(_1,{content:df});
}
this.inherited(arguments,[_1,_2]);
},postMixInProperties:function(){
this.inherited(arguments);
var _3=dojo.i18n.getLocalization("dijit","loading",this.lang);
this.loadingMessage=dojo.string.substitute(this.loadingMessage,_3);
this.errorMessage=dojo.string.substitute(this.errorMessage,_3);
},buildRendering:function(){
this.inherited(arguments);
if(!this.containerNode){
this.containerNode=this.domNode;
}
this.domNode.title="";
if(!dojo.attr(this.domNode,"role")){
dijit.setWaiRole(this.domNode,"group");
}
},_startChildren:function(){
this.inherited(arguments);
if(this._contentSetter){
dojo.forEach(this._contentSetter.parseResults,function(_4){
if(!_4._started&&!_4._destroyed&&dojo.isFunction(_4.startup)){
_4.startup();
_4._started=true;
}
},this);
}
},startup:function(){
if(this._started){
return;
}
this.inherited(arguments);
if(this._isShown()){
this._onShow();
}
},setHref:function(_5){
dojo.deprecated("dijit.layout.ContentPane.setHref() is deprecated. Use set('href', ...) instead.","","2.0");
return this.set("href",_5);
},_setHrefAttr:function(_6){
this.cancel();
this.onLoadDeferred=new dojo.Deferred(dojo.hitch(this,"cancel"));
this.onLoadDeferred.addCallback(dojo.hitch(this,"onLoad"));
this._set("href",_6);
if(this.preload||(this._created&&this._isShown())){
this._load();
}else{
this._hrefChanged=true;
}
return this.onLoadDeferred;
},setContent:function(_7){
dojo.deprecated("dijit.layout.ContentPane.setContent() is deprecated.  Use set('content', ...) instead.","","2.0");
this.set("content",_7);
},_setContentAttr:function(_8){
this._set("href","");
this.cancel();
this.onLoadDeferred=new dojo.Deferred(dojo.hitch(this,"cancel"));
if(this._created){
this.onLoadDeferred.addCallback(dojo.hitch(this,"onLoad"));
}
this._setContent(_8||"");
this._isDownloaded=false;
return this.onLoadDeferred;
},_getContentAttr:function(){
return this.containerNode.innerHTML;
},cancel:function(){
if(this._xhrDfd&&(this._xhrDfd.fired==-1)){
this._xhrDfd.cancel();
}
delete this._xhrDfd;
this.onLoadDeferred=null;
},uninitialize:function(){
if(this._beingDestroyed){
this.cancel();
}
this.inherited(arguments);
},destroyRecursive:function(_9){
if(this._beingDestroyed){
return;
}
this.inherited(arguments);
},resize:function(_a,_b){
if(!this._wasShown&&this.open!==false){
this._onShow();
}
this._resizeCalled=true;
this._scheduleLayout(_a,_b);
},_isShown:function(){
if(this._childOfLayoutWidget){
if(this._resizeCalled&&"open" in this){
return this.open;
}
return this._resizeCalled;
}else{
if("open" in this){
return this.open;
}else{
var _c=this.domNode,_d=this.domNode.parentNode;
return (_c.style.display!="none")&&(_c.style.visibility!="hidden")&&!dojo.hasClass(_c,"dijitHidden")&&_d&&_d.style&&(_d.style.display!="none");
}
}
},_onShow:function(){
if(this.href){
if(!this._xhrDfd&&(!this.isLoaded||this._hrefChanged||this.refreshOnShow)){
var d=this.refresh();
}
}else{
if(this._needLayout){
this._layout(this._changeSize,this._resultSize);
}
}
this.inherited(arguments);
this._wasShown=true;
return d;
},refresh:function(){
this.cancel();
this.onLoadDeferred=new dojo.Deferred(dojo.hitch(this,"cancel"));
this.onLoadDeferred.addCallback(dojo.hitch(this,"onLoad"));
this._load();
return this.onLoadDeferred;
},_load:function(){
this._setContent(this.onDownloadStart(),true);
var _e=this;
var _f={preventCache:(this.preventCache||this.refreshOnShow),url:this.href,handleAs:"text"};
if(dojo.isObject(this.ioArgs)){
dojo.mixin(_f,this.ioArgs);
}
var _10=(this._xhrDfd=(this.ioMethod||dojo.xhrGet)(_f));
_10.addCallback(function(_11){
try{
_e._isDownloaded=true;
_e._setContent(_11,false);
_e.onDownloadEnd();
}
catch(err){
_e._onError("Content",err);
}
delete _e._xhrDfd;
return _11;
});
_10.addErrback(function(err){
if(!_10.canceled){
_e._onError("Download",err);
}
delete _e._xhrDfd;
return err;
});
delete this._hrefChanged;
},_onLoadHandler:function(_12){
this._set("isLoaded",true);
try{
this.onLoadDeferred.callback(_12);
}
catch(e){
console.error("Error "+this.widgetId+" running custom onLoad code: "+e.message);
}
},_onUnloadHandler:function(){
this._set("isLoaded",false);
try{
this.onUnload();
}
catch(e){
console.error("Error "+this.widgetId+" running custom onUnload code: "+e.message);
}
},destroyDescendants:function(){
if(this.isLoaded){
this._onUnloadHandler();
}
var _13=this._contentSetter;
dojo.forEach(this.getChildren(),function(_14){
if(_14.destroyRecursive){
_14.destroyRecursive();
}
});
if(_13){
dojo.forEach(_13.parseResults,function(_15){
if(_15.destroyRecursive&&_15.domNode&&_15.domNode.parentNode==dojo.body()){
_15.destroyRecursive();
}
});
delete _13.parseResults;
}
dojo.html._emptyNode(this.containerNode);
delete this._singleChild;
},_setContent:function(_16,_17){
this.destroyDescendants();
var _18=this._contentSetter;
if(!(_18&&_18 instanceof dojo.html._ContentSetter)){
_18=this._contentSetter=new dojo.html._ContentSetter({node:this.containerNode,_onError:dojo.hitch(this,this._onError),onContentError:dojo.hitch(this,function(e){
var _19=this.onContentError(e);
try{
this.containerNode.innerHTML=_19;
}
catch(e){
console.error("Fatal "+this.id+" could not change content due to "+e.message,e);
}
})});
}
var _1a=dojo.mixin({cleanContent:this.cleanContent,extractContent:this.extractContent,parseContent:this.parseOnLoad,parserScope:this.parserScope,startup:false,dir:this.dir,lang:this.lang},this._contentSetterParams||{});
_18.set((dojo.isObject(_16)&&_16.domNode)?_16.domNode:_16,_1a);
delete this._contentSetterParams;
if(this.doLayout){
this._checkIfSingleChild();
}
if(!_17){
if(this._started){
this._startChildren();
this._scheduleLayout();
}
this._onLoadHandler(_16);
}
},_onError:function(_1b,err,_1c){
this.onLoadDeferred.errback(err);
var _1d=this["on"+_1b+"Error"].call(this,err);
if(_1c){
console.error(_1c,err);
}else{
if(_1d){
this._setContent(_1d,true);
}
}
},_scheduleLayout:function(_1e,_1f){
if(this._isShown()){
this._layout(_1e,_1f);
}else{
this._needLayout=true;
this._changeSize=_1e;
this._resultSize=_1f;
}
},onLoad:function(_20){
},onUnload:function(){
},onDownloadStart:function(){
return this.loadingMessage;
},onContentError:function(_21){
},onDownloadError:function(_22){
return this.errorMessage;
},onDownloadEnd:function(){
}});
}
