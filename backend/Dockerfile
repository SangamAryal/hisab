# Hisab backend as a container, for hosts like Railway that build from the repo.
# Build from the repo root:  docker build -f backend/Dockerfile .
# Data lives in /pb_data, so mount a persistent volume there.
FROM alpine:3.22
ARG PB_VERSION=0.40.4
ARG TARGETARCH=amd64
RUN apk add --no-cache ca-certificates unzip wget \
 && wget -q "https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/pocketbase_${PB_VERSION}_linux_${TARGETARCH}.zip" -O /tmp/pb.zip \
 && unzip /tmp/pb.zip pocketbase -d /usr/local/bin \
 && rm /tmp/pb.zip

COPY backend/pb_hooks /pb_hooks
COPY backend/pb_migrations /pb_migrations
COPY backend/docker-start.sh /usr/local/bin/hisab-start

# The host's proxy puts the caller's IP in this header (used by rate limits).
ENV HISAB_TRUSTED_PROXY_HEADER=X-Real-IP
EXPOSE 8090
CMD ["hisab-start"]
