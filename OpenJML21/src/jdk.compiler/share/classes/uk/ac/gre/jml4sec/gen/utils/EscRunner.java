package uk.ac.gre.jml4sec.gen.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EscRunner {

    public static final String OPENJML_PATH = System.getProperty("user.home")
            + "/Documents/openjml/openjml";

    public static List<String> runEsc(String filePath, String methodName) {

        List<String> output = new ArrayList<>();
        try {
            ProcessBuilder builder = new ProcessBuilder();

            builder.command("/bin/sh", "-c",
                    new File(OPENJML_PATH).getAbsolutePath() + " -esc "
                            + filePath + " --method " + methodName);

            builder.redirectErrorStream(true);

            Process process = builder.start();

            Queue<String> outputFragments = new ConcurrentLinkedQueue<>();
            StreamGobbler outputGobbler = new StreamGobbler(
                    process.getInputStream(), outputFragments::add);

            outputGobbler.start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.out.println("Failed to check esc.");
            }

            output.addAll(
                    outputFragments.stream().collect(Collectors.toList()));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return output;

    }

    public static class StreamGobbler extends Thread {
        private final InputStream      inputStream;

        private final Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream,
                Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(inputStream))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    consumer.accept(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
