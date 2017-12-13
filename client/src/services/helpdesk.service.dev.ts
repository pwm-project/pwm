/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
    IHelpDeskService, IRecentVerifications, IVerificationStatus,
    IVerificationTokenResponse
} from './helpdesk.service';
import {IPromise, IQService, IWindowService} from 'angular';

export default class HelpDeskService implements IHelpDeskService {
    PWM_GLOBAL: any;

    static $inject = [ '$q', '$window' ];
    constructor(private $q: IQService, private $window: IWindowService) {
    }

    checkVerification(userKey: string): IPromise<IVerificationStatus> {
        return this.$q.resolve({ passed: false });
    }

    getPerson(userKey: string): IPromise<any> {
        return this.$q.resolve({
                'userInfo': {
                    'userDN': 'cn=acorry,ou=users,o=novell',
                    'ldapProfile': 'default',
                    'userID': 'acorry',
                    'userEmailAddress': 'acorry@ad.utopia.netiq.com',
                    'requiresNewPassword': false,
                    'requiresResponseConfig': true,
                    'requiresUpdateProfile': false,
                    'requiresOtpConfig': false,
                    'requiresInteraction': true,
                    'passwordStatus': {
                        'expired': false,
                        'preExpired': false,
                        'violatesPolicy': false,
                        'warnPeriod': false
                    },
                    'passwordPolicy': {
                        'DisallowCurrent': 'true',
                        'MinimumNonAlpha': '0',
                        'MinimumNumeric': '0',
                        'AllowNumeric': 'true',
                        'MaximumSpecial': '0',
                        'AllowLastCharSpecial': 'true',
                        'CharGroupsValues': '.*[0-9]\n.*[^A-Za-z0-9]\n.*[A-Z]\n.*[a-z]',
                        'MinimumLength': '4',
                        'AllowFirstCharNumeric': 'true',
                        'MaximumUpperCase': '0',
                        'MaximumAlpha': '0',
                        'MinimumLowerCase': '0',
                        'UniqueRequired': 'false',
                        'PolicyEnabled': 'true',
                        'ADComplexityMaxViolations': '2',
                        'MaximumLength': '12',
                        'DisallowedValues': 'password\ntest',
                        'MinimumUnique': '0',
                        'MinimumLifetime': '0',
                        'CaseSensitive': 'true',
                        'AllowMacroInRegExSetting': 'true',
                        'AllowLastCharNumeric': 'true',
                        'RegExNoMatch': '',
                        'MinimumStrength': '0',
                        'RegExMatch': '',
                        'ExpirationInterval': '0',
                        'CharGroupsMinMatch': '0',
                        'AllowFirstCharSpecial': 'true',
                        'MinimumSpecial': '0',
                        'MaximumSequentialRepeat': '0',
                        'MinimumUpperCase': '0',
                        'EnableWordlist': 'true',
                        'MaximumRepeat': '0',
                        'DisallowedAttributes': 'givenName\ncn\nsn',
                        'MinimumAlpha': '0',
                        'MaximumLowerCase': '0',
                        'MaximumConsecutive': '0',
                        'ChangeMessage': '',
                        'MaximumNumeric': '0',
                        'AllowSpecial': 'true',
                        'MaximumNonAlpha': '0'
                    },
                    'passwordRules': [
                        'Password is case sensitive.',
                        'Must be at least 4 characters long.',
                        'Must be no more than 12 characters long.',
                        'Must not include any of the following values:  password test',
                        'Must not include part of your name or user name.',
                        'Must not include a common word or commonly used sequence of characters.'
                    ]
                },
                'userDisplayName': 'Aaron Corry - acorry - acorry@ad.utopia.netiq.com',
                'intruderLocked': false,
                'accountEnabled': true,
                'accountExpired': false,
                'userHistory': [],
                'searchDetails': {
                    'CN': [ 'acorry' ],
                    'First Name': [ 'Aaron' ],
                    'Last Name': [ 'Corry' ],
                    'Email Address': [ 'acorry@ad.utopia.netiq.com' ],
                    'Telephone Number': [ '801-802-0260' ],
                    'Title': [ 'Identity Architect' ],
                    'Department': [ 'Information Technology' ],
                    'Business Category': [ 'Identity' ],
                    'Company': [ 'Utopia Corp' ],
                    'Street': [ '50 Upper 5th' ],
                    'City': [ 'New York City' ],
                    'State': [ 'New York' ],
                    'Location': [ 'Manhattan' ],
                    'Employee Status': [ 'A' ],
                    'Workforce ID': [ 'E000260' ]
                },
                'passwordSetDelta': 'n/a',
                'passwordPolicyRules': {
                    'Policy Enabled': 'True',
                    'Minimum Length': '4',
                    'Maximum Length': '12',
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
                    'Must be at least 4 characters long.',
                    'Must be no more than 12 characters long.',
                    'Must not include any of the following values:  password test',
                    'Must not include part of your name or user name.',
                    'Must not include a common word or commonly used sequence of characters.'
                ],
                'passwordPolicyDN': 'cn=SSPR,cn=Password Policies,cn=Security',
                'passwordPolicyID': 'n/a',
                'hasOtpRecord': false,
                'otpRecordTimestamp': 'n/a'
        });
    }

    getRecentVerifications(): IPromise<IRecentVerifications> {
        return this.$q.resolve([
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
        return this.$q.resolve({ destination: 'bcarrolj@paypal.com' });
    }

    validateVerificationData(userKey: string, data: any, method: string): IPromise<IVerificationStatus> {
        return this.$q.resolve({ passed: true });
    }
}
