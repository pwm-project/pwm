import * as angular from 'angular';

export function Component(options: {
    controllerAs?: string,
    template?: string,
    templateUrl?: string,
    stylesheetUrl?: string
}) {
    return (controller: Function) => angular.extend(options, { controller: controller });
}
