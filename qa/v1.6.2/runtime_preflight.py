"""Read-only deployed-runtime prerequisite probe; prints no credentials or mail."""
import importlib.util
import json
import os
from pathlib import Path
import sys

result = {'uid': os.getuid(), 'gmail_client_available': False, 'gmail_profile_readable': False,
          'nantes_config_readable': False, 'smtp_library_available': False}
try:
    import smtplib
    result['smtp_library_available'] = True
    config = Path('/opt/data/home/.config/himalaya/config.toml')
    result['nantes_config_readable'] = os.access(config, os.R_OK)
    sys.path.insert(0, '/opt/data/skills/productivity/google-workspace/scripts')
    google_api = importlib.import_module('google_api')
    result['gmail_client_available'] = True
    profile = google_api.build_service('gmail', 'v1').users().getProfile(userId='me').execute()
    result['gmail_profile_readable'] = bool(profile.get('emailAddress'))
except Exception as error:
    result['error_type'] = type(error).__name__
print('V162_RUNTIME_PREFLIGHT_BEGIN')
print(json.dumps(result, sort_keys=True))
print('V162_RUNTIME_PREFLIGHT_END')
