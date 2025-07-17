#!/bin/bash

sbt docker:publishLocal

#docker buildx build --platform=linux/arm64,linux/amd64 --push -t jatos/jatos:3.9.7 target/docker/stage

#docker buildx build --platform=linux/arm64,linux/amd64 --push -t jatos/jatos:3.9.7 -t jatos/jatos:latest target/docker/stage
