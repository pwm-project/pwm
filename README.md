# PWM

PWM is an open source password self-service application for LDAP directories.

Official project page is at [https://github.com/pwm-project/pwm/](https://github.com/pwm-project/pwm/).

PWM is a Java Servlet based application, and is packaged as a Java executable single JAR file, traditional Servlet "WAR" file, and docker image. 

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

## Requirements

Minimum requirements for PWM application.

| PWM Version | Java [^1] | Servlet | Tomcat [^2] |
| --- | --- | --- | --- |
| v2.1.x | 17+ | 3.0 | 9 |
| v2.0.x | 11+ | 3.0 | 8-9 |
| v1.9.x (EOL) | 8-11 | 3.0 | 7-9 |

[^1] There is no requirement for a specific Java implementation, PWM builds use [Adoptium](https://adoptium.net/). 

[^2] Tomcat isn't an explicit requirement, but it is the most common container used with PWM, and
 the one that is used for the docker and onejar builds.



## Deploy / Install
PWM is distributed in the following artifacts, you can use whichever one is most convenient.

| Artifact | Description |
| --- | --- |
| Java Executable | Command line executable Java JAR application, includes tomcat. |
| WAR | Standard Java WAR (Web Archive) application deployment model, you need to have a working java & tomcat configuration on your server. |
| Docker | Docker image includes Java and Tomcat. |

For all deployment types, each PWM instance will need an _applicationPath_ directory defined on your local server for PWM's configuration,
log, and runtime files.  Once PWM is configured, the initial web UI will prompt the administrator for LDAP and other configuration settings.  

### Java Executable
The 'onejar' artifact released with PWM has an embedded tomcat instance, so you don't need to install tomcat to use this
version.  It's ideal for testing and evaluating PWM.  You will be responsible for getting it to run as a service (if desired).  

Requirements:
* Java 11 JDK or better

Help:
* `java -version` to ensure you have java 11 or better available
* `java -jar pwm-onejar-2.0.0.jar` for command line help

Example for running onejar executable (with /pwm-applicationPath being the location to your _applicationPath_ directory):
```
java -jar pwm-onejar-2.0.0.jar -applicationPath /pwm-applicationPath 
```
By default, the executable will remain attached to the console and listen for HTTPS connections on port 8443.


### WAR

Steps:
1) Get Apache tomcat working to the point you can access the tomcat landing page with your browser.  See tomcat documentation/help sites for
   assistance with installing and configuring tomcat.
2) Set the _PWM_APPLICATIONPATH_ environment variable in your tomcat instance to a local location of your _applicationPath_ directory. See tomcat and/or your
   operating system documentation/help sites for assistance with configuring environment variables as the method for doing this depends on OS and deployment type.
2) Place the pwm.war file in tomcat 'webapps' directory (rename from pwm-x.x.x.war with version naming)
3) Access with /pwm url and configure


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
local file system _/home/user/pwm-config_ folder (this will be the PWM application path for the container): 
```
docker create --name mypwm -p '8443:8443' --mount 'type=bind,source=/home/user/pwm-config,destination=/config' pwm/pwm-webapp
```

1. Start the _mypwm_ container:
```
docker start mypwm
```

## Configuration

Before configuring PWM you should use an LDAP browser/editor to ensure expected functionality of your LDAP environment. 
Most difficulties encountered configuring PWM are due to LDAP setup issues or unfamiliarity with LDAP. 
There are many LDAP browsers available, a common one is [Apache Directrory Studio](https://directory.apache.org/studio/). 
Use the browser to navigate your LDAP environment, familiarize yourself with the directory structure, and verify expected behavior.

In particular, Active Directory LDAP can be problematic because it is often mis-configured and behaves in unusual ways compared to other LDAP directories.
Specifically, AD LDAP uses referrals to redirect the LDAP client (PWM in this case) to servers of its choosing, thus PWM must be able to contact all domain controller server instances in the AD environment using the AD-configured DNS name. 
AD LDAP must also be configured to use SSL certificates for password modifications to work.  However, if the AD environment is well configured, PWM will work fine with it.

PWM includes a web-based configuration editor.
When PWM starts with no configuration, a web-based configuration guide will prompt the administrator for basic configuration information.
All configuration information is stored in the _PwmConfiguration.xml_ file, which will be created in the application path directory.
The application path is also used for other files, including a local database (_LocalDB_) (used primarily as a cache or for test environments), log files, and temporary files.
If multiple PWM servers are used in parallel, each server must have identical _PwmConfiguration.xml_ files.

PWM uses a configuration password to protect any modifications to the configuration.
Authentication to PWM requires an LDAP-backed login to a configured administrative account. 
In early setup or in cases of problems with the LDAP directory, it may be necessary to access the configuration when LDAP functionally is not available.
For this purpose, PWM has a "configuration-mode" which allows editing the config with the configuration password, but disables all other end-user functionality.
Configuration mode can be enabled/disabled by editing the _PwmConfiguration.xml_ file and change the`configIsEditable`property near the top of the file, and can also be changed in the web UI.

### Database Usage

PWM can optionally be configured with an RDBMS (also known as a SQL database server).
When configured to use a database, PWM user meta-data such as challenge/response answers, TOTP tokens, usage records, and other data will be stored in the database.
When not configured to use a database, PWM user meta-data will be stored to the LDAP directory.  Neither is better or worse, which one you use depends on your enviornment.

Any SQL server that has a Java supported JDBC driver should work, PWM will create its own schema on the first connection.

## Build

Build pre-requisites:
* Java ( check requirements above for version )
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
On Windows we recommend using paths without spaces for both PWM and JDK directory.

Artifacts created:

| Format | Directory |
| --- | --- |
| WAR | webapp/target |
| Executable | onejar/target |
| Docker | docker/target |

