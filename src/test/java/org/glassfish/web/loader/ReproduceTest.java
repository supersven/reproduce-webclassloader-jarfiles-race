package org.glassfish.web.loader;

import com.sun.enterprise.deployment.Application;
import org.apache.naming.resources.FileDirContext;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ReproduceTest {

    private final Random random = new Random();

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(500, 1_000, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1_000));

    @Test
    public void shouldProvokeRaceConditionIn_findResourceInternalFromJars() throws Exception {
        final ClassLoader classLoader = this.getClass().getClassLoader();
        final WebappClassLoader webappClassLoader = new WebappClassLoader(classLoader, Mockito.mock(Application.class));
        webappClassLoader.start();
        webappClassLoader.setResources(new FileDirContext());

        Runnable runnableLookup = () -> {
            try {
                Thread.sleep(random.nextInt(10));
                lookup(classLoader, webappClassLoader);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        };

        Runnable runnableAdd = () -> {
            try {
                Thread.sleep(random.nextInt(10));
                add(classLoader, webappClassLoader);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        Runnable runnableClose = () -> {
            try {
                Thread.sleep(random.nextInt(10));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            webappClassLoader.closeJARs(true);
//            System.out.println("Closed");
        };

        while (true) {
            try {
                executor.execute(runnableAdd);
                executor.execute(runnableLookup);
                for (int i = 0; i < 25; i++) {
                    executor.execute(runnableClose);
                }
            } catch (RejectedExecutionException e) {
                Thread.sleep(1_000);
            }
        }

//        webappClassLoader.close();

    }

    private void add(ClassLoader realClassLoader, WebappClassLoader webappClassLoader) throws IOException {
        List<ExtendedJarFile> jarFiles = findJarFiles(realClassLoader);

        jarFiles.forEach(j -> {
            try {
                webappClassLoader.addJar(j.name, j.jarFile, j.file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void lookup(ClassLoader realClassLoader, WebappClassLoader webappClassLoader) throws Exception {
        for (ExtendedJarFile extendedJarFile : findJarFiles(realClassLoader)) {
            for (JarEntry entry : Collections.list(extendedJarFile.jarFile.entries())) {
                URL resource = webappClassLoader.findResource(entry.getName());
                // System.out.println("Looked up " + resourceEntry);
                Thread.sleep(0, 100);
            }
        }
    }

    private List<ExtendedJarFile> findJarFiles(ClassLoader realClassLoader) throws IOException {
        List<ExtendedJarFile> jarFiles = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            String jarName = "junit-4.11-" + i + ".jar";
            File file = new File(realClassLoader.getResource(jarName).getFile());
            JarFile jarFile = new JarFile(file);
            jarFiles.add(new ExtendedJarFile(jarName, jarFile, file));
        }
        return jarFiles;
    }

    class ExtendedJarFile {
        final String name;
        final JarFile jarFile;
        final File file;

        ExtendedJarFile(String name, JarFile jarFile, File file) {
            this.name = name;
            this.jarFile = jarFile;
            this.file = file;
        }
    }
}
