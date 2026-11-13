# Copy to scripts/deploy.config.sh (gitignored) and adjust. Read by scripts/deploy.sh.
# Pride Universal runs dev and prod on the shared app server, in Docker, beside RestoBill.

# SSH alias from ~/.ssh/config (the shared app server 31.97.61.191, user sanskarpandit).
DEPLOY_SSH_HOST="hostinger-vps"

# Created by deploy/setup-server.sh: <env>.env, <env>/app.jar, <env>/uploads.
REMOTE_HOME="/home/sanskarpandit/pride"

# Host ports, bound to 127.0.0.1 only; nginx (deploy/nginx/) proxies the public hostnames to them.
#   prod → https://api.pride.codeimplants.com      (RestoBill is 8100/8101)
#   dev  → https://dev.api.pride.codeimplants.com
PROD_PORT="8110"
DEV_PORT="8111"

HEALTH_PATH="/actuator/health"
HEALTH_TIMEOUT="120"
CONTAINER_MEMORY="768m"
JRE_IMAGE="eclipse-temurin:21-jre"
