#!/usr/bin/env python3
"""Configures the hosted Supabase project through the Management API.

Runs in the "Deploy backend" GitHub Actions workflow. It:
  1. applies any new files in supabase/migrations (tracked in supabase_migrations.schema_migrations,
     the same table the Supabase CLI uses),
  2. sets the email-code sign-in settings, templates and (if provided) custom SMTP,
  3. points Postgres push dispatch (pg_net) at this project's push-dispatch function,
  4. stores the FCM service account as an Edge Function secret (if provided).

Environment:
  SUPABASE_ACCESS_TOKEN   required, GitHub secret (personal access token)
  SUPABASE_PROJECT_REF    required, from config/backend.properties
  SMTP_USER               optional, GitHub secret (the Gmail address that sends codes)
  SMTP_PASSWORD           optional, GitHub secret (Gmail app password)
  FCM_SERVICE_ACCOUNT     optional, GitHub secret (Firebase service-account JSON)

Secrets are never printed.
"""
import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.request

API = "https://api.supabase.com/v1"
ROOT = pathlib.Path(__file__).resolve().parent.parent


def env(name: str, required: bool = False) -> str:
    value = (os.environ.get(name) or "").strip()
    if required and not value:
        sys.exit(f"{name} is not set. See docs/SETUP.md.")
    return value


TOKEN = env("SUPABASE_ACCESS_TOKEN", required=True)
REF = env("SUPABASE_PROJECT_REF", required=True)


def call(method: str, path: str, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        f"{API}{path}",
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Content-Type": "application/json",
            "User-Agent": "meyouww-deploy",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw = resp.read().decode()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        detail = e.read().decode()[:500]
        sys.exit(f"{method} {path} failed: HTTP {e.code}: {detail}")


def sql(query: str):
    return call("POST", f"/projects/{REF}/database/query", {"query": query})


def literal(text: str) -> str:
    return "'" + text.replace("'", "''") + "'"


def apply_migrations() -> None:
    sql(
        "create schema if not exists supabase_migrations;"
        "create table if not exists supabase_migrations.schema_migrations"
        " (version text not null primary key, statements text[], name text);"
    )
    rows = sql("select version from supabase_migrations.schema_migrations") or []
    applied = {r["version"] for r in rows}
    files = sorted((ROOT / "supabase" / "migrations").glob("*.sql"))
    pending = 0
    for f in files:
        m = re.match(r"^(\d+)_(.+)\.sql$", f.name)
        if not m:
            continue
        version, name = m.group(1), m.group(2)
        if version in applied:
            continue
        body = f.read_text(encoding="utf-8")
        print(f"Applying migration {f.name}")
        sql(
            "begin;\n" + body + "\n;"
            f"insert into supabase_migrations.schema_migrations (version, name, statements)"
            f" values ({literal(version)}, {literal(name)}, array[{literal(body)}]);\n"
            "commit;"
        )
        pending += 1
    print(f"Migrations: {pending} applied, {len(applied)} already present.")


CODE_EMAIL = """<div style="font-family:sans-serif;max-width:420px;margin:auto;padding:24px;border:3px solid #231B1E;border-radius:22px;background:#FFF6E0;color:#231B1E">
<h2 style="margin-top:0">Your meyouww code</h2>
<p>Enter this code in the app:</p>
<p style="font-size:34px;letter-spacing:8px;font-weight:bold;margin:16px 0">{{ .Token }}</p>
<p>It expires in 10 minutes. If you didn't ask for it, you can ignore this email.</p>
</div>"""


def configure_auth() -> None:
    settings = {
        "site_url": "https://github.com/69nightmare/meyouww",
        "external_email_enabled": True,
        "mailer_autoconfirm": False,
        "mailer_otp_length": 6,
        "mailer_otp_exp": 600,
        "mailer_subjects_confirmation": "Your meyouww code",
        "mailer_templates_confirmation_content": CODE_EMAIL,
        "mailer_subjects_magic_link": "Your meyouww code",
        "mailer_templates_magic_link_content": CODE_EMAIL,
    }
    smtp_user, smtp_pass = env("SMTP_USER"), env("SMTP_PASSWORD")
    if smtp_user and smtp_pass:
        settings.update({
            "smtp_host": "smtp.gmail.com",
            "smtp_port": "587",
            "smtp_user": smtp_user,
            "smtp_pass": smtp_pass.replace(" ", ""),
            "smtp_admin_email": smtp_user,
            "smtp_sender_name": "meyouww",
            "smtp_max_frequency": 60,
            # Custom SMTP lifts Supabase's tiny default limit; keep it modest.
            "rate_limit_email_sent": 30,
        })
        print("Auth: email-code settings and custom SMTP updated.")
    else:
        print("Auth: email-code settings updated. SMTP_USER/SMTP_PASSWORD not set, so custom SMTP is unchanged.")
    call("PATCH", f"/projects/{REF}/config/auth", settings)


def configure_push_dispatch() -> None:
    url = f"https://{REF}.supabase.co/functions/v1/push-dispatch"
    sql(
        "update private.settings set value = " + literal(url) +
        " where key = 'push_dispatch_url' and value is distinct from " + literal(url) + ";"
    )
    print("Push dispatch: pg_net calls the push-dispatch function.")


def set_function_secrets() -> None:
    raw = env("FCM_SERVICE_ACCOUNT")
    if not raw:
        print("Functions: FCM_SERVICE_ACCOUNT not set, skipped.")
        return
    try:
        parsed = json.loads(raw)
        assert parsed.get("type") == "service_account" and parsed.get("private_key") and parsed.get("client_email")
    except Exception:
        sys.exit("FCM_SERVICE_ACCOUNT is not a Firebase service-account JSON file.")
    call("POST", f"/projects/{REF}/secrets", [{"name": "FCM_SERVICE_ACCOUNT", "value": json.dumps(parsed, separators=(",", ":"))}])
    print(f"Functions: FCM_SERVICE_ACCOUNT stored for project {parsed.get('project_id')}.")


if __name__ == "__main__":
    apply_migrations()
    configure_auth()
    configure_push_dispatch()
    set_function_secrets()
