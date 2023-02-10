# Changelog

## [2.0.5] - Release Feb 10, 2023
- update java and javascript dependencies
- update tomcat to 9.0.71 for onejar/docker images
- update java to 11.0.18_10 in docker image
- fix issue #688 - photo download mime type enforcement
- fix issue #689 - XML entity reference attack on log event data
- fix issue #690 - LDAP search filter injection during advanced peoplesearch and helpdesk queries
- fix issue #691 - Helpdesk idle timeout not working
- update default C/R PBKDF2/SHA512 iteration count to 1_000_000

## [2.0.4] - Released Oct 1, 2022
- version check service request frequency fix
- update java and javascript dependencies
- update tomcat to 9.0.67 for onejar/docker images
- update java to 11.0.16.1 in docker image

## [2.0.3] - Released July 30, 2022
- version check service de-serialization error fix
- fix issue with config guide buttons not working on storage selection page

## [2.0.2] - Released July 7, 2022
- add version check service
- update java and npm, dependencies including tomcat 9.0.65 for onejar/docker images.
- fix issue #542 - web actions do not save/load properly if a basic auth password is not included
- fix issue #660 - Shortcut module does not display shortcuts based on …
- fix issue with js dom/ready initialization on helpdesk/peoplesearch page loading
- replace log4j with reload4j (issue #628)

## [2.0.1] - Released March 11, 2022
- Issue #573 - PWM 5081 at the end of user activation ( no profile assigned )
- Issue #615 - Error 5203 while editing/removing challenge policy questions in config editor
- Dependency/Library updates
