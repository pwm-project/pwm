/******/ (function(modules) { // webpackBootstrap
/******/ 	// The module cache
/******/ 	var installedModules = {};

/******/ 	// The require function
/******/ 	function __webpack_require__(moduleId) {

/******/ 		// Check if module is in cache
/******/ 		if(installedModules[moduleId])
/******/ 			return installedModules[moduleId].exports;

/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = installedModules[moduleId] = {
/******/ 			exports: {},
/******/ 			id: moduleId,
/******/ 			loaded: false
/******/ 		};

/******/ 		// Execute the module function
/******/ 		modules[moduleId].call(module.exports, module, module.exports, __webpack_require__);

/******/ 		// Flag the module as loaded
/******/ 		module.loaded = true;

/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}


/******/ 	// expose the modules object (__webpack_modules__)
/******/ 	__webpack_require__.m = modules;

/******/ 	// expose the module cache
/******/ 	__webpack_require__.c = installedModules;

/******/ 	// __webpack_public_path__
/******/ 	__webpack_require__.p = "";

/******/ 	// Load entry module and return exports
/******/ 	return __webpack_require__(0);
/******/ })
/************************************************************************/
/******/ ([
/* 0 */
/***/ function(module, exports, __webpack_require__) {

	"use strict";
	var angular_1 = __webpack_require__(1);
	var angular_ui_router_1 = __webpack_require__(2);
	var orgchart_module_1 = __webpack_require__(3);
	var peoplesearch_module_1 = __webpack_require__(7);
	var routes_1 = __webpack_require__(21);
	var people_service_dev_1 = __webpack_require__(19);
	angular_1.module('app', [
	    angular_ui_router_1.default,
	    orgchart_module_1.default,
	    peoplesearch_module_1.default
	])
	    .config(routes_1.default)
	    .service('PeopleService', people_service_dev_1.default);
	angular_1.bootstrap(document, ['app']);
	

/***/ },
/* 1 */
/***/ function(module, exports) {

	module.exports = angular;

/***/ },
/* 2 */
/***/ function(module, exports) {

	module.exports = window["angular-ui-router"];

/***/ },
/* 3 */
/***/ function(module, exports, __webpack_require__) {

	"use strict";
	var angular_1 = __webpack_require__(1);
	var orgchart_component_1 = __webpack_require__(4);
	var moduleName = 'org-chart';
	angular_1.module(moduleName, [])
	    .component('orgChart', orgchart_component_1.default);
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = moduleName;
	

/***/ },
/* 4 */
/***/ function(module, exports, __webpack_require__) {

	"use strict";
	var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
	    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
	    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
	    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
	    return c > 3 && r && Object.defineProperty(target, key, r), r;
	};
	var component_1 = __webpack_require__(5);
	var templateUrl = __webpack_require__(6);
	var OrgChartComponent = (function () {
	    function OrgChartComponent($state, $stateParams, peopleService) {
	        this.$state = $state;
	        this.$stateParams = $stateParams;
	        this.peopleService = peopleService;
	    }
	    OrgChartComponent.prototype.$onInit = function () {
	        var personId = this.$stateParams['personId'];
	    };
	    OrgChartComponent.prototype.close = function () {
	        this.$state.go('search.table');
	    };
	    OrgChartComponent.$inject = ['$state', '$stateParams', 'PeopleService'];
	    OrgChartComponent = __decorate([
	        component_1.Component({
	            templateUrl: templateUrl
	        })
	    ], OrgChartComponent);
	    return OrgChartComponent;
	}());
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = OrgChartComponent;
	

/***/ },
/* 5 */
/***/ function(module, exports, __webpack_require__) {

	"use strict";
	var angular = __webpack_require__(1);
	function Component(options) {
	    return function (controller) { return angular.extend(options, { controller: controller }); };
	}
	exports.Component = Component;
	

/***/ },
/* 6 */
/***/ function(module, exports) {

	var path = 'orgchart/orgchart.component.html';
	var html = "<div id=\"page-content-title\">Organization</div>\n<div id=\"orgchart-close\" ng-click=\"$ctrl.close()\"></div>\n\n<div id=\"orgchart-content\" class=\"ng-cloak\">\n    <div class=\"orgchart-primary-person-connector\" class=\"ng-cloak\"></div>\n\n    <div class=\"orgchart-management-title orgchart-title\">Management</div>\n    <div class=\"orgchart-management\" ng-if=\"$ctrl.managementChain.length\" >\n        <div class=\"orgchart-manager\" ng-repeat=\"manager in $ctrl.managementChain\">\n            <div class=\"orgchart-separator\" ng-class=\"{first:$first,last:$last}\"></div>\n            <div class=\"orgchart-picture\">\n                <img ng-src=\"{{ manager.photoUrl }}\" />\n            </div>\n            <div class=\"orgchart-manager-fields\">\n                <div class=\"orgchart-field orgchart-field-0\">{{ manager.fields[0].value }}</div>\n                <div class=\"orgchart-field orgchart-field-1\">{{ manager.fields[1].value }}</div>\n            </div>\n        </div>\n    </div>\n\n    <div class=\"orgchart-primary-person\">\n        <img class=\"orgchart-picture\" ng-src=\"{{ $ctrl.person.photoUrl }}\" />\n        <div class=\"orgchart-num-reports\" ng-if=\"$ctrl.directReports.length\">{{ $ctrl.directReports.length }}</div>\n\n        <div class=\"orgchart-primary-field orgchart-field-0\">{{ $ctrl.person.fields[0].value }}</div>\n        <div class=\"orgchart-primary-field orgchart-field orgchart-field-1\">{{ $ctrl.person.fields[1].value }}</div>\n        <div class=\"orgchart-primary-field orgchart-field orgchart-field-2\">{{ $ctrl.person.fields[2].value }}</div>\n        <div class=\"orgchart-primary-field orgchart-field orgchart-field-3\">{{ $ctrl.person.fields[3].value }}</div>\n        <div class=\"orgchart-primary-field orgchart-field orgchart-field-4\">{{ $ctrl.person.fields[4].value }}</div>\n        <div class=\"orgchart-primary-field orgchart-field orgchart-field-5\">{{ $ctrl.person.fields[5].value }}</div>\n        <div class=\"orgchart-primary-field orgchart-field orgchart-field-6\">{{ $ctrl.person.fields[6].value }}</div>\n    </div>\n\n    <div class=\"orgchart-direct-reports-title orgchart-title\">Direct Reports</div>\n    <div class=\"orgchart-direct-reports person-card-list\" ng-if=\"$ctrl.directReports.length\">\n        <div class=\"person-card\" ng-repeat=\"directReport in $ctrl.directReports\">\n            <div class=\"person-card-image\"><img ng-src=\"{{ directReport.photoUrl }}\" /></div>\n            <div class=\"orgchart-num-reports\" ng-if=\"directReport.numOfReports\">{{ directReport.numOfReports }}</div>\n            <div class=\"person-card-details\">\n                <div class=\"person-card-row-1\">{{ directReport.fields[0].value }}</div>\n                <div class=\"person-card-row-2\">{{ directReport.fields[1].value }}</div>\n                <div class=\"person-card-row-3\">{{ directReport.fields[2].value }}</div>\n                <div class=\"person-card-row-4\">{{ directReport.fields[3].value }}</div>\n            </div>\n        </div>\n    </div>\n</div>\n";
	window.angular.module('ng').run(['$templateCache', function(c) { c.put(path, html) }]);
	module.exports = path;

/***/ },
/* 7 */
/***/ function(module, exports, __webpack_require__) {

	"use strict";
	var angular_1 = __webpack_require__(1);
	var peoplesearch_service_1 = __webpack_require__(8);
	var peoplesearch_component_1 = __webpack_require__(9);
	var peoplesearch_table_component_1 = __webpack_require__(15);
	var peoplesearch_cards_component_1 = __webpack_require__(17);
	var moduleName = 'people-search';
	angular_1.module(moduleName, [])
	    .service('peopleSearchService', peoplesearch_service_1.default)
	    .component('peopleSearch', peoplesearch_component_1.default)
	    .component('peopleSearchTable', peoplesearch_table_component_1.default)
	    .component('peopleSearchCards', peoplesearch_cards_component_1.default);
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = moduleName;
	

/***/ },
/* 8 */
/***/ function(module, exports) {

	"use strict";
	var PeopleSearchService = (function () {
	    function PeopleSearchService($rootScope, $timeout) {
	        this.$rootScope = $rootScope;
	        this.$timeout = $timeout;
	    }
	    PeopleSearchService.$inject = ['$rootScope', '$timeout'];
	    return PeopleSearchService;
	}());
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = PeopleSearchService;
	

/***/ },
/* 9 */
/***/ function(module, exports, __webpack_require__) {

	"use strict";
	var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
	    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
	    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
	    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
	    return c > 3 && r && Object.defineProperty(target, key, r), r;
	};
	var component_1 = __webpack_require__(5);
	var templateUrl = __webpack_require__(10);
	var stylesheetUrl = __webpack_require__(11);
	var PeopleSearchComponent = (function () {
	    function PeopleSearchComponent($state) {
	        this.$state = $state;
	    }
	    PeopleSearchComponent.prototype.$onInit = function () {
	        if (this.$state.is('search.table')) {
	            this.viewToggleClass = 'fa fa-th-large';
	        }
	        else {
	            this.viewToggleClass = 'fa fa-list-alt';
	        }
	    };
	    PeopleSearchComponent.prototype.viewToggleClicked = function () {
	        if (this.$state.is('search.table')) {
	            this.$state.go('search.cards');
	            this.viewToggleClass = 'fa fa-list-alt';
	        }
	        else {
	            this.$state.go('search.table');
	            this.viewToggleClass = 'fa fa-th-large';
	        }
	    };
	    PeopleSearchComponent.$inject = ['$state'];
	    PeopleSearchComponent = __decorate([
	        component_1.Component({
	            templateUrl: templateUrl,
	            stylesheetUrl: stylesheetUrl
	        })
	    ], PeopleSearchComponent);
	    return PeopleSearchComponent;
	}());
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = PeopleSearchComponent;
	

/***/ },
/* 10 */
/***/ function(module, exports) {

	var path = 'peoplesearch/peoplesearch.component.html';
	var html = "<div id=\"page-content-title\">People Search</div>\n\n<div id=\"panel-searchbar\" class=\"searchbar\">\n    <input id=\"username\" name=\"username\" placeholder=\"Search\" class=\"peoplesearch-input-username\" autocomplete=\"off\" /> <!-- Auto focus this control -->\n    <div class=\"searchbar-extras\">\n        <div id=\"searchIndicator\" style=\"display: none\">\n            <span style=\"\" class=\"pwm-icon pwm-icon-lg pwm-icon-spin pwm-icon-spinner\"></span>\n        </div>\n\n        <div id=\"maxResultsIndicator\" style=\"display: none;\">\n            <span style=\"color: #ffcd59;\" class=\"pwm-icon pwm-icon-lg pwm-icon-exclamation-circle\"></span>\n        </div>\n    </div>\n</div>\n\n<div id=\"people-search-view-toggle\" ng-click=\"$ctrl.viewToggleClicked()\" ng-class=\"$ctrl.viewToggleClass\"></div>\n\n<ui-view id=\"people-search-component-view\">Loading...</ui-view>\n";
	window.angular.module('ng').run(['$templateCache', function(c) { c.put(path, html) }]);
	module.exports = path;

/***/ },
/* 11 */
/***/ function(module, exports, __webpack_require__) {

	// style-loader: Adds some css to the DOM by adding a <style> tag

	// load the styles
	var content = __webpack_require__(12);
	if(typeof content === 'string') content = [[module.id, content, '']];
	// add the styles to the DOM
	var update = __webpack_require__(14)(content, {});
	if(content.locals) module.exports = content.locals;
	// Hot Module Replacement
	if(false) {
		// When the styles change, update the <style> tags
		if(!content.locals) {
			module.hot.accept("!!./../../node_modules/css-loader/index.js!./../../node_modules/sass-loader/index.js!./peoplesearch.component.scss", function() {
				var newContent = require("!!./../../node_modules/css-loader/index.js!./../../node_modules/sass-loader/index.js!./peoplesearch.component.scss");
				if(typeof newContent === 'string') newContent = [[module.id, newContent, '']];
				update(newContent);
			});
		}
		// When the module is disposed, remove the <style> tags
		module.hot.dispose(function() { update(); });
	}

/***/ },
/* 12 */
/***/ function(module, exports, __webpack_require__) {

	exports = module.exports = __webpack_require__(13)();
	// imports


	// module
	exports.push([module.id, "", ""]);

	// exports


/***/ },
/* 13 */
/***/ function(module, exports) {

	/*
		MIT License http://www.opensource.org/licenses/mit-license.php
		Author Tobias Koppers @sokra
	*/
	// css base code, injected by the css-loader
	module.exports = function() {
		var list = [];

		// return the list of modules as css string
		list.toString = function toString() {
			var result = [];
			for(var i = 0; i < this.length; i++) {
				var item = this[i];
				if(item[2]) {
					result.push("@media " + item[2] + "{" + item[1] + "}");
				} else {
					result.push(item[1]);
				}
			}
			return result.join("");
		};

		// import a list of modules into the list
		list.i = function(modules, mediaQuery) {
			if(typeof modules === "string")
				modules = [[null, modules, ""]];
			var alreadyImportedModules = {};
			for(var i = 0; i < this.length; i++) {
				var id = this[i][0];
				if(typeof id === "number")
					alreadyImportedModules[id] = true;
			}
			for(i = 0; i < modules.length; i++) {
				var item = modules[i];
				// skip already imported module
				// this implementation is not 100% perfect for weird media query combinations
				//  when a module is imported multiple times with different media queries.
				//  I hope this will never occur (Hey this way we have smaller bundles)
				if(typeof item[0] !== "number" || !alreadyImportedModules[item[0]]) {
					if(mediaQuery && !item[2]) {
						item[2] = mediaQuery;
					} else if(mediaQuery) {
						item[2] = "(" + item[2] + ") and (" + mediaQuery + ")";
					}
					list.push(item);
				}
			}
		};
		return list;
	};


/***/ },
/* 14 */
/***/ function(module, exports, __webpack_require__) {

	/*
		MIT License http://www.opensource.org/licenses/mit-license.php
		Author Tobias Koppers @sokra
	*/
	var stylesInDom = {},
		memoize = function(fn) {
			var memo;
			return function () {
				if (typeof memo === "undefined") memo = fn.apply(this, arguments);
				return memo;
			};
		},
		isOldIE = memoize(function() {
			return /msie [6-9]\b/.test(window.navigator.userAgent.toLowerCase());
		}),
		getHeadElement = memoize(function () {
			return document.head || document.getElementsByTagName("head")[0];
		}),
		singletonElement = null,
		singletonCounter = 0,
		styleElementsInsertedAtTop = [];

	module.exports = function(list, options) {
		if(false) {
			if(typeof document !== "object") throw new Error("The style-loader cannot be used in a non-browser environment");
		}

		options = options || {};
		// Force single-tag solution on IE6-9, which has a hard limit on the # of <style>
		// tags it will allow on a page
		if (typeof options.singleton === "undefined") options.singleton = isOldIE();

		// By default, add <style> tags to the bottom of <head>.
		if (typeof options.insertAt === "undefined") options.insertAt = "bottom";

		var styles = listToStyles(list);
		addStylesToDom(styles, options);

		return function update(newList) {
			var mayRemove = [];
			for(var i = 0; i < styles.length; i++) {
				var item = styles[i];
				var domStyle = stylesInDom[item.id];
				domStyle.refs--;
				mayRemove.push(domStyle);
			}
			if(newList) {
				var newStyles = listToStyles(newList);
				addStylesToDom(newStyles, options);
			}
			for(var i = 0; i < mayRemove.length; i++) {
				var domStyle = mayRemove[i];
				if(domStyle.refs === 0) {
					for(var j = 0; j < domStyle.parts.length; j++)
						domStyle.parts[j]();
					delete stylesInDom[domStyle.id];
				}
			}
		};
	}

	function addStylesToDom(styles, options) {
		for(var i = 0; i < styles.length; i++) {
			var item = styles[i];
			var domStyle = stylesInDom[item.id];
			if(domStyle) {
				domStyle.refs++;
				for(var j = 0; j < domStyle.parts.length; j++) {
					domStyle.parts[j](item.parts[j]);
				}
				for(; j < item.parts.length; j++) {
					domStyle.parts.push(addStyle(item.parts[j], options));
				}
			} else {
				var parts = [];
				for(var j = 0; j < item.parts.length; j++) {
					parts.push(addStyle(item.parts[j], options));
				}
				stylesInDom[item.id] = {id: item.id, refs: 1, parts: parts};
			}
		}
	}

	function listToStyles(list) {
		var styles = [];
		var newStyles = {};
		for(var i = 0; i < list.length; i++) {
			var item = list[i];
			var id = item[0];
			var css = item[1];
			var media = item[2];
			var sourceMap = item[3];
			var part = {css: css, media: media, sourceMap: sourceMap};
			if(!newStyles[id])
				styles.push(newStyles[id] = {id: id, parts: [part]});
			else
				newStyles[id].parts.push(part);
		}
		return styles;
	}

	function insertStyleElement(options, styleElement) {
		var head = getHeadElement();
		var lastStyleElementInsertedAtTop = styleElementsInsertedAtTop[styleElementsInsertedAtTop.length - 1];
		if (options.insertAt === "top") {
			if(!lastStyleElementInsertedAtTop) {
				head.insertBefore(styleElement, head.firstChild);
			} else if(lastStyleElementInsertedAtTop.nextSibling) {
				head.insertBefore(styleElement, lastStyleElementInsertedAtTop.nextSibling);
			} else {
				head.appendChild(styleElement);
			}
			styleElementsInsertedAtTop.push(styleElement);
		} else if (options.insertAt === "bottom") {
			head.appendChild(styleElement);
		} else {
			throw new Error("Invalid value for parameter 'insertAt'. Must be 'top' or 'bottom'.");
		}
	}

	function removeStyleElement(styleElement) {
		styleElement.parentNode.removeChild(styleElement);
		var idx = styleElementsInsertedAtTop.indexOf(styleElement);
		if(idx >= 0) {
			styleElementsInsertedAtTop.splice(idx, 1);
		}
	}

	function createStyleElement(options) {
		var styleElement = document.createElement("style");
		styleElement.type = "text/css";
		insertStyleElement(options, styleElement);
		return styleElement;
	}

	function createLinkElement(options) {
		var linkElement = document.createElement("link");
		linkElement.rel = "stylesheet";
		insertStyleElement(options, linkElement);
		return linkElement;
	}

	function addStyle(obj, options) {
		var styleElement, update, remove;

		if (options.singleton) {
			var styleIndex = singletonCounter++;
			styleElement = singletonElement || (singletonElement = createStyleElement(options));
			update = applyToSingletonTag.bind(null, styleElement, styleIndex, false);
			remove = applyToSingletonTag.bind(null, styleElement, styleIndex, true);
		} else if(obj.sourceMap &&
			typeof URL === "function" &&
			typeof URL.createObjectURL === "function" &&
			typeof URL.revokeObjectURL === "function" &&
			typeof Blob === "function" &&
			typeof btoa === "function") {
			styleElement = createLinkElement(options);
			update = updateLink.bind(null, styleElement);
			remove = function() {
				removeStyleElement(styleElement);
				if(styleElement.href)
					URL.revokeObjectURL(styleElement.href);
			};
		} else {
			styleElement = createStyleElement(options);
			update = applyToTag.bind(null, styleElement);
			remove = function() {
				removeStyleElement(styleElement);
			};
		}

		update(obj);

		return function updateStyle(newObj) {
			if(newObj) {
				if(newObj.css === obj.css && newObj.media === obj.media && newObj.sourceMap === obj.sourceMap)
					return;
				update(obj = newObj);
			} else {
				remove();
			}
		};
	}

	var replaceText = (function () {
		var textStore = [];

		return function (index, replacement) {
			textStore[index] = replacement;
			return textStore.filter(Boolean).join('\n');
		};
	})();

	function applyToSingletonTag(styleElement, index, remove, obj) {
		var css = remove ? "" : obj.css;

		if (styleElement.styleSheet) {
			styleElement.styleSheet.cssText = replaceText(index, css);
		} else {
			var cssNode = document.createTextNode(css);
			var childNodes = styleElement.childNodes;
			if (childNodes[index]) styleElement.removeChild(childNodes[index]);
			if (childNodes.length) {
				styleElement.insertBefore(cssNode, childNodes[index]);
			} else {
				styleElement.appendChild(cssNode);
			}
		}
	}

	function applyToTag(styleElement, obj) {
		var css = obj.css;
		var media = obj.media;

		if(media) {
			styleElement.setAttribute("media", media)
		}

		if(styleElement.styleSheet) {
			styleElement.styleSheet.cssText = css;
		} else {
			while(styleElement.firstChild) {
				styleElement.removeChild(styleElement.firstChild);
			}
			styleElement.appendChild(document.createTextNode(css));
		}
	}

	function updateLink(linkElement, obj) {
		var css = obj.css;
		var sourceMap = obj.sourceMap;

		if(sourceMap) {
			// http://stackoverflow.com/a/26603875
			css += "\n/*# sourceMappingURL=data:application/json;base64," + btoa(unescape(encodeURIComponent(JSON.stringify(sourceMap)))) + " */";
		}

		var blob = new Blob([css], { type: "text/css" });

		var oldSrc = linkElement.href;

		linkElement.href = URL.createObjectURL(blob);

		if(oldSrc)
			URL.revokeObjectURL(oldSrc);
	}


/***/ },
/* 15 */
/***/ function(module, exports, __webpack_require__) {

	"use strict";
	var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
	    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
	    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
	    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
	    return c > 3 && r && Object.defineProperty(target, key, r), r;
	};
	var component_1 = __webpack_require__(5);
	var templateUrl = __webpack_require__(16);
	var PeopleSearchTableComponent = (function () {
	    function PeopleSearchTableComponent($scope, peopleSearchService) {
	        this.$scope = $scope;
	        this.peopleSearchService = peopleSearchService;
	    }
	    PeopleSearchTableComponent.prototype.$onInit = function () {
	    };
	    PeopleSearchTableComponent.$inject = ['$scope', 'peopleSearchService'];
	    PeopleSearchTableComponent = __decorate([
	        component_1.Component({
	            templateUrl: templateUrl
	        })
	    ], PeopleSearchTableComponent);
	    return PeopleSearchTableComponent;
	}());
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = PeopleSearchTableComponent;
	

/***/ },
/* 16 */
/***/ function(module, exports) {

	var path = 'peoplesearch/peoplesearch-table.component.html';
	var html = "<table st-table=\"rowCollection\" class=\"table table-striped\">\n    <thead>\n    <tr>\n        <th>First Name</th>\n        <th>Last Name</th>\n        <th>Title</th>\n        <th>Email</th>\n        <th>Telephone</th>\n    </tr>\n    </thead>\n\n    <tbody>\n    <tr ng-repeat=\"person in $ctrl.data\" ng-click=\"$ctrl.selectPerson(person.id)\">\n        <td>{{ person.givenName }}</td>\n        <td>{{ person.sn }}</td>\n        <td>{{ person.title }}</td>\n        <td>{{ person.mail }}</td>\n        <td>{{ person.telephoneNumber }}</td>\n    </tr>\n    </tbody>\n</table>\n";
	window.angular.module('ng').run(['$templateCache', function(c) { c.put(path, html) }]);
	module.exports = path;

/***/ },
/* 17 */
/***/ function(module, exports, __webpack_require__) {

	"use strict";
	var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
	    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
	    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
	    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
	    return c > 3 && r && Object.defineProperty(target, key, r), r;
	};
	var component_1 = __webpack_require__(5);
	var templateUrl = __webpack_require__(18);
	var PeopleSearchCardsComponent = (function () {
	    function PeopleSearchCardsComponent($scope, peopleSearchService) {
	        this.$scope = $scope;
	        this.peopleSearchService = peopleSearchService;
	    }
	    PeopleSearchCardsComponent.prototype.$onInit = function () {
	    };
	    PeopleSearchCardsComponent.$inject = ['$scope', 'peopleSearchService'];
	    PeopleSearchCardsComponent = __decorate([
	        component_1.Component({
	            templateUrl: templateUrl
	        })
	    ], PeopleSearchCardsComponent);
	    return PeopleSearchCardsComponent;
	}());
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = PeopleSearchCardsComponent;
	

/***/ },
/* 18 */
/***/ function(module, exports) {

	var path = 'peoplesearch/peoplesearch-cards.component.html';
	var html = "<div class=\"person-card-list\">\n    <div class=\"person-card\" ng-repeat=\"person in $ctrl.people\" ng-click=\"$ctrl.selectPerson(person.id)\">\n        <div class=\"person-card-image\"></div>\n        <div class=\"person-card-details\">\n            <div class=\"person-card-row-1\">{{ person.givenName }} {{ person.sn }}</div>\n            <div class=\"person-card-row-2\">{{ person.title }}</div>\n            <div class=\"person-card-row-3\">{{ person.telephoneNumber }}</div>\n            <div class=\"person-card-row-4\">{{ person.mail }}</div>\n        </div>\n    </div>\n</div>\n";
	window.angular.module('ng').run(['$templateCache', function(c) { c.put(path, html) }]);
	module.exports = path;

/***/ },
/* 19 */
/***/ function(module, exports, __webpack_require__) {

	"use strict";
	var person_model_1 = __webpack_require__(22);
	var peopleData = __webpack_require__(20);
	var PeopleService = (function () {
	    function PeopleService($q, $timeout) {
	        this.$q = $q;
	        this.$timeout = $timeout;
	        this.people = peopleData.map(function (person) { return new person_model_1.default(person); });
	    }
	    PeopleService.prototype.getOrgChartDataForUser = function (id) {
	        return null;
	    };
	    PeopleService.prototype.getUserData = function (id) {
	        var _this = this;
	        var deferred = this.$q.defer();
	        this.$timeout(function () {
	            var people = _this.people.filter(function (person) { return person.id == id; });
	            if (people.length) {
	                deferred.resolve(people[0]);
	            }
	            else {
	                deferred.reject("Person with id: \"" + id + "\" not found.");
	            }
	        });
	        return deferred.promise;
	    };
	    PeopleService.$inject = ['$q', '$timeout'];
	    return PeopleService;
	}());
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = PeopleService;
	

/***/ },
/* 20 */
/***/ function(module, exports) {

	module.exports = [
		{
			"id": 1,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"name": "Name",
					"value": "Andy Smith"
				},
				{
					"fieldId": "phone",
					"name": "Phone",
					"type": "phone",
					"value": "(123) 456-7890"
				},
				{
					"fieldId": "email",
					"name": "Email",
					"type": "email",
					"value": "andy.smith@lapd.gov"
				},
				{
					"fieldId": "title",
					"name": "Title",
					"value": "ASST. C/O"
				},
				{
					"fieldId": "department",
					"name": "Department",
					"value": "Office of Operations"
				},
				{
					"fieldId": "manager",
					"name": "Manager",
					"value": "Debra McCarthy",
					"type": "person-link",
					"typeMetaData": {
						"id": 3
					}
				}
			],
			"managementChain": [
				{
					"id": 3,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "Debra McCarthy",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Deputy Chief"
						}
					]
				},
				{
					"id": 4,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "Earl Paysinger",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Assistant Chief"
						}
					]
				},
				{
					"id": 5,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "Charlie Beck",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Chief of Police"
						}
					]
				},
				{
					"id": 6,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "John W. Mack",
							"type": "person-link"
						},
						{
							"fieldId": "Commissioner",
							"value": "CFO"
						}
					]
				},
				{
					"id": 7,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "R. Tefank",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Executive Director"
						}
					]
				}
			],
			"directReports": [
				{
					"id": 8,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"numOfReports": 3,
					"fields": [
						{
							"fieldId": "name",
							"value": "Elmer Davis",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain III"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x001",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "elmer.davis@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 9,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "William Hart",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain I"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x002",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "william.hart@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 10,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"numOfReports": 4,
					"fields": [
						{
							"fieldId": "name",
							"value": "Bob Ginmala",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain III"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x003",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "bob.ginmala@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 11,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"numOfReports": 8,
					"fields": [
						{
							"fieldId": "name",
							"value": "Peter Wittingham",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain I"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x004",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "peter.wittingham@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 12,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "Elena Nathan",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain III"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x005",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "elena.nathan@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 13,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "Rolphe DeLaTorre",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain I"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x006",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "rolphe.delatorre@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 14,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"numOfReports": 19,
					"fields": [
						{
							"fieldId": "name",
							"value": "Mike Blake",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain III"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x007",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "mike.blake@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 15,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"numOfReports": 1,
					"fields": [
						{
							"fieldId": "name",
							"value": "Braeden Crump",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain I"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x007",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "braeden.crump@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 16,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "Jeremy Peters",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain III"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x007",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "jeremy.peters@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 17,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"numOfReports": 7,
					"fields": [
						{
							"fieldId": "name",
							"value": "Jeff West",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain I"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x007",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "jeff.west@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 18,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"fields": [
						{
							"fieldId": "name",
							"value": "Paul Fontanetta",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain II"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x007",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "paul.fontanetta@lapd.gov",
							"type": "email"
						}
					]
				},
				{
					"id": 19,
					"photoUrl": "/pwm/public/resources/UserPhoto.png",
					"numOfReports": 13,
					"fields": [
						{
							"fieldId": "name",
							"value": "Nadine Lauer",
							"type": "person-link"
						},
						{
							"fieldId": "title",
							"value": "Captain II"
						},
						{
							"fieldId": "phone",
							"value": "(123) 456-7890 x007",
							"type": "phone"
						},
						{
							"fieldId": "email",
							"value": "nadine.lauer@lapd.gov",
							"type": "email"
						}
					]
				}
			]
		},
		{
			"id": 3,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "Debra McCarthy",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Deputy Chief"
				}
			]
		},
		{
			"id": 4,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "Earl Paysinger",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Assistant Chief"
				}
			]
		},
		{
			"id": 5,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "Charlie Beck",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Chief of Police"
				}
			]
		},
		{
			"id": 6,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "John W. Mack",
					"type": "person-link"
				},
				{
					"fieldId": "Commissioner",
					"value": "CFO"
				}
			]
		},
		{
			"id": 7,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "R. Tefank",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Executive Director"
				}
			]
		},
		{
			"id": 8,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"numOfReports": 3,
			"fields": [
				{
					"fieldId": "name",
					"value": "Elmer Davis",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain III"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x001",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "elmer.davis@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 9,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "William Hart",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain I"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x002",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "william.hart@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 10,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"numOfReports": 4,
			"fields": [
				{
					"fieldId": "name",
					"value": "Bob Ginmala",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain III"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x003",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "bob.ginmala@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 11,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"numOfReports": 8,
			"fields": [
				{
					"fieldId": "name",
					"value": "Peter Wittingham",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain I"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x004",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "peter.wittingham@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 12,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "Elena Nathan",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain III"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x005",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "elena.nathan@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 13,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "Rolphe DeLaTorre",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain I"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x006",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "rolphe.delatorre@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 14,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"numOfReports": 19,
			"fields": [
				{
					"fieldId": "name",
					"value": "Mike Blake",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain III"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x007",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "mike.blake@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 15,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"numOfReports": 1,
			"fields": [
				{
					"fieldId": "name",
					"value": "Braeden Crump",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain I"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x007",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "braeden.crump@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 16,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "Jeremy Peters",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain III"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x007",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "jeremy.peters@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 17,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"numOfReports": 7,
			"fields": [
				{
					"fieldId": "name",
					"value": "Jeff West",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain I"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x007",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "jeff.west@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 18,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"fields": [
				{
					"fieldId": "name",
					"value": "Paul Fontanetta",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain II"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x007",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "paul.fontanetta@lapd.gov",
					"type": "email"
				}
			]
		},
		{
			"id": 19,
			"photoUrl": "/pwm/public/resources/UserPhoto.png",
			"numOfReports": 13,
			"fields": [
				{
					"fieldId": "name",
					"value": "Nadine Lauer",
					"type": "person-link"
				},
				{
					"fieldId": "title",
					"value": "Captain II"
				},
				{
					"fieldId": "phone",
					"value": "(123) 456-7890 x007",
					"type": "phone"
				},
				{
					"fieldId": "email",
					"value": "nadine.lauer@lapd.gov",
					"type": "email"
				}
			]
		}
	];

/***/ },
/* 21 */
/***/ function(module, exports) {

	"use strict";
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = [
	    '$stateProvider',
	    '$urlRouterProvider',
	    function ($stateProvider, $urlRouterProvider) {
	        $urlRouterProvider.otherwise('/search/table');
	        $stateProvider.state({ name: 'search', url: '/search', component: 'peopleSearch' });
	        $stateProvider.state({ name: 'search.table', url: '/table', component: 'peopleSearchTable' });
	        $stateProvider.state({ name: 'search.cards', url: '/cards', component: 'peopleSearchCards' });
	        $stateProvider.state({ name: 'orgchart', url: '/orgchart/{personId}', component: 'orgChart' });
	    }];
	

/***/ },
/* 22 */
/***/ function(module, exports) {

	"use strict";
	var Person = (function () {
	    function Person(options) {
	        this.directReports = options.directReports;
	        this.fields = options.fields;
	        this.givenName = options.givenName;
	        this.id = options.id;
	        this.mail = options.mail;
	        this.managementChain = options.managementChain;
	        this.numOfReports = options.numOfReports;
	        this.orgChartParentKey = options.orgChartParentKey;
	        this.photoUrl = options.photoUrl;
	        this.sn = options.sn;
	        this.telephoneNumber = options.telephoneNumber;
	        this.title = options.title;
	    }
	    return Person;
	}());
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.default = Person;
	

/***/ }
/******/ ]);
//# sourceMappingURL=app.js.map