#!/bin/bash

sbt docker:publishLocal

docker buildx build --platform=linux/arm64,linux/amd64 --push -t jatos/jatos:3.7.6 target/docker/stage

#docker buildx build --platform=linux/arm64,linux/amd64 --push -t jatos/jatos:3.7.6 -t jatos/jatos:latest target/docker/stage
