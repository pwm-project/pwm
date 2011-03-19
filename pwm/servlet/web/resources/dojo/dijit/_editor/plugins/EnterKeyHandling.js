/*
	Copyright (c) 2004-2011, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dijit._editor.plugins.EnterKeyHandling"]){
dojo._hasResource["dijit._editor.plugins.EnterKeyHandling"]=true;
dojo.provide("dijit._editor.plugins.EnterKeyHandling");
dojo.require("dojo.window");
dojo.require("dijit._editor._Plugin");
dojo.require("dijit._editor.range");
dojo.declare("dijit._editor.plugins.EnterKeyHandling",dijit._editor._Plugin,{blockNodeForEnter:"BR",constructor:function(_1){
if(_1){
if("blockNodeForEnter" in _1){
_1.blockNodeForEnter=_1.blockNodeForEnter.toUpperCase();
}
dojo.mixin(this,_1);
}
},setEditor:function(_2){
if(this.editor===_2){
return;
}
this.editor=_2;
if(this.blockNodeForEnter=="BR"){
this.editor.customUndo=true;
_2.onLoadDeferred.addCallback(dojo.hitch(this,function(d){
this.connect(_2.document,"onkeypress",function(e){
if(e.charOrCode==dojo.keys.ENTER){
var ne=dojo.mixin({},e);
ne.shiftKey=true;
if(!this.handleEnterKey(ne)){
dojo.stopEvent(e);
}
}
});
return d;
}));
}else{
if(this.blockNodeForEnter){
var h=dojo.hitch(this,this.handleEnterKey);
_2.addKeyHandler(13,0,0,h);
_2.addKeyHandler(13,0,1,h);
this.connect(this.editor,"onKeyPressed","onKeyPressed");
}
}
},onKeyPressed:function(e){
if(this._checkListLater){
if(dojo.withGlobal(this.editor.window,"isCollapsed",dijit)){
var _3=dojo.withGlobal(this.editor.window,"getAncestorElement",dijit._editor.selection,["LI"]);
if(!_3){
dijit._editor.RichText.prototype.execCommand.call(this.editor,"formatblock",this.blockNodeForEnter);
var _4=dojo.withGlobal(this.editor.window,"getAncestorElement",dijit._editor.selection,[this.blockNodeForEnter]);
if(_4){
_4.innerHTML=this.bogusHtmlContent;
if(dojo.isIE){
var r=this.editor.document.selection.createRange();
r.move("character",-1);
r.select();
}
}else{
console.error("onKeyPressed: Cannot find the new block node");
}
}else{
if(dojo.isMoz){
if(_3.parentNode.parentNode.nodeName=="LI"){
_3=_3.parentNode.parentNode;
}
}
var fc=_3.firstChild;
if(fc&&fc.nodeType==1&&(fc.nodeName=="UL"||fc.nodeName=="OL")){
_3.insertBefore(fc.ownerDocument.createTextNode(" "),fc);
var _5=dijit.range.create(this.editor.window);
_5.setStart(_3.firstChild,0);
var _6=dijit.range.getSelection(this.editor.window,true);
_6.removeAllRanges();
_6.addRange(_5);
}
}
}
this._checkListLater=false;
}
if(this._pressedEnterInBlock){
if(this._pressedEnterInBlock.previousSibling){
this.removeTrailingBr(this._pressedEnterInBlock.previousSibling);
}
delete this._pressedEnterInBlock;
}
},bogusHtmlContent:"&nbsp;",blockNodes:/^(?:P|H1|H2|H3|H4|H5|H6|LI)$/,handleEnterKey:function(e){
var _7,_8,_9,_a=this.editor.document,br,rs,_b;
if(e.shiftKey){
var _c=dojo.withGlobal(this.editor.window,"getParentElement",dijit._editor.selection);
var _d=dijit.range.getAncestor(_c,this.blockNodes);
if(_d){
if(_d.tagName=="LI"){
return true;
}
_7=dijit.range.getSelection(this.editor.window);
_8=_7.getRangeAt(0);
if(!_8.collapsed){
_8.deleteContents();
_7=dijit.range.getSelection(this.editor.window);
_8=_7.getRangeAt(0);
}
if(dijit.range.atBeginningOfContainer(_d,_8.startContainer,_8.startOffset)){
br=_a.createElement("br");
_9=dijit.range.create(this.editor.window);
_d.insertBefore(br,_d.firstChild);
_9.setStartBefore(br.nextSibling);
_7.removeAllRanges();
_7.addRange(_9);
}else{
if(dijit.range.atEndOfContainer(_d,_8.startContainer,_8.startOffset)){
_9=dijit.range.create(this.editor.window);
br=_a.createElement("br");
_d.appendChild(br);
_d.appendChild(_a.createTextNode(" "));
_9.setStart(_d.lastChild,0);
_7.removeAllRanges();
_7.addRange(_9);
}else{
rs=_8.startContainer;
if(rs&&rs.nodeType==3){
_b=rs.nodeValue;
dojo.withGlobal(this.editor.window,function(){
var _e=_a.createTextNode(_b.substring(0,_8.startOffset));
var _f=_a.createTextNode(_b.substring(_8.startOffset));
var _10=_a.createElement("br");
if(_f.nodeValue==""&&dojo.isWebKit){
_f=_a.createTextNode(" ");
}
dojo.place(_e,rs,"after");
dojo.place(_10,_e,"after");
dojo.place(_f,_10,"after");
dojo.destroy(rs);
_9=dijit.range.create(dojo.gobal);
_9.setStart(_f,0);
_7.removeAllRanges();
_7.addRange(_9);
});
return false;
}
return true;
}
}
}else{
_7=dijit.range.getSelection(this.editor.window);
if(_7.rangeCount){
_8=_7.getRangeAt(0);
if(_8&&_8.startContainer){
if(!_8.collapsed){
_8.deleteContents();
_7=dijit.range.getSelection(this.editor.window);
_8=_7.getRangeAt(0);
}
rs=_8.startContainer;
var _11,_12,_13;
if(rs&&rs.nodeType==3){
dojo.withGlobal(this.editor.window,dojo.hitch(this,function(){
var _14=false;
var _15=_8.startOffset;
if(rs.length<_15){
ret=this._adjustNodeAndOffset(rs,_15);
rs=ret.node;
_15=ret.offset;
}
_b=rs.nodeValue;
_11=_a.createTextNode(_b.substring(0,_15));
_12=_a.createTextNode(_b.substring(_15));
_13=_a.createElement("br");
if(!_12.length){
_12=_a.createTextNode(" ");
_14=true;
}
if(_11.length){
dojo.place(_11,rs,"after");
}else{
_11=rs;
}
dojo.place(_13,_11,"after");
dojo.place(_12,_13,"after");
dojo.destroy(rs);
_9=dijit.range.create(dojo.gobal);
_9.setStart(_12,0);
_9.setEnd(_12,_12.length);
_7.removeAllRanges();
_7.addRange(_9);
if(_14&&!dojo.isWebKit){
dijit._editor.selection.remove();
}else{
dijit._editor.selection.collapse(true);
}
}));
}else{
dojo.withGlobal(this.editor.window,dojo.hitch(this,function(){
var _16=_a.createElement("br");
rs.appendChild(_16);
var _17=_a.createTextNode(" ");
rs.appendChild(_17);
_9=dijit.range.create(dojo.global);
_9.setStart(_17,0);
_9.setEnd(_17,_17.length);
_7.removeAllRanges();
_7.addRange(_9);
dijit._editor.selection.collapse(true);
}));
}
}
}else{
dijit._editor.RichText.prototype.execCommand.call(this.editor,"inserthtml","<br>");
}
}
return false;
}
var _18=true;
_7=dijit.range.getSelection(this.editor.window);
_8=_7.getRangeAt(0);
if(!_8.collapsed){
_8.deleteContents();
_7=dijit.range.getSelection(this.editor.window);
_8=_7.getRangeAt(0);
}
var _19=dijit.range.getBlockAncestor(_8.endContainer,null,this.editor.editNode);
var _1a=_19.blockNode;
if((this._checkListLater=(_1a&&(_1a.nodeName=="LI"||_1a.parentNode.nodeName=="LI")))){
if(dojo.isMoz){
this._pressedEnterInBlock=_1a;
}
if(/^(\s|&nbsp;|\xA0|<span\b[^>]*\bclass=['"]Apple-style-span['"][^>]*>(\s|&nbsp;|\xA0)<\/span>)?(<br>)?$/.test(_1a.innerHTML)){
_1a.innerHTML="";
if(dojo.isWebKit){
_9=dijit.range.create(this.editor.window);
_9.setStart(_1a,0);
_7.removeAllRanges();
_7.addRange(_9);
}
this._checkListLater=false;
}
return true;
}
if(!_19.blockNode||_19.blockNode===this.editor.editNode){
try{
dijit._editor.RichText.prototype.execCommand.call(this.editor,"formatblock",this.blockNodeForEnter);
}
catch(e2){
}
_19={blockNode:dojo.withGlobal(this.editor.window,"getAncestorElement",dijit._editor.selection,[this.blockNodeForEnter]),blockContainer:this.editor.editNode};
if(_19.blockNode){
if(_19.blockNode!=this.editor.editNode&&(!(_19.blockNode.textContent||_19.blockNode.innerHTML).replace(/^\s+|\s+$/g,"").length)){
this.removeTrailingBr(_19.blockNode);
return false;
}
}else{
_19.blockNode=this.editor.editNode;
}
_7=dijit.range.getSelection(this.editor.window);
_8=_7.getRangeAt(0);
}
var _1b=_a.createElement(this.blockNodeForEnter);
_1b.innerHTML=this.bogusHtmlContent;
this.removeTrailingBr(_19.blockNode);
var _1c=_8.endOffset;
var _1d=_8.endContainer;
if(_1d.length<_1c){
var ret=this._adjustNodeAndOffset(_1d,_1c);
_1d=ret.node;
_1c=ret.offset;
}
if(dijit.range.atEndOfContainer(_19.blockNode,_1d,_1c)){
if(_19.blockNode===_19.blockContainer){
_19.blockNode.appendChild(_1b);
}else{
dojo.place(_1b,_19.blockNode,"after");
}
_18=false;
_9=dijit.range.create(this.editor.window);
_9.setStart(_1b,0);
_7.removeAllRanges();
_7.addRange(_9);
if(this.editor.height){
dojo.window.scrollIntoView(_1b);
}
}else{
if(dijit.range.atBeginningOfContainer(_19.blockNode,_8.startContainer,_8.startOffset)){
dojo.place(_1b,_19.blockNode,_19.blockNode===_19.blockContainer?"first":"before");
if(_1b.nextSibling&&this.editor.height){
_9=dijit.range.create(this.editor.window);
_9.setStart(_1b.nextSibling,0);
_7.removeAllRanges();
_7.addRange(_9);
dojo.window.scrollIntoView(_1b.nextSibling);
}
_18=false;
}else{
if(_19.blockNode===_19.blockContainer){
_19.blockNode.appendChild(_1b);
}else{
dojo.place(_1b,_19.blockNode,"after");
}
_18=false;
if(_19.blockNode.style){
if(_1b.style){
if(_19.blockNode.style.cssText){
_1b.style.cssText=_19.blockNode.style.cssText;
}
}
}
rs=_8.startContainer;
if(rs&&rs.nodeType==3){
var _1e,_1f;
_1c=_8.endOffset;
if(rs.length<_1c){
ret=this._adjustNodeAndOffset(rs,_1c);
rs=ret.node;
_1c=ret.offset;
}
_b=rs.nodeValue;
var _11=_a.createTextNode(_b.substring(0,_1c));
var _12=_a.createTextNode(_b.substring(_1c,_b.length));
dojo.place(_11,rs,"before");
dojo.place(_12,rs,"after");
dojo.destroy(rs);
var _20=_11.parentNode;
while(_20!==_19.blockNode){
var tg=_20.tagName;
var _21=_a.createElement(tg);
if(_20.style){
if(_21.style){
if(_20.style.cssText){
_21.style.cssText=_20.style.cssText;
}
}
}
_1e=_12;
while(_1e){
_1f=_1e.nextSibling;
_21.appendChild(_1e);
_1e=_1f;
}
dojo.place(_21,_20,"after");
_11=_20;
_12=_21;
_20=_20.parentNode;
}
_1e=_12;
if(_1e.nodeType==1||(_1e.nodeType==3&&_1e.nodeValue)){
_1b.innerHTML="";
}
while(_1e){
_1f=_1e.nextSibling;
_1b.appendChild(_1e);
_1e=_1f;
}
}
_9=dijit.range.create(this.editor.window);
_9.setStart(_1b,0);
_7.removeAllRanges();
_7.addRange(_9);
if(this.editor.height){
dijit.scrollIntoView(_1b);
}
if(dojo.isMoz){
this._pressedEnterInBlock=_19.blockNode;
}
}
}
return _18;
},_adjustNodeAndOffset:function(_22,_23){
while(_22.length<_23&&_22.nextSibling&&_22.nextSibling.nodeType==3){
_23=_23-_22.length;
_22=_22.nextSibling;
}
var ret={"node":_22,"offset":_23};
return ret;
},removeTrailingBr:function(_24){
var _25=/P|DIV|LI/i.test(_24.tagName)?_24:dijit._editor.selection.getParentOfType(_24,["P","DIV","LI"]);
if(!_25){
return;
}
if(_25.lastChild){
if((_25.childNodes.length>1&&_25.lastChild.nodeType==3&&/^[\s\xAD]*$/.test(_25.lastChild.nodeValue))||_25.lastChild.tagName=="BR"){
dojo.destroy(_25.lastChild);
}
}
if(!_25.childNodes.length){
_25.innerHTML=this.bogusHtmlContent;
}
}});
}
