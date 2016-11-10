import { module } from 'angular';
import AppBarComponent from './app-bar.component';
import AutoCompleteComponent from './auto-complete.component';
import IconButtonComponent from './icon-button.component';
import TableDirectiveFactory from './table.directive';
import TableColumnDirectiveFactory from './table-column.directive';

var moduleName = 'peoplesearch.ux';

module(moduleName, [ ])
    .component('mfAppBar', AppBarComponent)
    .component('mfAutoComplete', AutoCompleteComponent)
    .component('mfIconButton', IconButtonComponent)
    .directive('mfTable', TableDirectiveFactory)
    .directive('mfTableColumn', TableColumnDirectiveFactory);

export default moduleName;
