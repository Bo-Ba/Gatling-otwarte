package com.boba.gatling;

import io.gatling.app.Gatling;
import io.gatling.core.config.GatlingPropertiesBuilder;
import io.gatling.javaapi.core.Simulation;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.boba.gatling.UploadFile.uploadObject;

public class Application {

    public static void main(String[] args) {
    final Set<String> simulations = findAllSimulations().collect(Collectors.toSet());

    String envSimulationsString = System.getenv("SIMULATION");
    String[] envSimulations = envSimulationsString == null ? new String[0] : envSimulationsString.split(",");
//        String[] envSimulations = {"RaisingUsersTests"};

    Set<String> matchedSimulations =
            simulations.stream()
                       .filter(simulation ->
                                       (args.length == 0 && envSimulations.length == 0)
                                       || Arrays.stream(envSimulations).anyMatch(simulation::endsWith)
                                       || Arrays.stream(args).anyMatch(simulation::endsWith)
                       )
                       .collect(Collectors.toSet());

    System.out.printf("Running simulations: %s%n", matchedSimulations);
    matchedSimulations.forEach(Application::runGatlingSimulation);
}

    private static Stream<String> findAllSimulations() {
        final String packageName = Application.class.getPackageName();
        System.out.printf("Finding simulations in %s package%n", packageName);

        final Reflections reflections = new Reflections(packageName, new SubTypesScanner(false));
        return reflections.getSubTypesOf(Simulation.class)
                          .stream()
                          .map(Class::getName);
    }

    private static void runGatlingSimulation(String simulationFileName) {
        System.out.printf("Starting %s simulation%n", simulationFileName);
        final GatlingPropertiesBuilder gatlingPropertiesBuilder = new GatlingPropertiesBuilder();

        gatlingPropertiesBuilder.simulationClass(simulationFileName);
        gatlingPropertiesBuilder.resultsDirectory("test-reports");

        try {
            Gatling.fromMap(gatlingPropertiesBuilder.build());
        } catch (Exception exception) {
            System.err.printf(
                    "Something went wrong for simulation %s %s%n", simulationFileName, exception);
        }

        try {
            uploadMostRecentReport("./test-reports", simulationFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void uploadMostRecentReport(String directoryPath, String testClass) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            Optional<Path> mostRecentDirectoryPath = paths
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(getReportName(testClass)))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));

            mostRecentDirectoryPath.ifPresent(p -> {
                try {
                    String directoryName = p.getFileName().toString();
                    String zipFilePath = directoryPath + File.separator + directoryName + ".zip";

                    zipDirectory(p.toString(), zipFilePath);

                    uploadObject(directoryName+ ".zip", zipFilePath);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static String getReportName(String testClass) {
        return testClass.substring(testClass.lastIndexOf('.') + 1).toLowerCase() + "-";
    }

    public static void zipDirectory(String sourceDirectoryPath, String zipPath) throws IOException {
        Path zipFilePath = Paths.get(zipPath);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
            Path sourceDirPath = Paths.get(sourceDirectoryPath);

            Files.walk(sourceDirPath).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(sourceDirPath.relativize(path).toString());
                try {
                    zipOutputStream.putNextEntry(zipEntry);
                    Files.copy(path, zipOutputStream);
                    zipOutputStream.closeEntry();
                } catch (IOException e) {
                    System.err.println("Failed to add file to zip: " + path);

                }
            });
        }
    }
}
