#!/bin/bash

sbt docker:publishLocal

docker buildx build --platform=linux/arm64,linux/amd64 --push -t jatos/jatos:3.8.1-alpha target/docker/stage

#docker buildx build --platform=linux/arm64,linux/amd64 --push -t jatos/jatos:3.8.1-alpha -d jatos/jatos:latest target/docker/stage
