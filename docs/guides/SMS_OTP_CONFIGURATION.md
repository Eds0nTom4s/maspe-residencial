# SMS/OTP configuration

This guide documents the public menu OTP configuration. Secrets must be supplied
through environment variables or a secrets manager. Do not commit provider keys.

## Default behavior

By default, real SMS sending is disabled:

```properties
consuma.sms.provider=${CONSUMA_SMS_PROVIDER:disabled}
consuma.sms.enabled=${CONSUMA_SMS_ENABLED:false}
consuma.sms.api-key=${CONSUMA_SMS_API_KEY:}
```

With this default, OTP challenges can still be created, but the SMS gateway
returns a controlled `SMS_DISABLED` response and no external provider call is
made.

## Sandbox/local behavior

For local sandbox flows, use:

```properties
CONSUMA_SMS_PROVIDER=sandbox
CONSUMA_SMS_ENABLED=false
CONSUMA_OTP_MOCK_ENABLED=true
```

The sandbox provider does not call an external API. `CONSUMA_OTP_MOCK_ENABLED`
may return `debugOtp` in API responses so automated tests can verify the flow.
Keep it disabled outside test/sandbox contexts.

## Real provider behavior

To enable a real SMS provider:

```properties
CONSUMA_SMS_PROVIDER=telcosms
CONSUMA_SMS_ENABLED=true
CONSUMA_SMS_BASE_URL=<provider-base-url>
CONSUMA_SMS_API_KEY=<secret-from-vault-or-env>
CONSUMA_SMS_SENDER_ID=CONSUMA
```

The backend validates that `CONSUMA_SMS_BASE_URL` and `CONSUMA_SMS_API_KEY` are
present when a real provider is enabled.

## OTP limits

```properties
CONSUMA_OTP_ENABLED=true
CONSUMA_OTP_LENGTH=6
CONSUMA_OTP_TTL_SECONDS=300
CONSUMA_OTP_MAX_ATTEMPTS=5
CONSUMA_OTP_RESEND_COOLDOWN_SECONDS=60
CONSUMA_OTP_RATE_LIMIT_PER_PHONE=5
CONSUMA_OTP_RATE_LIMIT_PER_IP=20
CONSUMA_OTP_RATE_LIMIT_WINDOW_SECONDS=3600
CONSUMA_OTP_MAX_RESENDS=3
CONSUMA_OTP_MAX_ACTIVE_CHALLENGES_PER_PHONE=1
CONSUMA_OTP_HASH_PEPPER=<secret-from-vault-or-env>
```

Rate limit violations return HTTP `429` with a safe public message. OTP values
are stored as HMAC SHA-256 hashes and are never logged by the OTP service.

## Public endpoints

The public menu uses these endpoints through the normal `/api` base path:

```text
POST /public/q/{token}/identificacao/otp/request
POST /public/q/{token}/identificacao/otp/verify
```

Recovery endpoints also exist for active sessions:

```text
POST /public/q/{token}/recuperar/otp/request
POST /public/q/{token}/recuperar/otp/verify
```

## Operational notes

- Keep `CONSUMA_OTP_MOCK_ENABLED=false` in production.
- Keep `CONSUMA_SMS_PROVIDER=disabled` until provider credentials are available.
- Never expose `debugOtp` in public frontend screens.
- Prefer `CONSUMA_OTP_HASH_PEPPER` from a secret store in production.
