package de.adito.maven.plugin.jarsign;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.*;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.javatool.JavaToolException;
import org.apache.maven.shared.utils.cli.javatool.JavaToolResult;
import org.codehaus.plexus.digest.Digester;
import org.codehaus.plexus.digest.DigesterException;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.FileUtils;
import org.zeroturnaround.zip.ZipUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

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


  public void execute() throws MojoExecutionException
  {
    try
    {
      DefaultJarSigner jarSigner = new DefaultJarSigner();
      jarSigner.enableLogging(new ConsoleLogger());

      Set<Artifact> artifacts = project.getArtifacts();
      int signCount = 0;

      for (Artifact artifact : artifacts)
      {
        File file = artifact.getFile();
        if (forceSign || !_existingChecksumMatchs(file))
        {
          JarSignerUtil.unsignArchive(file);

          _updateManifest(file);

          getLog().info("Signing " + file.getAbsolutePath() + ".");
          JarSignerSignRequest signRequest = new JarSignerSignRequest();
          _setup(signRequest, file);
          signRequest.setKeypass(keypass);
          signRequest.setTsaLocation(tsa);
          _execute(jarSigner, signRequest);

          _installSignChecksum(file);
          signCount++;
        }

        JarSignerVerifyRequest verifyRequest = new JarSignerVerifyRequest();
        _setup(verifyRequest, file);
        verifyRequest.setVerbose(true);
        verifyRequest.setArguments("-strict", "-verbose:summary");
        _execute(jarSigner, verifyRequest);
      }

      getLog().info(signCount + " jars have been resigned.");
      getLog().info(artifacts.size() + " jars have been verified.");
    }
    catch (CommandLineException | IOException | JavaToolException e)
    {
      throw new MojoExecutionException(e.getMessage(), e);
    }
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

  private boolean _existingChecksumMatchs(File pFile) throws MojoExecutionException
  {
    String existingChecksum;
    try
    {
      existingChecksum = FileUtils.fileRead(pFile.getAbsoluteFile() + ".signed.sha1");
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

  private void _installSignChecksum(File pFile)
      throws MojoExecutionException
  {
    String checksum = _getChecksum(pFile);

    File checksumFile = new File(pFile.getAbsolutePath() + ".signed.sha1");
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