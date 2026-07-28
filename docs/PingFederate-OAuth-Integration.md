# PingFederate OAuth / OIDC Integration

How to configure PWM to authenticate against **PingFederate** (or another standards-compliant
OIDC provider) for SSO login and/or the Forgotten Password flow.

PWM's OAuth defaults were originally designed for **NetIQ OSP**. Two behaviors that OSP relies on
are incompatible with PingFederate out of the box, so this integration adds settings to opt into
standards-compliant behavior. All new settings default to the legacy (OSP) behavior, so existing
deployments are unaffected until you change them.

There are two independent OAuth configurations in PWM:

| Flow | Setting prefix | Configure if… |
| --- | --- | --- |
| **SSO login** | `oauth.idserver.*` | Users authenticate to PWM via Ping |
| **Forgotten Password** | `recovery.oauth.idserver.*` | The forgotten-password flow authenticates via Ping |

Configure whichever flows you use, or both. The settings are parallel — the checklist below applies
to each set.

---

## The two settings that make Ping work

These are the critical differences from a NetIQ OSP setup.

### 1. OAuth Client Authentication Method

- **Setting:** `OAuth Client Authentication Method` (`oauth.idserver.clientAuthMethod` /
  `recovery.oauth.idserver.clientAuthMethod`)
- **Default:** `Authorization Header and Request Body` (**both**)
- **Set to for Ping:** `Authorization Header Only (client_secret_basic)` **or**
  `Request Body Only (client_secret_post)` — match how the client is registered in PingFederate.

**Why:** RFC 6749 §2.3.1 states a client must not use more than one authentication method per
request. PingFederate rejects a token request that carries the client credentials in *both* the
Authorization header and the request body with an `invalid_request` error. This is the single most
common cause of a failed Ping integration. The default remains "both" because NetIQ OSP requires it.

### 2. OAuth/OIDC User Name Claim

- **Setting:** `OAuth/OIDC User Name Claim` (`oauth.idserver.usernameClaim` /
  `recovery.oauth.idserver.usernameClaim`)
- **Default:** empty (calls the NetIQ-style profile/userinfo web service)
- **Set to for Ping:** an `id_token` claim, e.g. `sub`, `upn`, or `preferred_username`
  (depending on how your Ping OIDC policy maps attributes). A comma-separated list is allowed;
  the first claim present in the token wins.

**Why:** PingFederate returns user identity in the OIDC `id_token`, not via a NetIQ profile/userinfo
web service. When this claim is set, PWM reads the username directly from the `id_token` and:

- the `openid` scope is requested automatically (if not already present), and
- the profile/userinfo **service URL** and **login attribute** settings are no longer required.

---

## Standard OAuth settings (still required)

Not new, but must be populated with Ping values:

| PWM Setting | Ping value |
| --- | --- |
| `OAuth Login URL` | Authorize endpoint, e.g. `https://<ping-host>/as/authorization.oauth2` |
| `OAuth Token / Code Resolve Service URL` | Token endpoint, e.g. `https://<ping-host>/as/token.oauth2` |
| `OAuth Client ID` | Client ID from the Ping OAuth client registration |
| `OAuth Shared Secret` | Client secret from the Ping OAuth client registration |
| `OAuth Server Certificate` | Import Ping's TLS server certificate |
| `OAuth Scope` | Include `openid` (auto-added when a username claim is set, but explicit is fine) |

---

## Forgotten Password flow — extra settings

The recovery flow previously could not send a scope at all, so an authorization server would never
issue an `id_token` for it. These settings bring it to parity:

| PWM Setting | Notes |
| --- | --- |
| `OAuth Scope` (`recovery.oauth.idserver.scope`) | Required so Ping issues an `id_token` in this flow. Include `openid`. |
| `OAuth/OIDC login_hint Value` (`recovery.oauth.idserver.loginHintValue`) | Optional. A macro (e.g. `@LDAP:mail@` or `@LDAP:cn@`) sent as the standard OIDC `login_hint` on the authorize redirect, so Ping pre-fills the username and does not prompt the user a second time. Leave empty to omit. |

---

## Optional advanced tuning (AppProperties)

These live in **App Properties**, not the normal settings UI. Defaults are sensible; change only if
needed:

| AppProperty | Default | Purpose |
| --- | --- | --- |
| `oauth.idToken.maxClockSkewSeconds` | `60` | Allowed clock skew when validating the `id_token` `exp` / `nbf` claims. |
| `oauth.idToken.requiredScope` | `openid` | Scope that triggers `id_token` issuance. |
| `oauth.cancelErrorValues` | `access_denied,usrcan` | OAuth error values treated as a user cancellation rather than a hard error. |

---

## Configuration checklist

### On the PingFederate side

1. Register an **OAuth client** for PWM.
   - Note the **Client ID** and **Client Secret**.
   - Set the **redirect URI** to PWM's OAuth return URL (`https://<pwm-host>/pwm/oauth`).
   - Choose a **client authentication method** — `client_secret_basic` or `client_secret_post`.
     Remember which one; it must match PWM's setting below.
2. Ensure the OIDC policy issues an `id_token` containing a stable username claim
   (`sub`, `upn`, or `preferred_username`) and that the `openid` scope is permitted for the client.
3. Have the token endpoint TLS certificate available to import into PWM.

### On the PWM side (per flow — SSO and/or Forgotten Password)

1. **OAuth Login URL** → Ping authorize endpoint.
2. **OAuth Token / Code Resolve Service URL** → Ping token endpoint.
3. **OAuth Client ID** / **OAuth Shared Secret** → from the Ping client registration.
4. **OAuth Server Certificate** → import Ping's TLS cert.
5. **OAuth Client Authentication Method** → set to `basic` or `post` to match Ping (**not** "both").
6. **OAuth/OIDC User Name Claim** → the `id_token` claim to read (e.g. `sub`).
7. **OAuth Scope** → include `openid`.
8. *(Forgotten Password only)* set **OAuth Scope** and optionally **OAuth/OIDC login_hint Value**.

### Verify

- Confirm login (and/or forgotten-password) redirects to Ping, authenticates, and returns to PWM.
- On failure, check PWM logs for `invalid_request` (→ wrong client auth method) or a claim-not-found
  error (→ wrong username claim name).

---

## Notes

- The `id_token` is validated for the registered `aud`, `azp`, `exp`, and `nbf` claims.
- Encrypted (JWE) tokens and malformed JWTs are rejected with actionable errors.
- All new settings default to empty / "both", so existing NetIQ OSP deployments see **no behavior
  change**. Ping support is strictly opt-in.

_Implemented in `OAuthIdTokenReader`, `OAuthClientAuthMethod`, `OAuthMachine`, and `OAuthSettings`
(commit `bb0779008`)._
