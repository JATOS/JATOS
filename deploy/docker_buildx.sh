#!/bin/bash

sbt docker:publishLocal

docker buildx build --platform=linux/arm64,linux/amd64 --push -t jatos/jatos:3.7.4 target/docker/stage
