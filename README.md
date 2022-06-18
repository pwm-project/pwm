# PWM

PWM is an open source password self-service application for LDAP directories. PWM is an ideal candidate for organizations that wish to “roll their own” password self service solution, but do not wish to start from scratch. [Overview/Screenshots](https://docs.google.com/presentation/d/1LxDXV_iiToJXAzzT9mc1xXO0atVObmRpCame6qXOyxM/pub?slide=id.p8)

Official project page is at [https://github.com/pwm-project/pwm/](https://github.com/pwm-project/pwm/).

# Links
* [PWM-General Google Group](https://groups.google.com/group/pwm-general) - please ask for assistance here first.
* [PWM Documentation Wiki](https://github.com/pwm-project/pwm/wiki) - Home for PWM documentation
* [PWM Reference](https://www.pwm-project.org/pwm/public/reference/) - Reference documentation built into PWM.

# Features
* Web based configuration manager with over 500 configurable settings
  * Configurable display values for every user-facing text string
  * Localized for Chinese (中文), Czech (ceština), Dutch (Nederlands), English, Finnish (suomi), French (français), German (Deutsch), Hebrew (עברית), Italian (italiano), Japanese (日本語), Korean (한국어), Polish (polski), Portuguese (português), Slovak (Slovenčina), Spanish (español), Thai (ไทย) and Turkish (Türkçe)
* Change Password functionality
  * Polished, intuitive end-user interface with as-you-type password rule enforcement
  * Large set of configurable password polices to match any organizational requirements
  * Read policies from LDAP directories (where supported by LDAP server)
* Forgotten Password
  * Store Responses in local server, standard RDBMS database, LDAP server or Novell NMAS repositories
  * Use Forgotten Password, Email/SMS Token/PIN, TOTP, Remote REST service, User LDAP attribute values, or any combination
  * Stand-alone, easy to deploy, java web application
* Helpdesk password reset and intruder lockout clearing
* New User Registration / Account Creation
* Guest User Registration / Updating
* PeopleSearch (white pages)
  * Configurable detail pages
  * OrgChart view
* Account Activation  / First time password assignment
* All configuration contained in a single importable/exportable file
* Support for multple domains/tenants  
* Administration modules including intruder-lockout manager, and online log viewer, daily stats viewer and user information debugging
* Theme-able interface with several example CSS themes
* Support for large dictionary wordlists to enforce strong passwords
* Shared password history to prevent passwords from being reused organizationally
* Captcha support using Google reCaptcha
* Integration with CAS
* Support for minimal, restricted and mobile browsers with no cookies, javascript or css
* Specialized skins for iPhone/Mobile devices
* Designed for integration with existing portals and web security gateways
* OAuth Service Provider to allow single-signon from OAuth servers and using OAuth as a forgotten password verification method
* REST Server APIs for most functionality  
* Callout to REST servers for custom integrations of several functions    
* LDAP Features
  * Support for password replication checking and minimum time delays during password sets
  * Automatic LDAP server fail-over to multiple ldap servers and retry during LDAP server failures
* LDAP Directory Support
  * Generic LDAP
  * Directory 389
  * NetIQ eDirectory
    * Password Policies & Challenge Sets
    * NMAS Operations and Error handling
    * Support for NMAS user challenge/responses
  * Microsoft Active Directory
  * OpenLDAP

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

PWM is primarily developed tested and built using [AdoptOpenJDK](https://adoptopenjdk.net) Java, but any standard Java distribution should work.

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
as `/config`.  You will need to map the `/config` volume to either a localhost or some type of persistent docker
volume for PWM to work properly.

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
docker create --name mypwm -p '8443:8443' pwm/pwm-webapp -v '/config:/home/user/pwm-config
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

