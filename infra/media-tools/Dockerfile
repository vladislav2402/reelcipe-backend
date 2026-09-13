FROM alpine:3.22.1

RUN apk add --no-cache \
        coreutils \
        ffmpeg \
    && addgroup -S -g 1000 reelcipe \
    && adduser -S -D -H -u 1000 -G reelcipe reelcipe \
    && mkdir -p /work \
    && chown reelcipe:reelcipe /work

USER reelcipe
WORKDIR /work
