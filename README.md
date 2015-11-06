# PWM

PWM is an open source password self service application for LDAP directories. PWM is an ideal candidate for organizations that wish to “roll their own” password self service solution, but do not wish to start from scratch. [Overview/Screenshots](https://docs.google.com/presentation/d/1LxDXV_iiToJXAzzT9mc1xXO0atVObmRpCame6qXOyxM/pub?slide=id.p8)

Official project page is at [https://github.com/pwm-project/pwm/](https://github.com/pwm-project/pwm/).

# Links
* [Release Downloads](https://drive.google.com/folderview?id=0B3oHdiTrftrGV3ZrMi1LUzVCY1U&usp=sharing#list) - the release versions are quite dated, consider using a current build.
* [Current Builds](http://www.pwm-project.org/artifacts/pwm/) - Current downloads built from recent github project commits
* [Google Groups](https://groups.google.com/group/pwm-general) - please ask for assistance here.
* [PWM Admin Guide](http://pwm.googlecode.com/svn/trunk/pwm/supplemental/PWMAdministrationGuide.pdf)

Features
* Web based configuration manager with over 400 configurable settings
* Configurable display values for every user-facing text string
* Localized for Chinese (中文), Czech (ceština), Dutch (Nederlands), English, Finnish (suomi), French (français), German (Deutsch), Hebrew (עברית), Italian (italiano), Japanese (日本語), Korean (한국어), Polish (polski), Portuguese (português), Slovak (Slovenčina), Spanish (español), Thai (ไทย) and Turkish (Türkçe)
* Polished, intuitive end-user interface with as-you-type password rule enforcement
* Forgotten Password
  * Store Responses in local server, standard RDBMS database, LDAP server or Novell NMAS repositories
  * Use Forgotten Password, Email/SMS Token/PIN, TOTP, Remote REST service, User LDAP attribute values, or any combination
  * Stand-alone, easy to deploy, java web application
* Helpdesk password reset and intruder lockout clearing
* New User Registration / Account Creation
* Guest User Registration / Updating
* PeopleSearch? (white pages)
* Account Activation  / First time password assignment
* Administration modules including intruder-lockout manager, and online log viewer, daily stats viewer and user information debugging
* Easy to customize JSP HTML pages
* Theme-able interface with several example CSS themes
* Support for large dictionary wordlists to enforce strong passwords
* Shared password history to prevent passwords from being reused organizationally
* Automatic LDAP server fail-over to multiple ldap servers
* Support for password replication checking and minimum time delays during password sets
* Captcha support using reCaptcha
* Integration with CAS
* Support for minimal, restricted and mobile browsers with no cookies, javascript or css
* Specialized skins for iPhone/Mobile devices
* Designed for integration with existing portals and web security gateways
* Directory Support
  * Generic LDAP
  * Directory 389
  * NetIQ  eDirectory
    * Password Policies & Challenge Sets
    * NMAS Operations and Error handling
    * Support for NMAS user challenge/responses
  * Microsoft Active Directory
  * OpenLDAP

[NetIQ SSPR](https://www.netiq.com/products/self-service-password-reset/) is a commercial, supported self service password reset product based on PWM.
