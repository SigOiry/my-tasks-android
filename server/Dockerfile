# Optional immutable application layer; production compose reuses verified installed image.
ARG HERMES_IMAGE=nousresearch/hermes-agent:latest
FROM ${HERMES_IMAGE}
USER root
WORKDIR /opt/mobile-server
COPY --chown=10000:10000 mobile_api.py event_feed.py hermes_chat.py web_auth.py releases.py conversations.py account_auth.py account_routes.py account_admin.py ./
COPY --chown=10000:10000 web_dist ./web_dist
ENV PYTHONPATH=/opt/hermes PYTHONDONTWRITEBYTECODE=1
USER 10000:10000
ENTRYPOINT ["/opt/hermes/.venv/bin/python", "-m", "uvicorn"]
CMD ["mobile_api:create_app", "--factory", "--host", "0.0.0.0", "--port", "9120", "--workers", "1", "--no-access-log", "--no-proxy-headers"]
