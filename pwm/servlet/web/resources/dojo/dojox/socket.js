/*
	Copyright (c) 2004-2011, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.socket"]){
dojo._hasResource["dojox.socket"]=true;
dojo.provide("dojox.socket");
dojo.require("dojo.cookie");
var WebSocket=window.WebSocket;
dojox.socket=Socket;
function Socket(_1){
if(typeof _1=="string"){
_1={url:_1};
}
return WebSocket?dojox.socket.WebSocket(_1):dojox.socket.LongPoll(_1);
};
Socket.WebSocket=function(_2){
var ws=new WebSocket(new dojo._Url(document.baseURI.replace(/^http/i,"ws"),_2.url));
ws.on=ws.addEventListener;
var _3;
dojo.connect(ws,"onopen",function(_4){
_3=true;
});
dojo.connect(ws,"onclose",function(_5){
if(_3){
return;
}
WebSocket=null;
Socket.replace(ws,dojox.socket(_2),true);
});
return ws;
};
Socket.replace=function(_6,_7,_8){
_6.send=dojo.hitch(_7,"send");
_6.close=dojo.hitch(_7,"close");
if(_8){
_9("open");
}
dojo.forEach(["message","close","error"],_9);
function _9(_a){
(_7.addEventListener||_7.on).call(_7,_a,function(_b){
_6.dispatchEvent(_b);
});
};
};
Socket.LongPoll=function(_c){
var _d=false,_e=true,_f,_10=[];
var _11={send:function(_12){
var _13=dojo.delegate(_c);
_13.rawBody=_12;
clearTimeout(_f);
var _14=_e?(_e=false)||_11.firstRequest(_13):_11.transport(_13);
_10.push(_14);
_14.then(function(_15){
_11.readyState=1;
_10.splice(dojo.indexOf(_10,_14),1);
if(!_10.length){
_f=setTimeout(_1d,_c.interval);
}
if(_15){
_17("message",{data:_15},_14);
}
},function(_16){
_10.splice(dojo.indexOf(_10,_14),1);
if(!_d){
_17("error",{error:_16},_14);
if(!_10.length){
_11.readyState=3;
_17("close",{wasClean:false},_14);
}
}
});
return _14;
},close:function(){
_11.readyState=2;
_d=true;
for(var i=0;i<_10.length;i++){
_10[i].cancel();
}
_11.readyState=3;
_17("close",{wasClean:true});
},transport:_c.transport||dojo.xhrPost,args:_c,url:_c.url,readyState:0,CONNECTING:0,OPEN:1,CLOSING:2,CLOSED:3,dispatchEvent:function(_18){
_17(_18.type,_18);
},on:function(_19,_1a){
return dojo.connect(this,"on"+_19,_1a);
},firstRequest:function(_1b){
var _1c=(_1b.headers||(_1b.headers={}));
_1c.Pragma="start-long-poll";
try{
return this.transport(_1b);
}
finally{
delete _1c.Pragma;
}
}};
function _1d(){
if(_11.readyState==0){
_17("open",{});
}
if(!_10.length){
_11.send();
}
};
function _17(_1e,_1f,_20){
if(_11["on"+_1e]){
var _21=document.createEvent("HTMLEvents");
_21.initEvent(_1e);
dojo.mixin(_21,_1f);
_21.ioArgs=_20&&_20.ioArgs;
_11["on"+_1e](_21);
}
};
_11.connect=_11.on;
setTimeout(_1d);
return _11;
};
}
