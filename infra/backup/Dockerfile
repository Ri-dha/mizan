FROM alpine:3.21
ARG TARGETARCH
RUN apk add --no-cache postgresql16-client curl \
    && curl -fsSL --retry 5 --retry-all-errors https://dl.min.io/client/mc/release/linux-${TARGETARCH}/mc -o /usr/local/bin/mc \
    && chmod +x /usr/local/bin/mc
COPY backup.sh /usr/local/bin/backup.sh
# One run per container start; the compose schedule restarts it daily.
CMD ["sh", "-c", "backup.sh && sleep 86400"]
