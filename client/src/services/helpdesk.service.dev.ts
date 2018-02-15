/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import {
    IHelpDeskService, IRandomPasswordResponse, IRecentVerifications, ISuccessResponse, IVerificationStatus,
    IVerificationTokenResponse
} from './helpdesk.service';
import {IPromise, IQService, ITimeoutService, IWindowService} from 'angular';

const SIMULATED_RESPONSE_TIME = 300;

export default class HelpDeskService implements IHelpDeskService {
    PWM_GLOBAL: any;

    static $inject = [ '$q', '$timeout', '$window' ];
    constructor(private $q: IQService, private $timeout: ITimeoutService, private $window: IWindowService) {
    }

    checkVerification(userKey: string): IPromise<IVerificationStatus> {
        return this.simulateResponse({ passed: false });
    }

    clearOtpSecret(userKey: string): IPromise<ISuccessResponse> {
        return this.simulateResponse({ successMessage: 'OTP Secret successfully cleared.' });
    }

    clearResponses(userKey: string): IPromise<ISuccessResponse> {
        return this.simulateResponse({ successMessage: 'Security answers successfully cleared.' });
    }

    customAction(actionName: string, userKey: string): IPromise<ISuccessResponse> {
        if (actionName === 'custom_0') {
            return this.simulateResponse({ successMessage: 'User successfully cloned.' });
        }
        else if (actionName === 'custom_1') {
            return this.simulateResponse({ successMessage: 'User successfully merged.' });
        }
        else {
            this.$q.reject('Error! Action name doesn\'t exist.');
        }
    }

    deleteUser(userKey: string): IPromise<ISuccessResponse> {
        return this.simulateResponse({ successMessage: 'User successfully deleted.' });
    }

    getPerson(userKey: string): IPromise<any> {
        return this.simulateResponse({
            'userDisplayName': 'Andrew Astin - aastin - aastin@ad.utopia.netiq.com',
            'userHistory': [
                {
                    'timestamp': '2017-12-13T18:36:50Z',
                    'label': 'Help Desk Set Password'
                },
                {
                    'timestamp': '2017-12-13T18:47:11Z',
                    'label': 'Help Desk Set Password'
                },
                {
                    'timestamp': '2017-12-13T18:50:35Z',
                    'label': 'Setup Password Responses'
                },
                {
                    'timestamp': '2017-12-13T19:19:33Z',
                    'label': 'Help Desk Set Password'
                },
                {
                    'timestamp': '2017-12-13T19:23:50Z',
                    'label': 'Change Password'
                },
                {
                    'timestamp': '2017-12-13T20:47:57Z',
                    'label': 'Clear Responses'
                },
                {
                    'timestamp': '2017-12-13T20:48:38Z',
                    'label': 'Setup Password Responses'
                }
            ],
            'passwordPolicyRules': {
                'Policy Enabled': 'True',
                'Minimum Length': '2',
                'Maximum Length': '64',
                'Minimum Upper Case': '0',
                'Maximum Upper Case': '0',
                'Minimum Lower Case': '0',
                'Maximum Lower Case': '0',
                'Allow Numeric': 'True',
                'Minimum Numeric': '0',
                'Maximum Numeric': '0',
                'Minimum Unique': '0',
                'Allow First Character Numeric': 'True',
                'Allow Last Character Numeric': 'True',
                'Allow Special': 'True',
                'Minimum Special': '0',
                'Maximum Special': '0',
                'Allow First Character Special': 'True',
                'Allow Last Character Special': 'True',
                'Maximum Repeat': '0',
                'Maximum Sequential Repeat': '0',
                'Change Message': '',
                'Expiration Interval': '0',
                'Minimum Lifetime': '0',
                'Case Sensitive': 'True',
                'Unique Required': 'False',
                'Disallowed Values': 'password\ntest',
                'Disallowed Attributes': 'givenName\ncn\nsn',
                'Disallow Current': 'True',
                'Maximum AD Complexity Violations': '2',
                'Regular Expression Match': '',
                'Regular Expression No Match': '',
                'Minimum Alpha': '0',
                'Maximum Alpha': '0',
                'Minimum Non-Alpha': '0',
                'Maximum Non-Alpha': '0',
                'Enable Word List': 'True',
                'Minimum Strength': '0',
                'Maximum Consecutive': '0',
                'Character Groups Minimum Required': '0',
                'Character Group Values': '.*[0-9]\n.*[^A-Za-z0-9]\n.*[A-Z]\n.*[a-z]',
                'Rule_AllowMacroInRegExSetting': 'True'
            },
            'passwordRequirements': [
                'Password is case sensitive.',
                'Must be at least 2 characters long.',
                'Must not include any of the following values:  password test',
                'Must not include part of your name or user name.',
                'Must not include a common word or commonly used sequence of characters.'
            ],
            'passwordPolicyDN': 'cn=SSPR,cn=Password Policies,cn=Security',
            'passwordPolicyID': 'n/a',
            'statusData': [
                {
                    'key': 'Field_Username',
                    'type': 'string',
                    'label': 'User Name',
                    'value': 'aastin'
                },
                {
                    'key': 'Field_UserDN',
                    'type': 'string',
                    'label': 'User DN',
                    'value': 'cn=aastin,ou=users,o=novell'
                },
                {
                    'key': 'Field_UserEmail',
                    'type': 'string',
                    'label': 'Email',
                    'value': 'aastin@ad.utopia.netiq.com'
                },
                {
                    'key': 'Field_UserSMS',
                    'type': 'string',
                    'label': 'SMS',
                    'value': 'n/a'
                },
                {
                    'key': 'Field_AccountEnabled',
                    'type': 'string',
                    'label': 'Account Enabled',
                    'value': 'True'
                },
                {
                    'key': 'Field_AccountExpired',
                    'type': 'string',
                    'label': 'Account Expired',
                    'value': 'False'
                },
                {
                    'key': 'Field_UserGUID',
                    'type': 'string',
                    'label': 'User GUID',
                    'value': 'ae95c9790234624d9848ae95c9790234'
                },
                {
                    'key': 'Field_AccountExpirationTime',
                    'type': 'timestamp',
                    'label': 'Account Expiration Time',
                    'value': 'n/a'
                },
                {
                    'key': 'Field_LastLoginTime',
                    'type': 'timestamp',
                    'label': 'Last Login Time',
                    'value': '2017-12-13T20:48:33Z'
                },
                {
                    'key': 'Field_LastLoginTimeDelta',
                    'type': 'string',
                    'label': 'Last Login Time Delta',
                    'value': '4 days, 22 hours, 49 minutes, 3 seconds'
                },
                {
                    'key': 'Field_PasswordExpired',
                    'type': 'string',
                    'label': 'Password Expired',
                    'value': 'False'
                },
                {
                    'key': 'Field_PasswordPreExpired',
                    'type': 'string',
                    'label': 'Password Pre-Expired',
                    'value': 'False'
                },
                {
                    'key': 'Field_PasswordWithinWarningPeriod',
                    'type': 'string',
                    'label': 'Within Warning Period',
                    'value': 'False'
                },
                {
                    'key': 'Field_PasswordSetTime',
                    'type': 'timestamp',
                    'label': 'Password Set Time',
                    'value': '2017-12-13T19:23:49Z'
                },
                {
                    'key': 'Field_PasswordSetTimeDelta',
                    'type': 'string',
                    'label': 'Password Set Time Delta',
                    'value': '5 days, 13 minutes, 47 seconds'
                },
                {
                    'key': 'Field_PasswordExpirationTime',
                    'type': 'timestamp',
                    'label': 'Password Expiration Time',
                    'value': 'n/a'
                },
                {
                    'key': 'Field_PasswordLocked',
                    'type': 'string',
                    'label': 'Password Locked (Intruder Detect)',
                    'value': 'False'
                },
                {
                    'key': 'Field_ResponsesStored',
                    'type': 'string',
                    'label': 'Responses Stored',
                    'value': 'True'
                },
                {
                    'key': 'Field_ResponsesNeeded',
                    'type': 'string',
                    'label': 'Response Updates are Needed',
                    'value': 'False'
                },
                {
                    'key': 'Field_ResponsesTimestamp',
                    'type': 'timestamp',
                    'label': 'Stored Responses Timestamp',
                    'value': '2017-12-13T20:48:37Z'
                },
                {
                    'key': 'Field_ResponsesTimestamp',
                    'type': 'timestamp',
                    'label': 'Stored Responses Timestamp',
                    'value': '2017-12-13T20:48:37Z'
                }
            ],
            'profileData': [
                {
                    'key': 'cn',
                    'type': 'string',
                    'label': 'CN',
                    'value': 'aastin'
                },
                {
                    'key': 'givenName',
                    'type': 'string',
                    'label': 'First Name',
                    'value': 'Andrew'
                },
                {
                    'key': 'sn',
                    'type': 'string',
                    'label': 'Last Name',
                    'value': 'Astin'
                },
                {
                    'key': 'mail',
                    'type': 'string',
                    'label': 'Email Address',
                    'value': 'aastin@ad.utopia.netiq.com'
                },
                {
                    'key': 'telephoneNumber',
                    'type': 'multiString',
                    'label': 'Telephone Number',
                    'values': [
                        '801-802-0259'
                    ]
                },
                {
                    'key': 'title',
                    'type': 'string',
                    'label': 'Title',
                    'value': 'Identity Administrator'
                },
                {
                    'key': 'ou',
                    'type': 'string',
                    'label': 'Department',
                    'value': 'Information Technology'
                },
                {
                    'key': 'businessCategory',
                    'type': 'string',
                    'label': 'Business Category',
                    'value': 'Identity'
                },
                {
                    'key': 'company',
                    'type': 'string',
                    'label': 'Company',
                    'value': 'Utopia Corp'
                },
                {
                    'key': 'street',
                    'type': 'string',
                    'label': 'Street',
                    'value': '50 Upper 5th'
                },
                {
                    'key': 'physicalDeliveryOfficeName',
                    'type': 'string',
                    'label': 'City',
                    'value': 'New York City'
                },
                {
                    'key': 'st',
                    'type': 'string',
                    'label': 'State',
                    'value': 'New York'
                },
                {
                    'key': 'l',
                    'type': 'string',
                    'label': 'Location',
                    'value': 'Manhattan'
                },
                {
                    'key': 'employeeStatus',
                    'type': 'string',
                    'label': 'Employee Status',
                    'value': 'A'
                },
                {
                    'key': 'workforceID',
                    'type': 'string',
                    'label': 'Workforce ID',
                    'value': 'E000259'
                }
            ],
            'helpdeskResponses': [
                {
                    'key': 'item_1',
                    'type': 'string',
                    'label': 'How many years old are you?',
                    'value': '25RRR'
                }
            ],
            'visibleButtons': [
                'refresh',
                'back',
                'changePassword',
                'unlock',
                'clearResponses',
                'clearOtpSecret',
                'verification',
                'deleteUser'
            ],
            'enabledButtons': [
                'refresh',
                'back',
                'changePassword',
                'unlock',
                'clearResponses',
                'clearOtpSecret',
                'verification',
                'deleteUser'
            ],
            'customButtons': [
                {
                    'name': 'custom_0',
                    'label': 'Clone User',
                    'description': 'Clones the current user'
                },
                {
                    'name': 'custom_1',
                    'label': 'Merge User',
                    'description': 'Merges the current user with another user'
                }
            ]
        });
    }

    getRandomPassword(userKey: string): IPromise<IRandomPasswordResponse> {
        let randomNumber = Math.floor(Math.random() * Math.floor(100));
        let passwordSuggestion = 'suggestion' + randomNumber;
        return this.simulateResponse({ password: passwordSuggestion });
    }

    getRecentVerifications(): IPromise<IRecentVerifications> {
        return this.simulateResponse([
            {
                timestamp: '2017-12-06T23:19:07Z',
                profile: 'default',
                username: 'aastin',
                method: 'Personal Data'
            },
            {
                timestamp: '2017-12-03T22:11:07Z',
                profile: 'default',
                username: 'bjroach',
                method: 'Personal Data'
            },
            {
                timestamp: '2017-12-02T13:09:07Z',
                profile: 'default',
                username: 'rrhoads',
                method: 'Personal Data'
            }
        ]);
    }

    sendVerificationToken(userKey: string, choice: string): IPromise<IVerificationTokenResponse> {
        return this.simulateResponse({ destination: 'bcarrolj@paypal.com' });
    }

    setPassword(userKey: string, random: boolean, password?: string): IPromise<ISuccessResponse> {
        if (random) {
            return this.simulateResponse({ successMessage: 'Random password successfully set!' });
        }
        else {
            return this.simulateResponse({ successMessage: 'Password successfully set!' });
        }
    }

    private simulateResponse<T>(data: T): IPromise<T> {
        let self = this;

        let deferred = this.$q.defer();
        let deferredAbort = this.$q.defer();

        let timeoutPromise = this.$timeout(() => {
            deferred.resolve(data);
        }, SIMULATED_RESPONSE_TIME);

        // To simulate an abortable promise, edit SIMULATED_RESPONSE_TIME
        deferred.promise['_httpTimeout'] = deferredAbort;
        deferredAbort.promise.then(() => {
            self.$timeout.cancel(timeoutPromise);
            deferred.resolve();
        });

        return deferred.promise as IPromise<T>;
    }

    unlockIntruder(userKey: string): IPromise<ISuccessResponse> {
        return this.simulateResponse({ successMessage: 'Unlock successful.' });
    }

    validateVerificationData(userKey: string, data: any, method: string): IPromise<IVerificationStatus> {
        return this.simulateResponse({ passed: true });
    }

    get showStrengthMeter(): boolean {
        return true;
    }
}
