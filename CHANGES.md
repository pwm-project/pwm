# Changelog

## [Unreleased]
- fix issue #723 - add new `Setting_Label_recovery.oauth.idserver.loginHintValue`
     ("OAuth/OIDC login_hint Value") macro setting on the Forgotten Password
     OAuth profile. When non-empty, its expanded value is sent as the
     standard OIDC `login_hint` query parameter on the authorize redirect,
     so the IdP can pre-fill the username field and avoid prompting the
     user a second time. The parameter name is configurable via the
     `http.parameter.oauth.loginHint` app property (default: `login_hint`).
- add OIDC `id_token` user name resolution, for interoperability with
     PingFederate and other authorization servers that return identity in the
     `id_token` rather than from a NetIQ-style profile/userinfo web service.
     New settings `oauth.idserver.usernameClaim` and
     `recovery.oauth.idserver.usernameClaim` ("OAuth/OIDC User Name Claim")
     name the token claim to read (a comma-separated list is allowed, first
     match wins).  When set, the profile/userinfo service is not called and
     the profile service url / login attribute settings are no longer
     required.  Default is empty, so existing deployments are unaffected.
     The token's `aud`, `azp`, `exp` and `nbf` claims are validated; encrypted
     (JWE) tokens are rejected with a clear error.  Clock skew tolerance is
     configurable via the `oauth.idToken.maxClockSkewSeconds` app property
     (default: 60) and the token response field name via
     `http.parameter.oauth.idToken` (default: `id_token`).
- handle user cancellation at the remote OAuth login page during the Forgotten
     Password flow.  Previously any error response from the oauth server produced
     an error page; a user who clicked "cancel" at the IdP had no way back.  The
     consumer servlet now redirects to the Forgotten Password servlet with
     `cancelOAuth=true`, which drops the in-progress OAUTH verification method and
     returns the user to method selection (or restarts the sequence if no method
     had been satisfied yet).  Recognized cancellation responses are configurable
     via the `oauth.cancelErrorValues` app property, default
     `access_denied,usrcan`, covering both the RFC 6749 section 4.1.2.1 error used
     by PingFederate and the NetIQ OSP proprietary sub error.  The sub error
     parameter name is configurable via `http.parameter.oauth.subError`.
- add `oauth.idserver.clientAuthMethod` and
     `recovery.oauth.idserver.clientAuthMethod` ("OAuth Client Authentication
     Method") settings on both OAuth profiles, controlling how client credentials
     are presented to the token endpoint: authorization header only
     (`client_secret_basic`), request body only (`client_secret_post`), or both.
     PWM has always sent both, which RFC 6749 section 2.3.1 forbids and which
     PingFederate rejects with `invalid_request`.  Default remains both, so
     existing NetIQ OSP deployments are unaffected.
- add `recovery.oauth.idserver.scope` ("OAuth Scope") setting on the Forgotten
     Password OAuth profile, mirroring the existing SSO-authentication scope
     setting.  The Forgotten Password flow previously had no way to send a
     scope at all, which prevented an authorization server from issuing an
     `id_token` for that flow.  When a user name claim is configured, the
     `openid` scope is added automatically if not already present.

## [2.0.8] - Release Feb 21, 2025
- fix issue #711 ERROR_INVALID_FORMID and other errors with 
     recaptcha enabled in chrome and some other browsers
- update embedded tomcat to v9.0.99 for onejar/docker artifacts

## [2.0.7] - Release Jan 18, 2025
- update embedded tomcat to v9.0.98 for onejar/docker artifacts
- update docker image to eclipse-based Java v21.0.5
- update builder to work from Java v11 to v21
- update java and js dependencies
- add basic support for LLDAP ldap directory type
- fix issue #701 - random password generator improvements
- fix issue #697 - html email contains illegal characters

## [2.0.6] - Release May 5, 2023
- update embedded tomcat to v9.0.74 for onejar/docker artifacts
- update docker image to eclipse-based Java v11.0.19
- add post Java v14 support for build and execution of pwm webapp
- update java and js dependencies
- fix illegal url error during email token validation
- fix thread/memory leak during configuration restart
- fix database connection breaking during configuration restart
- add multi-cpu support for response-set hash generation

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
