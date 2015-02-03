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

import java.io.File;
import java.io.IOException;
import java.util.Set;

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

  @Parameter(defaultValue = "false", property = "jarsigner.force")
  private boolean forceSign;

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

  @Component(hint = "sha1")
  private Digester sha1Digester;


  public void execute() throws MojoExecutionException
  {
    try
    {
      DefaultJarSigner jarSigner = new DefaultJarSigner();
      jarSigner.enableLogging(new ConsoleLogger());

      Set<Artifact> artifacts = project.getArtifacts();

      for (Artifact artifact : artifacts)
      {
        File file = artifact.getFile();
        if (forceSign || !_existingChecksumMatchs(file))
        {
          getLog().info("Signing " + file.getAbsolutePath() + ".");

          JarSignerUtil.unsignArchive(file);

          JarSignerSignRequest signRequest = new JarSignerSignRequest();
          _setup(signRequest, file);
          signRequest.setKeypass(keypass);
          signRequest.setTsaLocation(tsa);
          _execute(jarSigner, signRequest);
        }

        JarSignerVerifyRequest verifyRequest = new JarSignerVerifyRequest();
        _setup(verifyRequest, file);
        verifyRequest.setVerbose(true);
        verifyRequest.setArguments("-strict", "-verbose:summary");
        _execute(jarSigner, verifyRequest);

        _installSignChecksum(file);
      }

      getLog().info(artifacts.size() + " jars have been processed.");
    }
    catch (Exception e)
    {
      throw new MojoExecutionException(e.getMessage(), e);
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
      throws CommandLineException, JavaToolException
  {
    JavaToolResult result = pJarSigner.execute(pRequest);
    CommandLineException executionException = result.getExecutionException();
    if (executionException != null)
      throw executionException;
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