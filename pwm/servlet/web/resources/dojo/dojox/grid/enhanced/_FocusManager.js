/*
	Copyright (c) 2004-2011, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.enhanced._FocusManager"]){
dojo._hasResource["dojox.grid.enhanced._FocusManager"]=true;
dojo.provide("dojox.grid.enhanced._FocusManager");
dojo.declare("dojox.grid.enhanced._FocusArea",null,{constructor:function(_1,_2){
this._fm=_2;
this._evtStack=[_1.name];
var _3=function(){
return true;
};
_1.onFocus=_1.onFocus||_3;
_1.onBlur=_1.onBlur||_3;
_1.onMove=_1.onMove||_3;
_1.onKeyUp=_1.onKeyUp||_3;
_1.onKeyDown=_1.onKeyDown||_3;
dojo.mixin(this,_1);
},move:function(_4,_5,_6){
if(this.name){
var i,_7=this._evtStack.length;
for(i=_7-1;i>=0;--i){
if(this._fm._areas[this._evtStack[i]].onMove(_4,_5,_6)===false){
return false;
}
}
}
return true;
},_onKeyEvent:function(_8,_9){
if(this.name){
var i,_a=this._evtStack.length;
for(i=_a-1;i>=0;--i){
if(this._fm._areas[this._evtStack[i]][_9](_8,false)===false){
return false;
}
}
for(i=0;i<_a;++i){
if(this._fm._areas[this._evtStack[i]][_9](_8,true)===false){
return false;
}
}
}
return true;
},keydown:function(_b){
return this._onKeyEvent(_b,"onKeyDown");
},keyup:function(_c){
return this._onKeyEvent(_c,"onKeyUp");
},contentMouseEventPlanner:function(){
return 0;
},headerMouseEventPlanner:function(){
return 0;
}});
dojo.declare("dojox.grid.enhanced._FocusManager",dojox.grid._FocusManager,{_stopEvent:function(_d){
try{
if(_d&&_d.preventDefault){
dojo.stopEvent(_d);
}
}
catch(e){
}
},constructor:function(_e){
this.grid=_e;
this._areas={};
this._areaQueue=[];
this._contentMouseEventHandlers=[];
this._headerMouseEventHandlers=[];
this._currentAreaIdx=-1;
this._gridBlured=true;
this._connects.push(dojo.connect(_e,"onBlur",this,"_doBlur"));
this.addArea({name:"header",onFocus:dojo.hitch(this,this.focusHeader),onBlur:dojo.hitch(this,this._blurHeader),onMove:dojo.hitch(this,this._navHeader),getRegions:dojo.hitch(this,this._findHeaderCells),onRegionFocus:dojo.hitch(this,this.doColHeaderFocus),onRegionBlur:dojo.hitch(this,this.doColHeaderBlur),onKeyDown:dojo.hitch(this,this._onHeaderKeyDown)});
this.addArea({name:"content",onFocus:dojo.hitch(this,this._focusContent),onBlur:dojo.hitch(this,this._blurContent),onMove:dojo.hitch(this,this._navContent),onKeyDown:dojo.hitch(this,this._onContentKeyDown)});
this.addArea({name:"editableCell",onFocus:dojo.hitch(this,this._focusEditableCell),onBlur:dojo.hitch(this,this._blurEditableCell),onKeyDown:dojo.hitch(this,this._onEditableCellKeyDown),onContentMouseEvent:dojo.hitch(this,this._onEditableCellMouseEvent),contentMouseEventPlanner:function(_f,_10){
return -1;
}});
this.placeArea("header");
this.placeArea("content");
this.placeArea("editableCell");
this.placeArea("editableCell","above","content");
},destroy:function(){
for(var _11 in this._areas){
var _12=this._areas[_11];
dojo.forEach(_12._connects,dojo.disconnect);
_12._connects=null;
if(_12.uninitialize){
_12.uninitialize();
}
}
this.inherited(arguments);
},addArea:function(_13){
if(_13.name&&dojo.isString(_13.name)){
if(this._areas[_13.name]){
dojo.forEach(_13._connects,dojo.disconnect);
}
this._areas[_13.name]=new dojox.grid.enhanced._FocusArea(_13,this);
if(_13.onHeaderMouseEvent){
this._headerMouseEventHandlers.push(_13.name);
}
if(_13.onContentMouseEvent){
this._contentMouseEventHandlers.push(_13.name);
}
}
},getArea:function(_14){
return this._areas[_14];
},_bindAreaEvents:function(){
var _15,hdl,_16=this._areas;
dojo.forEach(this._areaQueue,function(_17){
_15=_16[_17];
if(!_15._initialized&&dojo.isFunction(_15.initialize)){
_15.initialize();
_15._initialized=true;
}
if(_15.getRegions){
_15._regions=_15.getRegions()||[];
dojo.forEach(_15._connects||[],dojo.disconnect);
_15._connects=[];
dojo.forEach(_15._regions,function(r){
if(_15.onRegionFocus){
hdl=dojo.connect(r,"onfocus",_15.onRegionFocus);
_15._connects.push(hdl);
}
if(_15.onRegionBlur){
hdl=dojo.connect(r,"onblur",_15.onRegionBlur);
_15._connects.push(hdl);
}
});
}
});
},removeArea:function(_18){
var _19=this._areas[_18];
if(_19){
this.ignoreArea(_18);
var i=dojo.indexOf(this._contentMouseEventHandlers,_18);
if(i>=0){
this._contentMouseEventHandlers.splice(i,1);
}
i=dojo.indexOf(this._headerMouseEventHandlers,_18);
if(i>=0){
this._headerMouseEventHandlers.splice(i,1);
}
dojo.forEach(_19._connects,dojo.disconnect);
if(_19.uninitialize){
_19.uninitialize();
}
delete this._areas[_18];
}
},currentArea:function(_1a,_1b){
var idx,cai=this._currentAreaIdx;
if(dojo.isString(_1a)&&(idx=dojo.indexOf(this._areaQueue,_1a))>=0){
if(cai!=idx){
this.tabbingOut=false;
if(_1b&&cai>=0&&cai<this._areaQueue.length){
this._areas[this._areaQueue[cai]].onBlur();
}
this._currentAreaIdx=idx;
}
}else{
return (cai<0||cai>=this._areaQueue.length)?new dojox.grid.enhanced._FocusArea({},this):this._areas[this._areaQueue[this._currentAreaIdx]];
}
return null;
},placeArea:function(_1c,pos,_1d){
if(!this._areas[_1c]){
return;
}
var idx=dojo.indexOf(this._areaQueue,_1d);
switch(pos){
case "after":
if(idx>=0){
++idx;
}
case "before":
if(idx>=0){
this._areaQueue.splice(idx,0,_1c);
break;
}
default:
this._areaQueue.push(_1c);
break;
case "above":
var _1e=true;
case "below":
var _1f=this._areas[_1d];
if(_1f){
if(_1e){
_1f._evtStack.push(_1c);
}else{
_1f._evtStack.splice(0,0,_1c);
}
}
}
},ignoreArea:function(_20){
this._areaQueue=dojo.filter(this._areaQueue,function(_21){
return _21!=_20;
});
},focusArea:function(_22,evt){
var idx;
if(typeof _22=="number"){
idx=_22<0?this._areaQueue.length+_22:_22;
}else{
idx=dojo.indexOf(this._areaQueue,dojo.isString(_22)?_22:(_22&&_22.name));
}
if(idx<0){
idx=0;
}
var _23=idx-this._currentAreaIdx;
this._gridBlured=false;
if(_23){
this.tab(_23,evt);
}else{
this.currentArea().onFocus(evt,_23);
}
},tab:function(_24,evt){
this._gridBlured=false;
this.tabbingOut=false;
if(_24===0){
return;
}
var cai=this._currentAreaIdx;
var dir=_24>0?1:-1;
if(cai<0||cai>=this._areaQueue.length){
cai=(this._currentAreaIdx+=_24);
}else{
var _25=this._areas[this._areaQueue[cai]].onBlur(evt,_24);
if(_25===true){
cai=(this._currentAreaIdx+=_24);
}else{
if(dojo.isString(_25)&&this._areas[_25]){
cai=this._currentAreaIdx=dojo.indexOf(this._areaQueue,_25);
}
}
}
for(;cai>=0&&cai<this._areaQueue.length;cai+=dir){
this._currentAreaIdx=cai;
if(this._areaQueue[cai]&&this._areas[this._areaQueue[cai]].onFocus(evt,_24)){
return;
}
}
this.tabbingOut=true;
if(_24<0){
this._currentAreaIdx=-1;
dijit.focus(this.grid.domNode);
}else{
this._currentAreaIdx=this._areaQueue.length;
dijit.focus(this.grid.lastFocusNode);
}
},_onMouseEvent:function(_26,evt){
var _27=_26.toLowerCase(),_28=this["_"+_27+"MouseEventHandlers"],res=dojo.map(_28,function(_29){
return {"area":_29,"idx":this._areas[_29][_27+"MouseEventPlanner"](evt,_28)};
},this).sort(function(a,b){
return b.idx-a.idx;
}),_2a=dojo.map(res,function(_2b){
return res.area;
}),i=res.length;
while(--i>=0){
if(this._areas[res[i].area]["on"+_26+"MouseEvent"](evt,_2a)===false){
return;
}
}
},contentMouseEvent:function(evt){
this._onMouseEvent("Content",evt);
},headerMouseEvent:function(evt){
this._onMouseEvent("Header",evt);
},initFocusView:function(){
this.focusView=this.grid.views.getFirstScrollingView()||this.focusView||this.grid.views.views[0];
this._bindAreaEvents();
},isNavHeader:function(){
return this._areaQueue[this._currentAreaIdx]=="header";
},previousKey:function(e){
this.tab(-1,e);
},nextKey:function(e){
this.tab(1,e);
},setFocusCell:function(_2c,_2d){
if(_2c){
this.currentArea(this.grid.edit.isEditing()?"editableCell":"content",true);
this._focusifyCellNode(false);
this.cell=_2c;
this.rowIndex=_2d;
this._focusifyCellNode(true);
}
this.grid.onCellFocus(this.cell,this.rowIndex);
},doFocus:function(e){
if(e&&e.target==e.currentTarget&&!this.tabbingOut){
if(this._gridBlured){
this._gridBlured=false;
if(this._currentAreaIdx<0||this._currentAreaIdx>=this._areaQueue.length){
this.focusArea(0,e);
}else{
this.focusArea(this._currentAreaIdx,e);
}
}
}else{
this.tabbingOut=false;
}
dojo.stopEvent(e);
},_doBlur:function(){
this._gridBlured=true;
},doLastNodeFocus:function(e){
if(this.tabbingOut){
this.tabbingOut=false;
}else{
this.focusArea(-1,e);
}
},_delayedHeaderFocus:function(){
if(this.isNavHeader()){
this.focusHeader();
}
},_delayedCellFocus:function(){
if(this.currentArea().name=="content"){
this.focusArea(this._currentAreaIdx);
}
},_changeMenuBindNode:function(_2e,_2f){
var hm=this.grid.headerMenu;
if(hm&&this._contextMenuBindNode==_2e){
hm.unBindDomNode(_2e);
hm.bindDomNode(_2f);
this._contextMenuBindNode=_2f;
}
},focusHeader:function(evt,_30){
var _31=false;
this.inherited(arguments);
if(this._colHeadNode&&dojo.style(this._colHeadNode,"display")!="none"){
dijit.focus(this._colHeadNode);
this._stopEvent(evt);
_31=true;
}
return _31;
},_blurHeader:function(evt,_32){
if(this._colHeadNode){
dojo.removeClass(this._colHeadNode,this.focusClass);
}
dojo.removeAttr(this.grid.domNode,"aria-activedescendant");
this._changeMenuBindNode(this.grid.domNode,this.grid.viewsHeaderNode);
this._colHeadNode=this._colHeadFocusIdx=null;
return true;
},_navHeader:function(_33,_34,evt){
var _35=_34<0?-1:1,_36=dojo.indexOf(this._findHeaderCells(),this._colHeadNode);
if(_36>=0&&(evt.shiftKey&&evt.ctrlKey)){
this.colSizeAdjust(evt,_36,_35*5);
return;
}
this.move(_33,_34);
},_onHeaderKeyDown:function(e,_37){
if(_37){
var dk=dojo.keys;
switch(e.keyCode){
case dk.ENTER:
case dk.SPACE:
var _38=this.getHeaderIndex();
if(_38>=0&&!this.grid.pluginMgr.isFixedCell(e.cell)){
this.grid.setSortIndex(_38,null,e);
dojo.stopEvent(e);
}
break;
}
}
return true;
},_setActiveColHeader:function(){
this.inherited(arguments);
dijit.focus(this._colHeadNode);
},findAndFocusGridCell:function(){
this._focusContent();
},_focusContent:function(evt,_39){
var _3a=true;
var _3b=(this.grid.rowCount===0);
if(this.isNoFocusCell()&&!_3b){
for(var i=0,_3c=this.grid.getCell(0);_3c&&_3c.hidden;_3c=this.grid.getCell(++i)){
}
this.setFocusIndex(0,_3c?i:0);
}else{
if(this.cell&&!_3b){
if(this.focusView&&!this.focusView.rowNodes[this.rowIndex]){
this.grid.scrollToRow(this.rowIndex);
this.focusGrid();
}else{
this.setFocusIndex(this.rowIndex,this.cell.index);
}
}else{
_3a=false;
}
}
if(_3a){
this._stopEvent(evt);
}
return _3a;
},_blurContent:function(evt,_3d){
this._focusifyCellNode(false);
return true;
},_navContent:function(_3e,_3f,evt){
if((this.rowIndex===0&&_3e<0)||(this.rowIndex===this.grid.rowCount-1&&_3e>0)){
return;
}
this._colHeadNode=null;
this.move(_3e,_3f,evt);
if(evt){
dojo.stopEvent(evt);
}
},move:function(_40,_41){
this.inherited(arguments);
var _42=this.cell,row=this.rowIndex;
if(!this.isNavHeader()&&_42){
if(_40!==0){
var _43=_42.view.getRowNode(row);
if(_43&&dojo.style(_43,"display")==="none"){
this.move(_40>0?1:-1,_41);
}
}else{
if(_41!==0){
var _43=_42.getNode(row);
if(_43&&dojo.style(_43,"display")==="none"){
this.move(_40,_41>0?1:-1);
}
}
}
}
},_onContentKeyDown:function(e,_44){
if(_44){
var dk=dojo.keys,s=this.grid.scroller;
switch(e.keyCode){
case dk.ENTER:
case dk.SPACE:
var g=this.grid;
if(g.indirectSelection){
break;
}
g.selection.clickSelect(this.rowIndex,dojo.isCopyKey(e),e.shiftKey);
g.onRowClick(e);
dojo.stopEvent(e);
break;
case dk.PAGE_UP:
if(this.rowIndex!==0){
if(this.rowIndex!=s.firstVisibleRow+1){
this._navContent(s.firstVisibleRow-this.rowIndex,0);
}else{
this.grid.setScrollTop(s.findScrollTop(this.rowIndex-1));
this._navContent(s.firstVisibleRow-s.lastVisibleRow+1,0);
}
dojo.stopEvent(e);
}
break;
case dk.PAGE_DOWN:
if(this.rowIndex+1!=this.grid.rowCount){
dojo.stopEvent(e);
if(this.rowIndex!=s.lastVisibleRow-1){
this._navContent(s.lastVisibleRow-this.rowIndex-1,0);
}else{
this.grid.setScrollTop(s.findScrollTop(this.rowIndex+1));
this._navContent(s.lastVisibleRow-s.firstVisibleRow-1,0);
}
dojo.stopEvent(e);
}
break;
}
}
return true;
},_blurFromEditableCell:false,_isNavigating:false,_navElems:null,_focusEditableCell:function(evt,_45){
var _46=false;
if(this._isNavigating){
_46=true;
}else{
if(this.grid.edit.isEditing()&&this.cell){
if(this._blurFromEditableCell||!this._blurEditableCell(evt,_45)){
this.setFocusIndex(this.rowIndex,this.cell.index);
_46=true;
}
this._stopEvent(evt);
}
}
return _46;
},_applyEditableCell:function(){
try{
this.grid.edit.apply();
}
catch(e){
console.warn("_FocusManager._applyEditableCell() error:",e);
}
},_blurEditableCell:function(evt,_47){
this._blurFromEditableCell=false;
if(this._isNavigating){
var _48=true;
if(evt){
var _49=this._navElems;
var _4a=_49.lowest||_49.first;
var _4b=_49.last||_49.highest||_4a;
var _4c=dojo.isIE?evt.srcElement:evt.target;
_48=_4c==(_47>0?_4b:_4a);
}
if(_48){
this._isNavigating=false;
return "content";
}
return false;
}else{
if(this.grid.edit.isEditing()&&this.cell){
if(!_47||typeof _47!="number"){
return false;
}
var dir=_47>0?1:-1;
var cc=this.grid.layout.cellCount;
for(var _4d,col=this.cell.index+dir;col>=0&&col<cc;col+=dir){
_4d=this.grid.getCell(col);
if(_4d.editable){
this.cell=_4d;
this._blurFromEditableCell=true;
return false;
}
}
if((this.rowIndex>0||dir==1)&&(this.rowIndex<this.grid.rowCount||dir==-1)){
this.rowIndex+=dir;
for(col=dir>0?0:cc-1;col>=0&&col<cc;col+=dir){
_4d=this.grid.getCell(col);
if(_4d.editable){
this.cell=_4d;
break;
}
}
this._applyEditableCell();
return "content";
}
}
}
return true;
},_initNavigatableElems:function(){
this._navElems=dijit._getTabNavigable(this.cell.getNode(this.rowIndex));
},_onEditableCellKeyDown:function(e,_4e){
var dk=dojo.keys,g=this.grid,_4f=g.edit,_50=false,_51=true;
switch(e.keyCode){
case dk.ENTER:
if(_4e&&_4f.isEditing()){
this._applyEditableCell();
_50=true;
}
case dk.SPACE:
if(!_4e&&this._isNavigating){
_51=false;
break;
}
if(_4e){
if(!this.cell.editable&&this.cell.navigatable){
this._initNavigatableElems();
var _52=this._navElems.lowest||this._navElems.first;
if(_52){
this._isNavigating=true;
dijit.focus(_52);
dojo.stopEvent(e);
this.currentArea("editableCell",true);
break;
}
}
if(!_50&&!_4f.isEditing()&&!g.pluginMgr.isFixedCell(this.cell)){
_4f.setEditCell(this.cell,this.rowIndex);
}
if(_50){
this.currentArea("content",true);
}else{
if(this.cell.editable&&g.canEdit()){
this.currentArea("editableCell",true);
}
}
}
break;
case dk.PAGE_UP:
case dk.PAGE_DOWN:
if(!_4e&&_4f.isEditing()){
_51=false;
}
break;
case dk.ESCAPE:
if(!_4e){
_4f.cancel();
this.currentArea("content",true);
}
}
return _51;
},_onEditableCellMouseEvent:function(evt){
if(evt.type=="click"){
var _53=this.cell||evt.cell;
if(_53&&!_53.editable&&_53.navigatable){
this._initNavigatableElems();
if(this._navElems.lowest||this._navElems.first){
var _54=dojo.isIE?evt.srcElement:evt.target;
if(_54!=_53.getNode(evt.rowIndex)){
this._isNavigating=true;
this.focusArea("editableCell",evt);
dijit.focus(_54);
return false;
}
}
}else{
if(this.grid.singleClickEdit){
this.currentArea("editableCell");
return false;
}
}
}
return true;
}});
}
