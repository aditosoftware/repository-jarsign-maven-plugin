package de.adito.maven.plugin.jarsign;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.*;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.javatool.*;
import org.codehaus.plexus.digest.*;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.FileUtils;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.util.*;
import java.util.jar.*;

/**
 * @author PaL
 *         Date: 01.02.15
 *         Time. 21:15
 */
@Mojo(name = "sign", requiresDependencyResolution = ResolutionScope.COMPILE)
public class SignMojo extends AbstractMojo
{

  @Component
  private MavenProject project;

  @Component(hint = "sha1")
  private Digester sha1Digester;

  /**
   * If <i>true</i> all jars are signed no matter whether already signed or not.
   */
  @Parameter(defaultValue = "false", property = "repository.jarsign.force")
  private boolean forceSign;

  /**
   * All entries in this map are added to the jars manifests.
   */
  @Parameter
  private Map<String, String> additionalManifestEntries;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.alias")
  private String alias;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.keystore")
  private String keystore;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.storepass")
  private String storepass;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.keypass")
  private String keypass;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.tsa")
  private String tsa;


  private static final Collection<String> SCOPES = Arrays.asList("compile", "runtime");
  private static final String CHECKSUM_POSTFIX = ".signed.sha1";


  public void execute() throws MojoExecutionException
  {
    try
    {
      DefaultJarSigner jarSigner = new DefaultJarSigner();
      jarSigner.enableLogging(new ConsoleLogger());


      Set<File> workFiles = _getWorkFiles();
      for (File file : workFiles)
      {
        getLog().info("Signing " + file.getAbsolutePath() + ".");

        File workingCopy = new File(file.getAbsolutePath() + "~");
        FileUtils.copyFile(file, workingCopy);

        JarSignerUtil.unsignArchive(workingCopy);

        _updateManifest(workingCopy);

        JarSignerSignRequest signRequest = new JarSignerSignRequest();
        _setup(signRequest, workingCopy);
        signRequest.setKeypass(keypass);
        signRequest.setTsaLocation(tsa);
        _execute(jarSigner, signRequest);

        _installSignChecksum(workingCopy, file.getAbsolutePath() + CHECKSUM_POSTFIX);

        _updateFile(file, workingCopy);

        JarSignerVerifyRequest verifyRequest = new JarSignerVerifyRequest();
        _setup(verifyRequest, file);
        verifyRequest.setVerbose(true);
        verifyRequest.setArguments("-strict", "-verbose:summary");
        _execute(jarSigner, verifyRequest);

        if (Thread.interrupted())
          throw new InterruptedException();
      }
      getLog().info(workFiles.size() + " jars have been signed and verified.");
    }
    catch (CommandLineException | IOException | JavaToolException | InterruptedException e)
    {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void _updateFile(File pOrgFile, File pWorkingCopy) throws MojoExecutionException, IOException, InterruptedException
  {
    IOException ioEx = null;
    for (int i = 0; i < 10; i++)
    {
      try
      {
        FileUtils.forceDelete(pOrgFile);
        ioEx = null;
        break;
      }
      catch (IOException e)
      {
        ioEx = e;
      }
      Thread.sleep(250);
    }
    if (ioEx != null)
      throw ioEx;
    FileUtils.rename(pWorkingCopy, pOrgFile);
  }

  private Set<File> _getWorkFiles() throws MojoExecutionException
  {
    int toSignCount = 0;
    Set<File> workFiles = new HashSet<>();
    for (Artifact artifact : project.getArtifacts())
    {
      if (SCOPES.contains(artifact.getScope()))
      {
        toSignCount++;
        File file = artifact.getFile();
        if (forceSign || !_existingChecksumMatches(file))
          workFiles.add(file);
      }
    }
    getLog().info(toSignCount + " jars are relevant for signing.");
    getLog().info(workFiles.size() + " jars need to be signed.");
    return workFiles;
  }

  private void _updateManifest(File pFile) throws IOException
  {
    try
    {
      String manifestPath = "META-INF/MANIFEST.MF";
      byte[] zipContent = ZipUtil.unpackEntry(pFile, manifestPath);
      try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent))
      {
        Manifest manifest = new Manifest(inputStream);
        Attributes mainAttributes = manifest.getMainAttributes();
        for (Map.Entry<String, String> entry : additionalManifestEntries.entrySet())
          mainAttributes.putValue(entry.getKey(), entry.getValue());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream())
        {
          manifest.write(outputStream);
          ZipUtil.replaceEntry(pFile, manifestPath, outputStream.toByteArray());
          getLog().info("Updated manifest for " + pFile + ".");
        }
      }
    }
    catch (Exception e)
    {
      getLog().error(e);
    }
  }

  private void _setup(AbstractJarSignerRequest pRequest, File pFile)
  {
    pRequest.setKeystore(keystore);
    pRequest.setAlias(alias);
    pRequest.setStorepass(storepass);
    pRequest.setArchive(pFile);
  }

  private void _execute(JarSigner pJarSigner, AbstractJarSignerRequest pRequest)
      throws CommandLineException, JavaToolException, MojoExecutionException
  {
    JavaToolResult result = pJarSigner.execute(pRequest);
    CommandLineException executionException = result.getExecutionException();
    if (executionException != null)
      throw executionException;
    int exitCode = result.getExitCode();
    if (exitCode != 0)
      throw new MojoExecutionException("Wrong exit code '" + exitCode + "'. Jar signing or verifying failed");
  }

  private boolean _existingChecksumMatches(File pFile) throws MojoExecutionException
  {
    String existingChecksum;
    try
    {
      existingChecksum = FileUtils.fileRead(pFile.getAbsoluteFile() + CHECKSUM_POSTFIX);
    }
    catch (IOException e)
    {
      return false;
    }
    return _getChecksum(pFile).equals(existingChecksum);
  }

  private String _getChecksum(File pFile) throws MojoExecutionException
  {
    getLog().debug("Calculating " + sha1Digester.getAlgorithm() + " checksum for " + pFile);
    try
    {
      return sha1Digester.calc(pFile);
    }
    catch (DigesterException e)
    {
      throw new MojoExecutionException("Failed to calculate " + sha1Digester.getAlgorithm() + " checksum for "
                                           + pFile, e);
    }
  }

  private void _installSignChecksum(File pFile, String pChecksumPath)
      throws MojoExecutionException
  {
    String checksum = _getChecksum(pFile);

    File checksumFile = new File(pChecksumPath);
    getLog().debug("Installing checksum to " + checksumFile);
    try
    {
      checksumFile.getParentFile().mkdirs();
      FileUtils.fileWrite(checksumFile.getAbsolutePath(), "UTF-8", checksum);
    }
    catch (IOException e)
    {
      throw new MojoExecutionException("Failed to install checksum to " + checksumFile, e);
    }
  }

}