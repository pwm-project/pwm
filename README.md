# PWM

PWM is an open source password self service application for LDAP directories. PWM is an ideal candidate for organizations that wish to “roll their own” password self service solution, but do not wish to start from scratch. [Overview/Screenshots](https://docs.google.com/presentation/d/1LxDXV_iiToJXAzzT9mc1xXO0atVObmRpCame6qXOyxM/pub?slide=id.p8)

Official project page is at [https://github.com/pwm-project/pwm/](https://github.com/pwm-project/pwm/).

# Links
* [PWM-General Google Group](https://groups.google.com/group/pwm-general) - please ask for assistance here first.
* [PWM Documentation Wiki](https://github.com/pwm-project/pwm/wiki) - Home for PWM documentation
* [Current Builds](https://www.pwm-project.org/artifacts/pwm/) - Current downloads built from recent github project commits
* [PWM Reference](https://www.pwm-project.org/pwm/public/reference/) - Reference documentation built into PWM.

# Features
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
* PeopleSearch (white pages)
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

[NetIQ Self Service Password Reset](https://www.microfocus.com/en-us/products/netiq-self-service-password-reset/overview) is a commercial, supported self service password reset product based on PWM.

# Build Information

Build pre-requisites:
* Java 1.8 JDK or newer
* Maven 3.2 or newer

Build execution:
* Set `JAVA_HOME` environment variable to JDK home  
* Run `mvn clean package` in base directory

A WAR file suitable for deployment on Apache Tomcat is created in `webapp/target` directory.  Rename to `pwm.war` and copy into `tomcat/webapp` directory.

Alternatively, an executable JAR file is created in `onejar\target`.  This JAR file is self-contained single executable with embedded Apache Tomcat runtime. To execute use a command similar to:   

`java -jar pwm-onejar.jar`

The executable will show additional options that may be required.

A docker image is created in `docker/target` as jib-image.tar.  You can import this docker image using a command similar to:

`docker load --input=jib-image.tar`

Create docker container and run using:

`docker run -d --name <container name> -p 8443:8443 pwm/pwm-webapp`

This will expose the https port to 8443.  If you want the configuration to persist to you can also exposed configuration volume of `/config` using the docker `-v` option during the container
creation and map it to a directory on the docker host or use a docker volume container.  
The PWM docker container will place all of it's configuration and runtime data in the `/config` volume.

# PWM Source Code License Update 2019

* Previous License: GPL v2.0
* New License: Apache 2.0
* Update Date: June 17, 2019

This project is licensed using Apache 2.0 License (https://www.apache.org/licenses/LICENSE-2.0).  Previous versions 
of this source code were licensed under GPL v2.0 License (https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
New submissions to this code base are made under the Apache 2.0 License.  The GPL branch of the source code contains the 
previously licensed GPL v2.0 code.
