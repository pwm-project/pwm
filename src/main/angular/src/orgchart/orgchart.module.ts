import { module } from 'angular';
import OrgChartComponent from './orgchart.component';

var moduleName = 'org-chart';

module(moduleName, [])
    .component('orgChart', OrgChartComponent);

export default moduleName;
