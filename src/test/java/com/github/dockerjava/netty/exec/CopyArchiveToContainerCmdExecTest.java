package com.github.dockerjava.netty.exec;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.util.CompressArchiveUtil;
import com.github.dockerjava.netty.AbstractNettyDockerClientTest;

@Test(groups = "integration")
public class CopyArchiveToContainerCmdExecTest extends AbstractNettyDockerClientTest {
    @BeforeTest
    public void beforeTest() throws Exception {
        super.beforeTest();
    }

    @AfterTest
    public void afterTest() {
        super.afterTest();
    }

    @BeforeMethod
    public void beforeMethod(Method method) {
        super.beforeMethod(method);
    }

    @AfterMethod
    public void afterMethod(ITestResult result) {
        super.afterMethod(result);
    }

    @Test
    public void copyFileToContainer() throws Exception {
        CreateContainerResponse container = prepareContainerForCopy();
        Path temp = Files.createTempFile("", ".tar.gz");
        CompressArchiveUtil.tar(Paths.get("src/test/resources/testReadFile"), temp, true, false);
        try (InputStream uploadStream = Files.newInputStream(temp)) {
            dockerClient.copyArchiveToContainerCmd(container.getId()).withTarInputStream(uploadStream).exec();
            assertFileCopied(container);
        }
    }

    @Test
    public void copyStreamToContainer() throws Exception {
        CreateContainerResponse container = prepareContainerForCopy();
        dockerClient.copyArchiveToContainerCmd(container.getId()).withHostResource("src/test/resources/testReadFile")
                .exec();
        assertFileCopied(container);
    }

    private CreateContainerResponse prepareContainerForCopy() {
        CreateContainerResponse container = dockerClient.createContainerCmd("busybox")
                .withName("docker-java-itest-copyToContainer").exec();
        LOG.info("Created container: {}", container);
        assertThat(container.getId(), not(isEmptyOrNullString()));
        dockerClient.startContainerCmd(container.getId()).exec();
        // Copy a folder to the container
        return container;
    }

    private void assertFileCopied(CreateContainerResponse container) throws IOException {
        try (InputStream response = dockerClient.copyArchiveFromContainerCmd(container.getId(), "testReadFile").exec()) {
            boolean bytesAvailable = response.available() > 0;
            assertTrue(bytesAvailable, "The file was not copied to the container.");
        }
    }

    @Test
    public void copyToNonExistingContainer() throws Exception {
        try {
            dockerClient.copyArchiveToContainerCmd("non-existing").withHostResource("src/test/resources/testReadFile")
                    .exec();
            fail("expected NotFoundException");
        } catch (NotFoundException ignored) {
        }
    }

    @Test
    public void copyDirWithLastAddedTarEntryEmptyDir() throws Exception{
        // create a temp dir
        Path localDir = Files.createTempDirectory(null);
        localDir.toFile().deleteOnExit();
        // create empty sub-dir with name b
        Files.createDirectory(localDir.resolve("b"));
        // create sub-dir with name a
        Path dirWithFile = Files.createDirectory(localDir.resolve("a"));
        // create file in sub-dir b, name or conter are irrelevant
        Files.createFile(dirWithFile.resolve("file"));

        // create a test container
        CreateContainerResponse container = dockerClient.createContainerCmd("busybox")
                .withCmd("sleep", "9999")
                .exec();
        // start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        // copy data from local dir to container
        dockerClient.copyArchiveToContainerCmd(container.getId())
                .withHostResource(localDir.toString())
                .exec();

        // cleanup dir
        FileUtils.deleteDirectory(localDir.toFile());
    }

}
