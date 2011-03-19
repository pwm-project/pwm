/*
	Copyright (c) 2004-2011, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.mobile._base"]){
dojo._hasResource["dojox.mobile._base"]=true;
dojo.provide("dojox.mobile._base");
dojo.require("dijit._WidgetBase");
dojo.isBB=(navigator.userAgent.indexOf("BlackBerry")!=-1)&&!dojo.isWebKit;
dojo.declare("dojox.mobile.View",dijit._WidgetBase,{selected:false,keepScrollPos:true,_started:false,constructor:function(_1,_2){
if(_2){
dojo.byId(_2).style.visibility="hidden";
}
},buildRendering:function(){
this.domNode=this.containerNode=this.srcNodeRef||dojo.doc.createElement("DIV");
this.domNode.className="mblView";
this.connect(this.domNode,"webkitAnimationEnd","onAnimationEnd");
this.connect(this.domNode,"webkitAnimationStart","onAnimationStart");
var id=location.href.match(/#(\w+)([^\w=]|$)/)?RegExp.$1:null;
this._visible=this.selected&&!id||this.id==id;
if(this.selected){
dojox.mobile._defaultView=this;
}
},startup:function(){
if(this._started){
return;
}
var _3=this;
setTimeout(function(){
if(!_3._visible){
_3.domNode.style.display="none";
}else{
dojox.mobile.currentView=_3;
_3.onStartView();
}
_3.domNode.style.visibility="visible";
},dojo.isIE?100:0);
this._started=true;
},onStartView:function(){
},onBeforeTransitionIn:function(_4,_5,_6,_7,_8){
},onAfterTransitionIn:function(_9,_a,_b,_c,_d){
},onBeforeTransitionOut:function(_e,_f,_10,_11,_12){
},onAfterTransitionOut:function(_13,dir,_14,_15,_16){
},_saveState:function(_17,dir,_18,_19,_1a){
this._context=_19;
this._method=_1a;
if(_18=="none"||!dojo.isWebKit){
_18=null;
}
this._moveTo=_17;
this._dir=dir;
this._transition=_18;
this._arguments=[];
var i;
for(i=0;i<arguments.length;i++){
this._arguments.push(arguments[i]);
}
this._args=[];
if(_19||_1a){
for(i=5;i<arguments.length;i++){
this._args.push(arguments[i]);
}
}
},performTransition:function(_1b,dir,_1c,_1d,_1e){
if(dojo.hash){
if(typeof (_1b)=="string"&&_1b.charAt(0)=="#"&&!dojox.mobile._params){
dojox.mobile._params=[];
for(var i=0;i<arguments.length;i++){
dojox.mobile._params.push(arguments[i]);
}
dojo.hash(_1b);
return;
}
}
this._saveState.apply(this,arguments);
var _1f;
if(_1b){
if(typeof (_1b)=="string"){
_1b.match(/^#?([^&]+)/);
_1f=RegExp.$1;
}else{
_1f=_1b;
}
}else{
if(!this._dummyNode){
this._dummyNode=dojo.doc.createElement("DIV");
dojo.body().appendChild(this._dummyNode);
}
_1f=this._dummyNode;
}
var _20=this.domNode;
_1f=this.toNode=dojo.byId(_1f);
if(!_1f){
alert("dojox.mobile.View#performTransition: destination view not found: "+_1f);
}
_1f.style.visibility="hidden";
_1f.style.display="";
this.onBeforeTransitionOut.apply(this,arguments);
var _21=dijit.byNode(_1f);
if(_21){
if(this.keepScrollPos&&!dijit.getEnclosingWidget(this.domNode.parentNode)){
var _22=dojo.body().scrollTop||dojo.doc.documentElement.scrollTop||dojo.global.pageYOffset||0;
if(dir==1){
_1f.style.top="0px";
if(_22>1){
_20.style.top=-_22+"px";
if(dojo.config["mblHideAddressBar"]!==false){
setTimeout(function(){
dojo.global.scrollTo(0,1);
},0);
}
}
}else{
if(_22>1||_1f.offsetTop!==0){
var _23=-_1f.offsetTop;
_1f.style.top="0px";
_20.style.top=_23-_22+"px";
if(dojo.config["mblHideAddressBar"]!==false&&_23>0){
setTimeout(function(){
dojo.global.scrollTo(0,_23+1);
},0);
}
}
}
}else{
_1f.style.top="0px";
}
_21.onBeforeTransitionIn.apply(_21,arguments);
}
_1f.style.display="none";
_1f.style.visibility="visible";
this._doTransition(_20,_1f,_1c,dir);
},_doTransition:function(_24,_25,_26,dir){
var rev=(dir==-1)?" reverse":"";
_25.style.display="";
if(!_26||_26=="none"){
this.domNode.style.display="none";
this.invokeCallback();
}else{
dojo.addClass(_24,_26+" out"+rev);
dojo.addClass(_25,_26+" in"+rev);
}
},onAnimationStart:function(e){
},onAnimationEnd:function(e){
var _27=false;
if(dojo.hasClass(this.domNode,"out")){
_27=true;
this.domNode.style.display="none";
dojo.forEach([this._transition,"in","out","reverse"],function(s){
dojo.removeClass(this.domNode,s);
},this);
}
if(e.animationName.indexOf("shrink")===0){
var li=e.target;
li.style.display="none";
dojo.removeClass(li,"mblCloseContent");
}
if(_27){
this.invokeCallback();
}
this.domNode&&(this.domNode.className="mblView");
},invokeCallback:function(){
this.onAfterTransitionOut.apply(this,this._arguments);
var _28=dijit.byNode(this.toNode);
if(_28){
_28.onAfterTransitionIn.apply(_28,this._arguments);
}
dojox.mobile.currentView=_28;
var c=this._context,m=this._method;
if(!c&&!m){
return;
}
if(!m){
m=c;
c=null;
}
c=c||dojo.global;
if(typeof (m)=="string"){
c[m].apply(c,this._args);
}else{
m.apply(c,this._args);
}
},getShowingView:function(){
var _29=this.domNode.parentNode.childNodes;
for(var i=0;i<_29.length;i++){
if(dojo.hasClass(_29[i],"mblView")&&dojo.style(_29[i],"display")!="none"){
return dijit.byNode(_29[i]);
}
}
},show:function(){
var fs=this.getShowingView().domNode.style;
var ts=this.domNode.style;
fs.display="none";
ts.display="";
dojox.mobile.currentView=this;
},addChild:function(_2a){
this.containerNode.appendChild(_2a.domNode);
}});
dojo.declare("dojox.mobile.Heading",dijit._WidgetBase,{back:"",href:"",moveTo:"",transition:"slide",label:"",iconBase:"",buildRendering:function(){
this.domNode=this.containerNode=this.srcNodeRef||dojo.doc.createElement("H1");
this.domNode.className="mblHeading";
this._view=dijit.getEnclosingWidget(this.domNode.parentNode);
if(this.label){
this.domNode.appendChild(document.createTextNode(this.label));
}else{
this.label="";
dojo.forEach(this.domNode.childNodes,function(n){
if(n.nodeType==3){
this.label+=n.nodeValue;
}
},this);
this.label=dojo.trim(this.label);
}
if(this.back){
var btn=dojo.create("DIV",{className:"mblArrowButton"},this.domNode,"first");
var _2b=dojo.create("DIV",{className:"mblArrowButtonHead"},btn);
var _2c=dojo.create("DIV",{className:"mblArrowButtonBody mblArrowButtonText"},btn);
this._body=_2c;
this._head=_2b;
this._btn=btn;
_2c.innerHTML=this.back;
this.connect(_2c,"onclick","onClick");
var _2d=dojo.create("DIV",{className:"mblArrowButtonNeck"},btn);
btn.style.width=_2c.offsetWidth+_2b.offsetWidth+"px";
this.setLabel(this.label);
}
},startup:function(){
if(this._btn){
this._btn.style.width=this._body.offsetWidth+this._head.offsetWidth+"px";
}
},onClick:function(e){
var h1=this.domNode;
dojo.addClass(h1,"mblArrowButtonSelected");
setTimeout(function(){
dojo.removeClass(h1,"mblArrowButtonSelected");
},1000);
this.goTo(this.moveTo,this.href);
},setLabel:function(_2e){
if(_2e!=this.label){
this.label=_2e;
this.domNode.firstChild.nodeValue=_2e;
}
},goTo:function(_2f,_30){
if(!this._view){
this._view=dijit.byNode(this.domNode.parentNode);
}
if(!this._view){
return;
}
if(_30){
this._view.performTransition(null,-1,this.transition,this,function(){
location.href=_30;
});
}else{
if(dojox.mobile.app&&dojox.mobile.app.STAGE_CONTROLLER_ACTIVE){
dojo.publish("/dojox/mobile/app/goback");
}else{
this._view.performTransition(_2f,-1,this.transition);
}
}
}});
dojo.declare("dojox.mobile.RoundRect",dijit._WidgetBase,{shadow:false,buildRendering:function(){
this.domNode=this.containerNode=this.srcNodeRef||dojo.doc.createElement("DIV");
this.domNode.className=this.shadow?"mblRoundRect mblShadow":"mblRoundRect";
}});
dojo.declare("dojox.mobile.RoundRectCategory",dijit._WidgetBase,{label:"",buildRendering:function(){
this.domNode=this.containerNode=this.srcNodeRef||dojo.doc.createElement("H2");
this.domNode.className="mblRoundRectCategory";
if(this.label){
this.domNode.innerHTML=this.label;
}else{
this.label=this.domNode.innerHTML;
}
}});
dojo.declare("dojox.mobile.EdgeToEdgeCategory",dojox.mobile.RoundRectCategory,{buildRendering:function(){
this.inherited(arguments);
this.domNode.className="mblEdgeToEdgeCategory";
}});
dojo.declare("dojox.mobile.RoundRectList",dijit._WidgetBase,{transition:"slide",iconBase:"",iconPos:"",buildRendering:function(){
this.domNode=this.containerNode=this.srcNodeRef||dojo.doc.createElement("UL");
this.domNode.className="mblRoundRectList";
},addChild:function(_31){
this.containerNode.appendChild(_31.domNode);
_31.inheritParams();
_31.setIcon();
}});
dojo.declare("dojox.mobile.EdgeToEdgeList",dojox.mobile.RoundRectList,{stateful:false,buildRendering:function(){
this.inherited(arguments);
this.domNode.className="mblEdgeToEdgeList";
}});
dojo.declare("dojox.mobile.AbstractItem",dijit._WidgetBase,{icon:"",iconPos:"",href:"",hrefTarget:"",moveTo:"",scene:"",clickable:false,url:"",transition:"",transitionDir:1,callback:null,sync:true,label:"",toggle:false,_duration:800,inheritParams:function(){
var _32=this.getParentWidget();
if(_32){
if(!this.transition){
this.transition=_32.transition;
}
if(!this.icon){
this.icon=_32.iconBase;
}
if(!this.iconPos){
this.iconPos=_32.iconPos;
}
}
},findCurrentView:function(_33){
var w;
if(_33){
w=dijit.byId(_33);
if(w){
return w.getShowingView();
}
}
var n=this.domNode.parentNode;
while(true){
w=dijit.getEnclosingWidget(n);
if(!w){
return null;
}
if(w.performTransition){
break;
}
n=w.domNode.parentNode;
}
return w;
},transitionTo:function(_34,_35,url,_36){
var w=this.findCurrentView(_34);
if(!w||_34&&w===dijit.byId(_34)){
return;
}
if(_35){
if(this.hrefTarget){
dojox.mobile.openWindow(this.href,this.hrefTarget);
}else{
w.performTransition(null,this.transitionDir,this.transition,this,function(){
location.href=_35;
});
}
return;
}else{
if(_36){
dojo.publish("/dojox/mobile/app/pushScene",[_36]);
return;
}
}
if(url){
var id;
if(dojox.mobile._viewMap&&dojox.mobile._viewMap[url]){
id=dojox.mobile._viewMap[url];
}else{
var _37=this._text;
if(!_37){
if(this.sync){
_37=dojo.trim(dojo._getText(url));
}else{
dojo["require"]("dojo._base.xhr");
var _38=dojox.mobile.ProgressIndicator.getInstance();
dojo.body().appendChild(_38.domNode);
_38.start();
var xhr=dojo.xhrGet({url:url,handleAs:"text"});
xhr.addCallback(dojo.hitch(this,function(_39,_3a){
_38.stop();
if(_39){
this._text=_39;
this.transitionTo(_34,_35,url,_36);
}
}));
xhr.addErrback(function(_3b){
_38.stop();
alert("Failed to load "+url+"\n"+(_3b.description||_3b));
});
return;
}
}
this._text=null;
id=this._parse(_37);
if(!dojox.mobile._viewMap){
dojox.mobile._viewMap=[];
}
dojox.mobile._viewMap[url]=id;
}
_34=id;
}
w.performTransition(_34,this.transitionDir,this.transition,this.callback&&this,this.callback);
},_parse:function(_3c){
var _3d=dojo.create("DIV");
var _3e;
if(_3c.charAt(0)=="<"){
_3d.innerHTML=_3c;
_3e=_3d.firstChild;
if(!_3e&&_3e.nodeType!=1){
alert("dojox.mobile.AbstractItem#transitionTo: invalid view content");
return;
}
_3e.setAttribute("_started","true");
_3e.style.visibility="hidden";
dojo.body().appendChild(_3d);
(dojox.mobile.parser||dojo.parser).parse(_3d);
}else{
if(_3c.charAt(0)=="{"){
dojo.body().appendChild(_3d);
this._ws=[];
_3e=this._instantiate(eval("("+_3c+")"),_3d);
for(var i=0;i<this._ws.length;i++){
var w=this._ws[i];
w.startup&&!w._started&&(!w.getParent||!w.getParent())&&w.startup();
}
this._ws=null;
}
}
_3e.style.display="none";
_3e.style.visibility="visible";
var id=_3e.id;
return dojo.hash?"#"+id:id;
},_instantiate:function(obj,_3f,_40){
var _41;
for(var key in obj){
if(key.charAt(0)=="@"){
continue;
}
var cls=dojo.getObject(key);
if(!cls){
continue;
}
var _42={};
var _43=cls.prototype;
var _44=dojo.isArray(obj[key])?obj[key]:[obj[key]];
for(var i=0;i<_44.length;i++){
for(var _45 in _44[i]){
if(_45.charAt(0)=="@"){
var val=_44[i][_45];
_45=_45.substring(1);
if(typeof _43[_45]=="string"){
_42[_45]=val;
}else{
if(typeof _43[_45]=="number"){
_42[_45]=val-0;
}else{
if(typeof _43[_45]=="boolean"){
_42[_45]=(val!="false");
}else{
if(typeof _43[_45]=="object"){
_42[_45]=eval("("+val+")");
}
}
}
}
}
}
_41=new cls(_42,_3f);
if(!_3f){
this._ws.push(_41);
}
if(_40&&_40.addChild){
_40.addChild(_41);
}
this._instantiate(_44[i],null,_41);
}
}
return _41&&_41.domNode;
},createDomButton:function(_46,_47){
var s=_46.className;
if(s.match(/mblDomButton\w+_(\d+)/)){
var _48=RegExp.$1-0;
for(var i=0,p=(_47||_46);i<_48;i++){
p=dojo.create("DIV",null,p);
}
}
},select:function(_49){
},defaultClickAction:function(){
if(this.toggle){
this.select(this.selected);
}else{
if(!this.selected){
this.select();
if(!this.selectOne){
var _4a=this;
setTimeout(function(){
_4a.select(true);
},this._duration);
}
if(this.moveTo||this.href||this.url||this.scene){
this.transitionTo(this.moveTo,this.href,this.url,this.scene);
}
}
}
},getParentWidget:function(){
var ref=this.srcNodeRef||this.domNode;
return ref&&ref.parentNode?dijit.getEnclosingWidget(ref.parentNode):null;
}});
dojo.declare("dojox.mobile.ListItem",dojox.mobile.AbstractItem,{rightText:"",btnClass:"",anchorLabel:false,noArrow:false,selected:false,buildRendering:function(){
this.inheritParams();
var a=this.anchorNode=dojo.create("A");
a.className="mblListItemAnchor";
var box=dojo.create("DIV");
box.className="mblListItemTextBox";
if(this.anchorLabel){
box.style.cursor="pointer";
}
var r=this.srcNodeRef;
if(r){
for(var i=0,len=r.childNodes.length;i<len;i++){
box.appendChild(r.removeChild(r.firstChild));
}
}
if(this.label){
box.appendChild(dojo.doc.createTextNode(this.label));
}
a.appendChild(box);
if(this.rightText){
this._setRightTextAttr(this.rightText);
}
if(this.moveTo||this.href||this.url||this.clickable){
var _4b=this.getParentWidget();
if(!this.noArrow&&!(_4b&&_4b.stateful)){
var _4c=dojo.create("DIV");
_4c.className="mblArrow";
a.appendChild(_4c);
}
this.connect(a,"onclick","onClick");
}else{
if(this.btnClass){
var div=this.btnNode=dojo.create("DIV");
div.className=this.btnClass+" mblRightButton";
div.appendChild(dojo.create("DIV"));
div.appendChild(dojo.create("P"));
var _4d=dojo.create("DIV");
_4d.className="mblRightButtonContainer";
_4d.appendChild(div);
a.appendChild(_4d);
dojo.addClass(a,"mblListItemAnchorHasRightButton");
setTimeout(function(){
_4d.style.width=div.offsetWidth+"px";
_4d.style.height=div.offsetHeight+"px";
if(dojo.isIE){
a.parentNode.style.height=a.parentNode.offsetHeight+"px";
}
},0);
}
}
if(this.anchorLabel){
box.style.display="inline";
}
var li=this.domNode=this.containerNode=this.srcNodeRef||dojo.doc.createElement("LI");
li.className="mblListItem"+(this.selected?" mblItemSelected":"");
li.appendChild(a);
this.setIcon();
},setIcon:function(){
if(this.iconNode){
return;
}
var a=this.anchorNode;
if(this.icon&&this.icon!="none"){
var img=this.iconNode=dojo.create("IMG");
img.className="mblListItemIcon";
img.src=this.icon;
this.domNode.insertBefore(img,a);
dojox.mobile.setupIcon(this.iconNode,this.iconPos);
dojo.removeClass(a,"mblListItemAnchorNoIcon");
}else{
dojo.addClass(a,"mblListItemAnchorNoIcon");
}
},onClick:function(e){
var a=e.currentTarget;
var li=a.parentNode;
if(dojo.hasClass(li,"mblItemSelected")){
return;
}
if(this.anchorLabel){
for(var p=e.target;p.tagName!="LI";p=p.parentNode){
if(p.className=="mblListItemTextBox"){
dojo.addClass(p,"mblListItemTextBoxSelected");
setTimeout(function(){
dojo.removeClass(p,"mblListItemTextBoxSelected");
},1000);
this.onAnchorLabelClicked(e);
return;
}
}
}
if(this.getParentWidget().stateful){
for(var i=0,c=li.parentNode.childNodes;i<c.length;i++){
dojo.removeClass(c[i],"mblItemSelected");
}
}else{
setTimeout(function(){
dojo.removeClass(li,"mblItemSelected");
},1000);
}
dojo.addClass(li,"mblItemSelected");
this.transitionTo(this.moveTo,this.href,this.url,this.scene);
},onAnchorLabelClicked:function(e){
},_setRightTextAttr:function(_4e){
this.rightText=_4e;
if(!this._rightTextNode){
this._rightTextNode=dojo.create("DIV",{className:"mblRightText"},this.anchorNode);
}
this._rightTextNode.innerHTML=_4e;
}});
dojo.declare("dojox.mobile.Switch",dijit._WidgetBase,{value:"on",leftLabel:"ON",rightLabel:"OFF",_width:53,buildRendering:function(){
this.domNode=this.srcNodeRef||dojo.doc.createElement("DIV");
this.domNode.className="mblSwitch";
this.domNode.innerHTML="<div class=\"mblSwitchInner\">"+"<div class=\"mblSwitchBg mblSwitchBgLeft\">"+"<div class=\"mblSwitchText mblSwitchTextLeft\">"+this.leftLabel+"</div>"+"</div>"+"<div class=\"mblSwitchBg mblSwitchBgRight\">"+"<div class=\"mblSwitchText mblSwitchTextRight\">"+this.rightLabel+"</div>"+"</div>"+"<div class=\"mblSwitchKnob\"></div>"+"</div>";
var n=this.inner=this.domNode.firstChild;
this.left=n.childNodes[0];
this.right=n.childNodes[1];
this.knob=n.childNodes[2];
dojo.addClass(this.domNode,(this.value=="on")?"mblSwitchOn":"mblSwitchOff");
this[this.value=="off"?"left":"right"].style.display="none";
},postCreate:function(){
this.connect(this.knob,"onclick","onClick");
this.connect(this.knob,"touchstart","onTouchStart");
this.connect(this.knob,"mousedown","onTouchStart");
},_changeState:function(_4f){
this.inner.style.left="";
dojo.addClass(this.domNode,"mblSwitchAnimation");
dojo.removeClass(this.domNode,(_4f=="on")?"mblSwitchOff":"mblSwitchOn");
dojo.addClass(this.domNode,(_4f=="on")?"mblSwitchOn":"mblSwitchOff");
var _50=this;
setTimeout(function(){
_50[_4f=="off"?"left":"right"].style.display="none";
dojo.removeClass(_50.domNode,"mblSwitchAnimation");
},300);
},onClick:function(e){
if(this._moved){
return;
}
this.value=(this.value=="on")?"off":"on";
this._changeState(this.value);
this.onStateChanged(this.value);
},onTouchStart:function(e){
this._moved=false;
this.innerStartX=this.inner.offsetLeft;
if(e.targetTouches){
this.touchStartX=e.targetTouches[0].clientX;
this._conn1=dojo.connect(this.inner,"touchmove",this,"onTouchMove");
this._conn2=dojo.connect(this.inner,"touchend",this,"onTouchEnd");
}
this.left.style.display="block";
this.right.style.display="block";
dojo.stopEvent(e);
},onTouchMove:function(e){
e.preventDefault();
var dx;
if(e.targetTouches){
if(e.targetTouches.length!=1){
return false;
}
dx=e.targetTouches[0].clientX-this.touchStartX;
}else{
dx=e.clientX-this.touchStartX;
}
var pos=this.innerStartX+dx;
var d=10;
if(pos<=-(this._width-d)){
pos=-this._width;
}
if(pos>=-d){
pos=0;
}
this.inner.style.left=pos+"px";
this._moved=true;
},onTouchEnd:function(e){
dojo.disconnect(this._conn1);
dojo.disconnect(this._conn2);
if(this.innerStartX==this.inner.offsetLeft){
if(dojo.isWebKit){
var ev=dojo.doc.createEvent("MouseEvents");
ev.initEvent("click",true,true);
this.knob.dispatchEvent(ev);
}
return;
}
var _51=(this.inner.offsetLeft<-(this._width/2))?"off":"on";
this._changeState(_51);
if(_51!=this.value){
this.value=_51;
this.onStateChanged(this.value);
}
},onStateChanged:function(_52){
}});
dojo.declare("dojox.mobile.Button",dijit._WidgetBase,{btnClass:"mblBlueButton",duration:1000,label:null,buildRendering:function(){
this.domNode=this.containerNode=this.srcNodeRef||dojo.doc.createElement("BUTTON");
this.domNode.className="mblButton "+this.btnClass;
if(this.label){
this.domNode.innerHTML=this.label;
}
this.connect(this.domNode,"onclick","onClick");
},onClick:function(e){
var _53=this.domNode;
var c="mblButtonSelected "+this.btnClass+"Selected";
dojo.addClass(_53,c);
setTimeout(function(){
dojo.removeClass(_53,c);
},this.duration);
}});
dojo.declare("dojox.mobile.ToolBarButton",dojox.mobile.AbstractItem,{selected:false,_defaultColor:"mblColorDefault",_selColor:"mblColorDefaultSel",buildRendering:function(){
this.inheritParams();
this.domNode=this.containerNode=this.srcNodeRef||dojo.doc.createElement("div");
dojo.addClass(this.domNode,"mblToolbarButton mblArrowButtonText");
var _54;
if(this.selected){
_54=this._selColor;
}else{
if(this.domNode.className.indexOf("mblColor")==-1){
_54=this._defaultColor;
}
}
dojo.addClass(this.domNode,_54);
if(this.label){
this.domNode.innerHTML=this.label;
}else{
this.label=this.domNode.innerHTML;
}
if(this.icon&&this.icon!="none"){
var img;
if(this.iconPos){
var _55=dojo.create("DIV",null,this.domNode);
img=dojo.create("IMG",null,_55);
img.style.position="absolute";
var arr=this.iconPos.split(/[ ,]/);
dojo.style(_55,{position:"relative",width:arr[2]+"px",height:arr[3]+"px"});
}else{
img=dojo.create("IMG",null,this.domNode);
}
img.src=this.icon;
dojox.mobile.setupIcon(img,this.iconPos);
this.iconNode=img;
}
this.createDomButton(this.domNode);
this.connect(this.domNode,"onclick","onClick");
},select:function(_56){
dojo.toggleClass(this.domNode,this._selColor,!_56);
this.selected=!_56;
},onClick:function(e){
this.defaultClickAction();
}});
dojo.declare("dojox.mobile.ProgressIndicator",null,{interval:100,colors:["#C0C0C0","#C0C0C0","#C0C0C0","#C0C0C0","#C0C0C0","#C0C0C0","#B8B9B8","#AEAFAE","#A4A5A4","#9A9A9A","#8E8E8E","#838383"],_bars:[],constructor:function(){
this.domNode=dojo.create("DIV");
this.domNode.className="mblProgContainer";
for(var i=0;i<12;i++){
var div=dojo.create("DIV");
div.className="mblProg mblProg"+i;
this.domNode.appendChild(div);
this._bars.push(div);
}
},start:function(){
var _57=0;
var _58=this;
this.timer=setInterval(function(){
_57--;
_57=_57<0?11:_57;
var c=_58.colors;
for(var i=0;i<12;i++){
var idx=(_57+i)%12;
_58._bars[i].style.backgroundColor=c[idx];
}
},this.interval);
},stop:function(){
if(this.timer){
clearInterval(this.timer);
}
this.timer=null;
if(this.domNode.parentNode){
this.domNode.parentNode.removeChild(this.domNode);
}
}});
dojox.mobile.ProgressIndicator._instance=null;
dojox.mobile.ProgressIndicator.getInstance=function(){
if(!dojox.mobile.ProgressIndicator._instance){
dojox.mobile.ProgressIndicator._instance=new dojox.mobile.ProgressIndicator();
}
return dojox.mobile.ProgressIndicator._instance;
};
dojox.mobile.addClass=function(){
var _59=document.getElementsByTagName("link");
for(var i=0,len=_59.length;i<len;i++){
if(_59[i].href.match(/dojox\/mobile\/themes\/(\w+)\//)){
dojox.mobile.theme=RegExp.$1;
dojo.addClass(dojo.body(),dojox.mobile.theme);
break;
}
}
};
dojox.mobile.setupIcon=function(_5a,_5b){
if(_5a&&_5b){
var arr=dojo.map(_5b.split(/[ ,]/),function(_5c){
return _5c-0;
});
var t=arr[0];
var r=arr[1]+arr[2];
var b=arr[0]+arr[3];
var l=arr[1];
_5a.style.clip="rect("+t+"px "+r+"px "+b+"px "+l+"px)";
_5a.style.top=dojo.style(_5a,"top")-t+"px";
_5a.style.left=dojo.style(_5a.parentNode,"paddingLeft")-l+"px";
}
};
dojox.mobile.hideAddressBar=function(){
dojo.body().style.minHeight="1000px";
setTimeout(function(){
scrollTo(0,1);
},100);
setTimeout(function(){
scrollTo(0,1);
},400);
setTimeout(function(){
scrollTo(0,1);
dojo.body().style.minHeight=(dojo.global.innerHeight||dojo.doc.documentElement.clientHeight)+"px";
},1000);
};
dojox.mobile.openWindow=function(url,_5d){
dojo.global.open(url,_5d||"_blank");
};
dojo._loaders.unshift(function(){
var _5e=dojo.body().getElementsByTagName("*");
var i,len,s;
len=_5e.length;
for(i=0;i<len;i++){
s=_5e[i].getAttribute("dojoType");
if(s){
if(_5e[i].parentNode.getAttribute("lazy")=="true"){
_5e[i].setAttribute("__dojoType",s);
_5e[i].removeAttribute("dojoType");
}
}
}
});
dojo.addOnLoad(function(){
dojox.mobile.addClass();
if(dojo.config["mblApplyPageStyles"]!==false){
dojo.addClass(dojo.doc.documentElement,"mobile");
}
if(dojo.config["mblHideAddressBar"]!==false){
dojox.mobile.hideAddressBar();
if(dojo.config["mblAlwaysHideAddressBar"]==true){
if(dojo.global.onorientationchange!==undefined){
dojo.connect(dojo.global,"onorientationchange",dojox.mobile.hideAddressBar);
}else{
dojo.connect(dojo.global,"onresize",dojox.mobile.hideAddressBar);
}
}
}
var _5f=dojo.body().getElementsByTagName("*");
var i,len=_5f.length,s;
for(i=0;i<len;i++){
s=_5f[i].getAttribute("__dojoType");
if(s){
_5f[i].setAttribute("dojoType",s);
_5f[i].removeAttribute("__dojoType");
}
}
if(dojo.hash){
var _60=function(_61){
var arr;
arr=dijit.findWidgets(_61);
var _62=arr;
for(var i=0;i<_62.length;i++){
arr=arr.concat(_60(_62[i].containerNode));
}
return arr;
};
dojo.subscribe("/dojo/hashchange",null,function(_63){
var _64=dojox.mobile.currentView;
if(!_64){
return;
}
var _65=dojox.mobile._params;
if(!_65){
var _66=_63?_63:dojox.mobile._defaultView.id;
var _67=_60(_64.domNode);
var dir=1,_68="slide";
for(i=0;i<_67.length;i++){
var w=_67[i];
if("#"+_66==w.moveTo){
_68=w.transition;
dir=(w instanceof dojox.mobile.Heading)?-1:1;
break;
}
}
_65=[_66,dir,_68];
}
_64.performTransition.apply(_64,_65);
dojox.mobile._params=null;
});
}
dojo.body().style.visibility="visible";
});
dijit.getEnclosingWidget=function(_69){
while(_69&&_69.tagName!=="BODY"){
if(_69.getAttribute&&_69.getAttribute("widgetId")){
return dijit.registry.byId(_69.getAttribute("widgetId"));
}
_69=_69._parentNode||_69.parentNode;
}
return null;
};
}
