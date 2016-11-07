import * as angular from 'angular';

export function Component(options: {
    bindings?: any,
    bindToController?: boolean,
    controllerAs?: string,
    template?: string,
    templateUrl?: string,
    transclude?: boolean,
    stylesheetUrl?: string
}) {
    return (controller: Function) => angular.extend(options, { controller: controller });
}
