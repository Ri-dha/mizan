FROM alpine:3.21
# MinIO no longer publishes mc binaries; Alpine's package names it mcli to avoid Midnight Commander.
RUN apk add --no-cache postgresql16-client minio-client \
    && ln -s /usr/bin/mcli /usr/local/bin/mc
COPY backup.sh /usr/local/bin/backup.sh
# One run per container start; the compose schedule restarts it daily.
CMD ["sh", "-c", "backup.sh && sleep 86400"]
