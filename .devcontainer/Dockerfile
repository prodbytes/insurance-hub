# Jetify's image ships devbox + nix preconfigured for the non-root `devbox` user.
FROM jetpackio/devbox:latest

# Tools used by scripts/health.sh (curl for the ih-vdn probe, jq for the docker
# registry-auth check; the script falls back to grep if jq is missing).
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl jq ca-certificates \
    && rm -rf /var/lib/apt/lists/*
USER devbox

# Warm the nix store with this project's packages so first boot is fast.
# The runtime workspace mount overlays these files, but the downloaded store
# paths persist in the image, so the later `devbox install` only re-links.
COPY --chown=devbox:devbox devbox.json devbox.lock /home/devbox/prewarm/
RUN cd /home/devbox/prewarm && devbox install && cd / && rm -rf /home/devbox/prewarm
