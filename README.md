using sbt-docker to package a spark job and run it in local mode

use the following line to build and run
    sbt docker
    docker run --name=sparktest eu.stupidsoup.spark/docker-spark

needs sbt and docker installed