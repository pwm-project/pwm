# PWM

PWM is an open source password self-service application for LDAP directories.

Official project page is at [https://github.com/pwm-project/pwm/](https://github.com/pwm-project/pwm/).

# Links
* [PWM-General Google Group](https://groups.google.com/group/pwm-general) - please ask for assistance here first.
* [PWM Documentation Wiki](https://github.com/pwm-project/pwm/wiki) - Home for PWM documentation
* [PWM Reference](https://www.pwm-project.org/pwm/public/reference/) - Reference documentation built into PWM.
* [Downloads](https://github.com/pwm-project/pwm/releases)

# Features
* Web based configuration manager with over 500 configurable settings
  * All configuration contained in a single importable/exportable file
  * Configurable display values for every user-facing text string
* Included localizations (not all are complete or current):
  * English - English
  * Catalan - català
  * Chinese (China) - 中文 (中国)
  * Chinese (Taiwan) - 中文 (台灣)
  * Czech - čeština
  * Danish - dansk
  * Dutch - Nederlands
  * English (Canada) - English (Canada)
  * Finnish - suomi
  * French - français
  * French (Canada) - français (Canada)
  * German - Deutsch
  * Greek - Ελληνικά
  * Hebrew - עברית
  * Hungarian - magyar
  * Italian - italiano
  * Japanese - 日本語
  * Korean - 한국어
  * Norwegian - norsk
  * Norwegian Bokmål - norsk bokmål
  * Norwegian Nynorsk - nynorsk
  * Polish - polski
  * Portuguese - português
  * Portuguese (Brazil) - português (Brasil)
  * Russian - русский
  * Slovak - slovenčina
  * Spanish - español
  * Swedish - svenska
  * Thai - ไทย
  * Turkish - Türkçe
* LDAP Directory Support:
  * Multiple LDAP vendor support:
    * Generic LDAP (best-effort, LDAP password behavior and error handling is not standardized in LDAP)
    * Directory 389
      * Reading of configured user password policies
    * NetIQ eDirectory
      * Read Password Policies & Challenge Sets
      * NMAS Operations and Error handling
      * Support for NMAS user challenge/responses
    * Microsoft Active Directory
      * Reading of Fine-Grained Password Policy (FGPP) Password Setting Objects (PSO) (does not read domain policies)
    * OpenLDAP
  * Native LDAP retry/failover support of multiple redundant LDAP servers
* Large set of locally configurable password polices
  * Standard syntax rules
  * Regex rules
  * Password dictionary enforcement
  * Remote REST server checking
  * AD-style syntax groups
  * Shared password history to prevent passwords from being reused organizationally
* Modules
  * Change Password
    * as-you-type password rule enforcement
    * password strength feedback display
  * Account Activation / First time password assignment
  * Forgotten Password
    * Store Responses in local server, standard RDBMS database, LDAP server or eDirectory NMAS repositories
    * User verification options:
      * Email/SMS Token/PIN
      * TOTP
      * Remote REST service
      * OAuth service
      * User LDAP attribute values
  * New User Registration / Account Creation
  * Guest User Registration / Updating
  * PeopleSearch (white pages)
    * Configurable detail pages
    * OrgChart view
  * Helpdesk password reset and intruder lockout clearing
  * Administration modules including intruder-lockout manager
    * online log viewer 
    * daily stats viewer and user information debugging
    * statistics
    * audit records
* Multiple Deployment Options
  * Java WAR file (bring your own application server, tested with Apache Tomcat)
  * Java single JAR file (bring your own Java VM)
  * Docker container
* Theme-able interface with several example CSS themes
  * Mobile devices specific CSS themes
  * Configuration support for additional web assets (css, js, images, etc)
  * Force display of organizational 
* Captcha support using Google reCaptcha
* Multiple SSO options
  * Basic Authentication 
  * HTTP header username injection
  * Central Authentication Service (CAS)
  * OAuth client
* REST Server APIs for most functionality
  * Password set
  * Forgotten password
  * Password policy reading
  * User attribute updates
  * Password policy verification
* Outbound REST API for custom integrations during user activities such as change password, new user registration, etc.    

## Deploy
PWM is distributed in the following artifacts:

| Artifact| Description |
| --- | --- |
| WAR | Standard Java WAR (Web Archive) application deployment model, you need to have a working java & tomcat configuration on your server. |
| Executable | Command line executable Java JAR application, includes tomcat. |
| Docker | Docker image includes Java and Tomcat. |

For all artifacts, each PWM instance will need an _applicationPath_ directory defined on your local server for PWM's configuration,
log, and runtime files.  Once PWM is configured, the initial web UI will prompt the administrator for LDAP and other configuration settings.  
Alternatively, you can place the _PwmConfiguration.xml_ in the _applicationPath_ directory to create a fully configured instance.

PWM is primarily developed tested and built using [Adoptium](https://adoptium.net/) Java, but any standard Java distribution should work.

### WAR
Requirements:
* Java 11 JDK or better
* Servlet Container v3.0 or better ( tested with Apache Tomcat v9.5.x )

Steps:
1) Get Apache tomcat working to the point you can access the tomcat landing page with your browser.  See tomcat documentation/help sites for 
   assistance with installing and configuring tomcat.
2) Set the _PWM_APPLICATIONPATH_ environment variable in your tomcat instance to a local location of your _applicationPath_ directory. See tomcat and/or your 
   operating system documentation/help sites for assistance with configuring environment variables as the method for doing this depends on OS and deployment type.
2) Place the pwm.war file in tomcat 'webapps' directory (rename from pwm-x.x.x.war with version naming)
3) Access with /pwm url and configure

### Executable
The 'onejar' artifact released with PWM has an embedded tomcat instance, so you don't need to install tomcat to use this
version.  You will be responsible for getting it to run as a service, and you won't be able to do any advanced tomcat
configuration.

Requirements:
* Java 11 JDK or better

Help:
* `java -version` to ensure you have java 11 or better available
* `java -jar pwm-onejar-2.0.0.jar` for command line help

Example for running onejar executable (with /pwm-applicationPath being the location to your _applicationPath_ directory):
```
java -jar pwm-onejar-2.0.0.jar -applicationPath /pwm-applicationPath 
```
By default the executable will remain attached to the console and listen for HTTPS connections on port 8443.


### Docker
The PWM docker image includes Java and Tomcat.  It listens using https on port 8443, and has a volume exposed
as `/config`.  You will need to map the `/config` volume to some type of persistent docker
volume for PWM to retain configuration.

Requirements:
* Server running docker

Steps:

1. Load your docker image with image nae of default _pwm/pwm-webapp_:
```
docker load --input=pwm-docker-image-v2.0.0.tar
```
   
1. Create docker image named _mypwm_, map to the server's 8443 port, and set the config volume to use the server's
local file system _/home/user/pwm-config_ folder:
```
docker create --name mypwm -p '8443:8443' --mount 'type=bind,source=/home/user/pwm-config,destination=/config' pwm/pwm-webapp
```

1. Start the _mypwm_ container:
```
docker start mypwm
```

## Build

Build pre-requisites:
* Java 11 JDK or better
* Git
* The build uses maven, but you do not need to install it; the maven wrapper in the source tree will download a local version.

Build steps:
1. Set _JAVA_HOME_ environment variable to JDK home.
1. Clone the git project 
1. Change to pwm directory
1. Run the maven build 
   
Linux example: 
```
export JAVA_HOME="/home/vm/JavaJDKDirectory"
git clone https://github.com/pwm-project/pwm
cd pwm
./mvnw clean verify
```  
Windows example:
```
set JAVA_HOME="c:\JavaJDKDirectory" 
git clone https://github.com/pwm-project/pwm
cd pwm
mvnw.cmd clean verify
```
On Windows we recommend using paths without spaces (including for the JDK directory).

Artifacts created:

| Format | Directory |
| --- | --- |
| WAR | webapp/target |
| Executable | onejar/target |
| Docker | docker/target |

