/*
	Copyright (c) 2004-2011, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.grid.enhanced.plugins.NestedSorting"]){
dojo._hasResource["dojox.grid.enhanced.plugins.NestedSorting"]=true;
dojo.provide("dojox.grid.enhanced.plugins.NestedSorting");
dojo.require("dojox.grid.enhanced._Plugin");
dojo.declare("dojox.grid.enhanced.plugins.NestedSorting",dojox.grid.enhanced._Plugin,{name:"nestedSorting",_currMainSort:"none",_currRegionIdx:-1,_a11yText:{"dojoxGridDescending":"&#9662;","dojoxGridAscending":"&#9652;","dojoxGridAscendingTip":"&#1784;","dojoxGridDescendingTip":"&#1783;","dojoxGridUnsortedTip":"x"},constructor:function(){
this._sortDef=[];
this._sortData={};
this._headerNodes={};
this._excludedColIdx=[];
this.nls=this.grid._nls;
this.grid.setSortInfo=function(){
};
this.grid.setSortIndex=dojo.hitch(this,"_setGridSortIndex");
this.grid.getSortProps=dojo.hitch(this,"getSortProps");
if(this.grid.sortFields){
this._setGridSortIndex(this.grid.sortFields,null,true);
}
this.connect(this.grid.views,"render","_initSort");
this.initCookieHandler();
},onStartUp:function(){
this.inherited(arguments);
this.connect(this.grid,"onHeaderCellClick","_onHeaderCellClick");
this.connect(this.grid,"onHeaderCellMouseOver","_onHeaderCellMouseOver");
this.connect(this.grid,"onHeaderCellMouseOut","_onHeaderCellMouseOut");
},_setGridSortIndex:function(_1,_2,_3){
if(dojo.isArray(_1)){
var i,d,_4;
for(i=0;i<_1.length;i++){
d=_1[i];
_4=this.grid.getCellByField(d.attribute);
if(!_4){
console.warn("Invalid sorting option, column ",d.attribute," not found.");
return;
}
if(_4["nosort"]||!this.grid.canSort(_4.index,_4.field)){
console.warn("Invalid sorting option, column ",d.attribute," is unsortable.");
return;
}
}
this.clearSort();
dojo.forEach(_1,function(d,i){
_4=this.grid.getCellByField(d.attribute);
this.setSortData(_4.index,"index",i);
this.setSortData(_4.index,"order",d.descending?"desc":"asc");
},this);
}else{
if(!isNaN(_1)){
if(_2===undefined){
return;
}
this.setSortData(_1,"order",_2?"asc":"desc");
}else{
return;
}
}
this._updateSortDef();
if(!_3){
this.grid.sort();
}
},getSortProps:function(){
return this._sortDef.length?this._sortDef:null;
},_initSort:function(_5){
var g=this.grid,n=g.domNode,_6=this._sortDef.length;
dojo.toggleClass(n,"dojoxGridSorted",!!_6);
dojo.toggleClass(n,"dojoxGridSingleSorted",_6===1);
dojo.toggleClass(n,"dojoxGridNestSorted",_6>1);
if(_6>0){
this._currMainSort=this._sortDef[0].descending?"desc":"asc";
}
var _7,_8=this._excludedCoIdx=[];
this._headerNodes=dojo.query("th",g.viewsHeaderNode).forEach(function(n){
_7=parseInt(dojo.attr(n,"idx"),10);
if(dojo.style(n,"display")==="none"||g.layout.cells[_7]["nosort"]||(g.canSort&&!g.canSort(_7,g.layout.cells[_7]["field"]))){
_8.push(_7);
}
});
this._headerNodes.forEach(this._initHeaderNode,this);
this._initFocus();
if(_5){
this._focusHeader();
}
},_initHeaderNode:function(_9){
var _a=dojo.query(".dojoxGridSortNode",_9)[0];
if(_a){
dojo.toggleClass(_a,"dojoxGridSortNoWrap",true);
}
if(dojo.indexOf(this._excludedCoIdx,dojo.attr(_9,"idx"))>=0){
dojo.addClass(_9,"dojoxGridNoSort");
return;
}
if(!dojo.query(".dojoxGridSortBtn",_9).length){
this._connects=dojo.filter(this._connects,function(_b){
if(_b._sort){
dojo.disconnect(_b);
return false;
}
return true;
});
var n=dojo.create("a",{className:"dojoxGridSortBtn dojoxGridSortBtnNested",title:this.nls.nestedSort+" - "+this.nls.ascending,innerHTML:"1"},_9.firstChild,"last");
var h=this.connect(n,"onmousedown",dojo.stopEvent);
h._sort=true;
n=dojo.create("a",{className:"dojoxGridSortBtn dojoxGridSortBtnSingle",title:this.nls.singleSort+" - "+this.nls.ascending},_9.firstChild,"last");
h=this.connect(n,"onmousedown",dojo.stopEvent);
h._sort=true;
}else{
var a1=dojo.query(".dojoxGridSortBtnSingle",_9)[0];
var a2=dojo.query(".dojoxGridSortBtnNested",_9)[0];
a1.className="dojoxGridSortBtn dojoxGridSortBtnSingle";
a2.className="dojoxGridSortBtn dojoxGridSortBtnNested";
a2.innerHTML="1";
dojo.removeClass(_9,"dojoxGridCellShowIndex");
dojo.removeClass(_9.firstChild,"dojoxGridSortNodeSorted");
dojo.removeClass(_9.firstChild,"dojoxGridSortNodeAsc");
dojo.removeClass(_9.firstChild,"dojoxGridSortNodeDesc");
dojo.removeClass(_9.firstChild,"dojoxGridSortNodeMain");
dojo.removeClass(_9.firstChild,"dojoxGridSortNodeSub");
}
this._updateHeaderNodeUI(_9);
},_onHeaderCellClick:function(e){
this._focusRegion(e.target);
if(dojo.hasClass(e.target,"dojoxGridSortBtn")){
this._onSortBtnClick(e);
dojo.stopEvent(e);
this._focusRegion(this._getCurrentRegion());
}
},_onHeaderCellMouseOver:function(e){
if(!e.cell){
return;
}
if(this._sortDef.length>1){
return;
}
if(this._sortData[e.cellIndex]&&this._sortData[e.cellIndex].index===0){
return;
}
var p;
for(p in this._sortData){
if(this._sortData[p].index===0){
dojo.addClass(this._headerNodes[p],"dojoxGridCellShowIndex");
break;
}
}
if(!dojo.hasClass(dojo.body(),"dijit_a11y")){
return;
}
var i=e.cell.index,_c=e.cellNode;
var _d=dojo.query(".dojoxGridSortBtnSingle",_c)[0];
var _e=dojo.query(".dojoxGridSortBtnNested",_c)[0];
var _f="none";
if(dojo.hasClass(this.grid.domNode,"dojoxGridSingleSorted")){
_f="single";
}else{
if(dojo.hasClass(this.grid.domNode,"dojoxGridNestSorted")){
_f="nested";
}
}
var _10=dojo.attr(_e,"orderIndex");
if(_10===null||_10===undefined){
dojo.attr(_e,"orderIndex",_e.innerHTML);
_10=_e.innerHTML;
}
if(this.isAsc(i)){
_e.innerHTML=_10+this._a11yText.dojoxGridDescending;
}else{
if(this.isDesc(i)){
_e.innerHTML=_10+this._a11yText.dojoxGridUnsortedTip;
}else{
_e.innerHTML=_10+this._a11yText.dojoxGridAscending;
}
}
if(this._currMainSort==="none"){
_d.innerHTML=this._a11yText.dojoxGridAscending;
}else{
if(this._currMainSort==="asc"){
_d.innerHTML=this._a11yText.dojoxGridDescending;
}else{
if(this._currMainSort==="desc"){
_d.innerHTML=this._a11yText.dojoxGridUnsortedTip;
}
}
}
},_onHeaderCellMouseOut:function(e){
var p;
for(p in this._sortData){
if(this._sortData[p].index===0){
dojo.removeClass(this._headerNodes[p],"dojoxGridCellShowIndex");
break;
}
}
},_onSortBtnClick:function(e){
var _11=e.cell.index;
if(dojo.hasClass(e.target,"dojoxGridSortBtnSingle")){
this._prepareSingleSort(_11);
}else{
if(dojo.hasClass(e.target,"dojoxGridSortBtnNested")){
this._prepareNestedSort(_11);
}else{
return;
}
}
dojo.stopEvent(e);
this._doSort(_11);
},_doSort:function(_12){
if(!this._sortData[_12]||!this._sortData[_12].order){
this.setSortData(_12,"order","asc");
}else{
if(this.isAsc(_12)){
this.setSortData(_12,"order","desc");
}else{
if(this.isDesc(_12)){
this.removeSortData(_12);
}
}
}
this._updateSortDef();
this.grid.sort();
this._initSort(true);
},setSortData:function(_13,_14,_15){
var sd=this._sortData[_13];
if(!sd){
sd=this._sortData[_13]={};
}
sd[_14]=_15;
},removeSortData:function(_16){
var d=this._sortData,i=d[_16].index,p;
delete d[_16];
for(p in d){
if(d[p].index>i){
d[p].index--;
}
}
},_prepareSingleSort:function(_17){
var d=this._sortData,p;
for(p in d){
delete d[p];
}
this.setSortData(_17,"index",0);
this.setSortData(_17,"order",this._currMainSort==="none"?null:this._currMainSort);
if(!this._sortData[_17]||!this._sortData[_17].order){
this._currMainSort="asc";
}else{
if(this.isAsc(_17)){
this._currMainSort="desc";
}else{
if(this.isDesc(_17)){
this._currMainSort="none";
}
}
}
},_prepareNestedSort:function(_18){
var i=this._sortData[_18]?this._sortData[_18].index:null;
if(i===0||!!i){
return;
}
this.setSortData(_18,"index",this._sortDef.length);
},_updateSortDef:function(){
this._sortDef.length=0;
var d=this._sortData,p;
for(p in d){
this._sortDef[d[p].index]={attribute:this.grid.layout.cells[p].field,descending:d[p].order==="desc"};
}
},_updateHeaderNodeUI:function(_19){
var _1a=this._getCellByNode(_19);
var _1b=_1a.index;
var _1c=this._sortData[_1b];
var _1d=dojo.query(".dojoxGridSortNode",_19)[0];
var _1e=dojo.query(".dojoxGridSortBtnSingle",_19)[0];
var _1f=dojo.query(".dojoxGridSortBtnNested",_19)[0];
dojo.toggleClass(_1e,"dojoxGridSortBtnAsc",this._currMainSort==="asc");
dojo.toggleClass(_1e,"dojoxGridSortBtnDesc",this._currMainSort==="desc");
if(this._currMainSort==="asc"){
_1e.title=this.nls.singleSort+" - "+this.nls.descending;
}else{
if(this._currMainSort==="desc"){
_1e.title=this.nls.singleSort+" - "+this.nls.unsorted;
}else{
_1e.title=this.nls.singleSort+" - "+this.nls.ascending;
}
}
var _20=this;
function _21(){
var _22="Column "+(_1a.index+1)+" "+_1a.field;
var _23="none";
var _24="ascending";
if(_1c){
_23=_1c.order==="asc"?"ascending":"descending";
_24=_1c.order==="asc"?"descending":"none";
}
var _25=_22+" - is sorted by "+_23;
var _26=_22+" - is nested sorted by "+_23;
var _27=_22+" - choose to sort by "+_24;
var _28=_22+" - choose to nested sort by "+_24;
dijit.setWaiState(_1e,"label",_25);
dijit.setWaiState(_1f,"label",_26);
var _29=[_20.connect(_1e,"onmouseover",function(){
dijit.setWaiState(_1e,"label",_27);
}),_20.connect(_1e,"onmouseout",function(){
dijit.setWaiState(_1e,"label",_25);
}),_20.connect(_1f,"onmouseover",function(){
dijit.setWaiState(_1f,"label",_28);
}),_20.connect(_1f,"onmouseout",function(){
dijit.setWaiState(_1f,"label",_26);
})];
dojo.forEach(_29,function(_2a){
_2a._sort=true;
});
};
_21();
var _2b=dojo.hasClass(dojo.body(),"dijit_a11y");
if(!_1c){
_1f.innerHTML=this._sortDef.length+1;
return;
}
if(_1c.index||(_1c.index===0&&this._sortDef.length>1)){
_1f.innerHTML=_1c.index+1;
}
dojo.addClass(_1d,"dojoxGridSortNodeSorted");
if(this.isAsc(_1b)){
dojo.addClass(_1d,"dojoxGridSortNodeAsc");
_1f.title=this.nls.nestedSort+" - "+this.nls.descending;
if(_2b){
_1d.innerHTML=this._a11yText.dojoxGridAscendingTip;
}
}else{
if(this.isDesc(_1b)){
dojo.addClass(_1d,"dojoxGridSortNodeDesc");
_1f.title=this.nls.nestedSort+" - "+this.nls.unsorted;
if(_2b){
_1d.innerHTML=this._a11yText.dojoxGridDescendingTip;
}
}
}
dojo.addClass(_1d,(_1c.index===0?"dojoxGridSortNodeMain":"dojoxGridSortNodeSub"));
},isAsc:function(_2c){
return this._sortData[_2c].order==="asc";
},isDesc:function(_2d){
return this._sortData[_2d].order==="desc";
},_getCellByNode:function(_2e){
var i;
for(i=0;i<this._headerNodes.length;i++){
if(this._headerNodes[i]===_2e){
return this.grid.layout.cells[i];
}
}
return null;
},clearSort:function(){
this._sortData={};
this._sortDef.length=0;
},initCookieHandler:function(){
if(this.grid.addCookieHandler){
this.grid.addCookieHandler({name:"sortOrder",onLoad:dojo.hitch(this,"_loadNestedSortingProps"),onSave:dojo.hitch(this,"_saveNestedSortingProps")});
}
},_loadNestedSortingProps:function(_2f,_30){
this._setGridSortIndex(_2f);
},_saveNestedSortingProps:function(_31){
return this.getSortProps();
},_initFocus:function(){
var f=this.focus=this.grid.focus;
this._focusRegions=this._getRegions();
if(!this._headerArea){
var _32=this._headerArea=f.getArea("header");
_32.onFocus=f.focusHeader=dojo.hitch(this,"_focusHeader");
_32.onBlur=f.blurHeader=f._blurHeader=dojo.hitch(this,"_blurHeader");
_32.onMove=dojo.hitch(this,"_onMove");
_32.onKeyDown=dojo.hitch(this,"_onKeyDown");
_32._regions=[];
_32.getRegions=null;
this.connect(this.grid,"onBlur","_blurHeader");
}
},_focusHeader:function(evt){
if(this._currRegionIdx===-1){
this._onMove(0,1,null);
}else{
this._focusRegion(this._getCurrentRegion());
}
try{
dojo.stopEvent(evt);
}
catch(e){
}
return true;
},_blurHeader:function(evt){
this._blurRegion(this._getCurrentRegion());
return true;
},_onMove:function(_33,_34,evt){
var _35=this._currRegionIdx||0,_36=this._focusRegions;
var _37=_36[_35+_34];
if(!_37){
return;
}else{
if(dojo.style(_37,"display")==="none"||dojo.style(_37,"visibility")==="hidden"){
this._onMove(_33,_34+(_34>0?1:-1),evt);
return;
}
}
this._focusRegion(_37);
var _38=this._getRegionView(_37);
_38.scrollboxNode.scrollLeft=_38.headerNode.scrollLeft;
},_onKeyDown:function(e,_39){
if(_39){
switch(e.keyCode){
case dojo.keys.ENTER:
case dojo.keys.SPACE:
if(dojo.hasClass(e.target,"dojoxGridSortBtnSingle")||dojo.hasClass(e.target,"dojoxGridSortBtnNested")){
this._onSortBtnClick(e);
}
}
}
},_getRegionView:function(_3a){
var _3b=_3a;
while(_3b&&!dojo.hasClass(_3b,"dojoxGridHeader")){
_3b=_3b.parentNode;
}
if(_3b){
return dojo.filter(this.grid.views.views,function(_3c){
return _3c.headerNode===_3b;
})[0]||null;
}
return null;
},_getRegions:function(){
var _3d=[],_3e=this.grid.layout.cells;
this._headerNodes.forEach(function(n,i){
if(dojo.style(n,"display")==="none"){
return;
}
if(_3e[i]["isRowSelector"]){
_3d.push(n);
return;
}
dojo.query(".dojoxGridSortNode,.dojoxGridSortBtnNested,.dojoxGridSortBtnSingle",n).forEach(function(_3f){
dojo.attr(_3f,"tabindex",0);
_3d.push(_3f);
});
},this);
return _3d;
},_focusRegion:function(_40){
if(!_40){
return;
}
var _41=this._getCurrentRegion();
if(_41&&_40!==_41){
this._blurRegion(_41);
}
var _42=this._getRegionHeader(_40);
dojo.addClass(_42,"dojoxGridCellSortFocus");
if(dojo.hasClass(_40,"dojoxGridSortNode")){
dojo.addClass(_40,"dojoxGridSortNodeFocus");
}else{
if(dojo.hasClass(_40,"dojoxGridSortBtn")){
dojo.addClass(_40,"dojoxGridSortBtnFocus");
}
}
_40.focus();
this.focus.currentArea("header");
this._currRegionIdx=dojo.indexOf(this._focusRegions,_40);
},_blurRegion:function(_43){
if(!_43){
return;
}
var _44=this._getRegionHeader(_43);
dojo.removeClass(_44,"dojoxGridCellSortFocus");
if(dojo.hasClass(_43,"dojoxGridSortNode")){
dojo.removeClass(_43,"dojoxGridSortNodeFocus");
}else{
if(dojo.hasClass(_43,"dojoxGridSortBtn")){
dojo.removeClass(_43,"dojoxGridSortBtnFocus");
}
}
_43.blur();
},_getCurrentRegion:function(){
return this._focusRegions[this._currRegionIdx];
},_getRegionHeader:function(_45){
while(_45&&!dojo.hasClass(_45,"dojoxGridCell")){
_45=_45.parentNode;
}
return _45;
},destroy:function(){
this._sortDef=this._sortData=null;
this._headerNodes=this._focusRegions=null;
this.inherited(arguments);
}});
dojox.grid.EnhancedGrid.registerPlugin(dojox.grid.enhanced.plugins.NestedSorting);
}
