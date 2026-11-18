FROM alpine:3.21
RUN apk add --no-cache postgresql16-client rclone
COPY backup.sh /usr/local/bin/backup.sh
# One run per container start; the compose schedule restarts it daily.
CMD ["sh", "-c", "backup.sh && sleep 86400"]
