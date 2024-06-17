call ./gradlew fatJar
call docker build -t europe-north1-docker.pkg.dev/root-augury-420915/microservice/gatling .
call docker push europe-north1-docker.pkg.dev/root-augury-420915/microservice/gatling