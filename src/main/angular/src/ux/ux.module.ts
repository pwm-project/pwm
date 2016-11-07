import { module } from 'angular';
import AppBarComponent from './app-bar.component';
import AutoCompleteComponent from './auto-complete.component';
import IconButtonComponent from './icon-button.component';
import TableDirective from './table.directive';
import TableColumnDirective from './table-column.directive';

var moduleName = 'peoplesearch.ux';

module(moduleName, [ ])
    .component('mfAppBar', AppBarComponent)
    .component('mfAutoComplete', AutoCompleteComponent)
    .component('mfIconButton', IconButtonComponent)
    .directive('mfTable', TableDirective.factory)
    .directive('mfTableColumn', TableColumnDirective.factory);

export default moduleName;
