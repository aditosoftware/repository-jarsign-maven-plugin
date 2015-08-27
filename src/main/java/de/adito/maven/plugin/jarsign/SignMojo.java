package de.adito.maven.plugin.jarsign;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.*;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.javatool.*;
import org.codehaus.plexus.digest.Digester;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.FileUtils;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.*;

/**
 * @author PaL
 *         Date: 01.02.15
 *         Time. 21:15
 */
@Mojo(name = "sign", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class SignMojo extends AbstractMojo
{

  @Component
  private MavenProject project;

  @Component(hint = "sha1")
  private Digester digester;

  /**
   * Local repository.
   */
  @Parameter(readonly = true, required = true, defaultValue = "${localRepository}")
  private ArtifactRepository localRepository;

  /**
   * Identifier for the jar-signing-cache. Signed jars are shared between all modules with the same id.
   */
  @Parameter(required = true, property = "repository.jarsign.id")
  private String id;

  /**
   * The directory where the jars to be signed are in.
   */
  @Parameter(required = true, property = "repository.jarsign.directory")
  private String jarDirectory;

  /**
   * A comma separated string with all extensions that shall be signed. Default is <tt>jar</tt> only.
   */
  @Parameter(defaultValue = "jar", property = "repository.jarsign.types")
  private String types;

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
      SignChecksumHelper signChecksumHelper = new SignChecksumHelper(getLog(), digester);

      Set<File> workFiles = _getWorkFiles();
      List<SignCandidate> candidates = new ArrayList<>();

      // update shared directory
      synchronized (id.intern())
      {
        Path cachePath = Paths.get(localRepository.getBasedir()).getParent().resolve("jarsign-cache").resolve(id);
        Files.createDirectories(cachePath);

        for (File file : workFiles)
        {
          SignCandidate candidate = new SignCandidate(file, cachePath, signChecksumHelper, forceSign);
          candidates.add(candidate);
          switch (candidate.getType())
          {
            case NEW:
              getLog().info("Signing " + candidate.getFile().getAbsolutePath() + ".");

              File workingCopy = candidate.getWorkingCopy();
              FileUtils.copyFile(file, workingCopy);

              JarSignerUtil.unsignArchive(workingCopy);

              _updateManifest(workingCopy);

              JarSignerSignRequest signRequest = new JarSignerSignRequest();
              _setup(signRequest, workingCopy);
              signRequest.setKeypass(keypass);
              signRequest.setTsaLocation(tsa);
              _execute(jarSigner, signRequest);

              // install default checksum
              signChecksumHelper.installSignChecksum(file, candidate.getCheckSumPath());
              // install signed checksum
              signChecksumHelper.installSignChecksum(workingCopy, candidate.getSignedCheckSumPath());

              break;
            case CACHED:
            case SIGNED:
              break;
            default:
              throw new MojoExecutionException("unknown type: " + candidate.getType());
          }

          if (Thread.interrupted())
            throw new InterruptedException();
        }
      }

      int signedCount = 0;
      int verifiedCount = 0;

      // update signing directory and verify
      for (SignCandidate candidate : candidates)
      {
        File file = candidate.getFile();
        switch (candidate.getType())
        {
          case NEW:
            signedCount++;

            // fall through
          case CACHED:
            File workingCopy = candidate.getWorkingCopy();
            FileUtils.copyFile(workingCopy, file);

            // fall through
          default:
            JarSignerVerifyRequest verifyRequest = new JarSignerVerifyRequest();
            _setup(verifyRequest, file);
            verifyRequest.setVerbose(true);
            verifyRequest.setArguments("-strict", "-verbose:summary");
            _execute(jarSigner, verifyRequest);

            verifiedCount++;
            break;
        }

        if (Thread.interrupted())
          throw new InterruptedException();
      }

      getLog().info(signedCount + " jars have been signed.");
      getLog().info(verifiedCount + " jars have been verified.");
    }
    catch (Exception e)
    {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private Set<File> _getWorkFiles() throws IOException
  {
    Path path = Paths.get(jarDirectory);
    if (!path.isAbsolute())
      path = project.getBasedir().toPath().resolve(path);

    final PathMatcher matcher = path.getFileSystem().getPathMatcher("glob:**.{" + types + "}");
    final Set<File> workFiles = new HashSet<>();

    if (Files.exists(path))
    {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>()
      {
        @Override
        public FileVisitResult visitFile(Path pFilePath, BasicFileAttributes attrs) throws IOException
        {
          FileVisitResult visitResult = super.visitFile(pFilePath, attrs);
          if (matcher.matches(pFilePath))
            workFiles.add(pFilePath.toFile());
          return visitResult;
        }
      });
    }

    return workFiles;
  }

  private void _updateManifest(File pFile) throws IOException
  {
    String manifestPath = "META-INF/MANIFEST.MF";

    Manifest manifest;
    byte[] zipContent = ZipUtil.unpackEntry(pFile, manifestPath);
    if (zipContent == null)
      manifest = new Manifest();
    else
      try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent))
      {
        manifest = new Manifest(inputStream);
      }

    Attributes mainAttributes = manifest.getMainAttributes();
    if (additionalManifestEntries != null)
      for (Map.Entry<String, String> entry : additionalManifestEntries.entrySet())
        mainAttributes.putValue(entry.getKey(), entry.getValue());

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream())
    {
      manifest.write(outputStream);
      ZipUtil.replaceEntry(pFile, manifestPath, outputStream.toByteArray());
      getLog().info("Updated manifest for " + pFile + ".");
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
      throw new MojoExecutionException("Wrong exit code '" + exitCode + "'. Jar signing or verifying failed for "
                                           + pRequest.getArchive().getAbsolutePath() + ".");
  }

}