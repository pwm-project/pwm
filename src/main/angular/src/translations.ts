import 'angular-translate';

export default [
    '$translateProvider',
    ($translateProvider: angular.translate.ITranslateProvider) => {
        // Register a translation file for each supported language:
        $translateProvider.translations('en', require('i18n/translations_en.json'));
        $translateProvider.useSanitizeValueStrategy('escapeParameters');
        $translateProvider.preferredLanguage('en');
    }];
