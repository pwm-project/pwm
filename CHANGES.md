# Changelog

## [2.1.0] - Not yet released
### New
- Multi-Domain support (aka multi-tenant), each domain can server a specific host url with independent configuration and management 
### Changed
- Removed setting 'Security ⇨ Web Security ⇨ Permitted IP Network Addresses', this functionality is better provided by the web server itself.

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
### Changed
- Issue #573 - PWM 5081 at the end of user activation ( no profile assigned )
- Issue #615 - Error 5203 while editing/removing challenge policy questions in config editor
- Dependency/Library updates
